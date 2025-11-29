"""
Queue Service API Client

Purpose: Abstract all HTTP communication with the task queue service

Class: QueueClient
    
    Fields:
        - base_url: str (e.g., "http://localhost:8080")
    Methods:
        - __init__(base_url: str)
            Initialize the client with queue service URL
        
        - create_queue(name: str) -> UUID
            POST /queue
            Create a new queue and return its ID
        
        - enqueue_task(queue_id: UUID, params: dict, priority: int) -> UUID
            POST /queue/{id}/task
            Submit a task to the queue, return task ID
        
        - dequeue_task(queue_id: UUID) -> Optional[dict]
            GET /queue/{id}/task
            Dequeue next highest priority task
            Returns None if queue is empty (HTTP 204)
        
        - submit_result(queue_id: UUID, task_id: UUID, output: str, status: str) -> None
            POST /queue/{id}/result
            Submit a result for a completed task
            Status: "SUCCESS" or "FAILURE"
        
        - get_result(queue_id: UUID, task_id: UUID) -> Optional[dict]
            GET /queue/{id}/result/{taskId}
            Retrieve result for a specific task
            Returns None if not found (HTTP 404)
        
        - get_queue_status(queue_id: UUID) -> dict
            GET /queue/{id}/status
            Get queue status (pending tasks, completed results)
            Returns: {pendingTaskCount, completedResultCount, hasPendingTasks}
        
        - _handle_error(response: Response) -> None
            Helper method for error handling

Dependencies:
    - requests
    - json
    - uuid

Testing:
    - Mock requests with responses library
    - Test each endpoint
    - Test error cases (404, 400, timeouts)

    AI assistance used for: Preliminary structure/design, error handling patterns.
"""
import json
from typing import Optional, Dict, Any

import requests


class QueueClientError(Exception):
    """Base exception for QueueClient errors."""


class QueueNotFoundError(QueueClientError):
    """Raised when a queue is not found (HTTP 404)."""


class InvalidRequestError(QueueClientError):
    """Raised when request is invalid (HTTP 400)."""


class QueueClient:
    """Client for Queue Service REST API."""

    def __init__(self, base_url: str):
        """Initialize client with service URL."""
        if not base_url:
            raise ValueError("base_url cannot be empty")
        self.base_url = base_url.rstrip('/')
        self.timeout = 30

    def create_queue(self, name: str) -> str:
        """Create queue. Returns queue ID."""
        url = f"{self.base_url}/queue"
        payload = {"name": name}

        try:
            response = requests.post(url, json=payload, timeout=self.timeout)
            if response.status_code == 400:
                raise InvalidRequestError(f"Invalid: {self._extract_error(response)}")
            response.raise_for_status()
            return response.json()['id']
        except requests.RequestException as e:
            raise QueueClientError(f"Failed to create queue: {e}") from e

    def enqueue_task(self, queue_id: str, params: Dict[str, Any], priority: int) -> str:
        """Submit task to queue. Returns task ID."""
        url = f"{self.base_url}/queue/{queue_id}/task"
        payload = {"params": json.dumps(params), "priority": priority}

        try:
            response = requests.post(url, json=payload, timeout=self.timeout)
            if response.status_code == 404:
                raise QueueNotFoundError(f"Queue not found: {queue_id}")
            if response.status_code == 400:
                raise InvalidRequestError(f"Invalid: {self._extract_error(response)}")
            response.raise_for_status()
            return response.json()['taskId']
        except requests.RequestException as e:
            if isinstance(e, (QueueNotFoundError, InvalidRequestError)):
                raise
            raise QueueClientError(f"Failed to enqueue: {e}") from e

    def dequeue_task(self, queue_id: str) -> Optional[Dict[str, Any]]:
        """Get next task from queue. Returns None if empty."""
        url = f"{self.base_url}/queue/{queue_id}/task"

        try:
            response = requests.get(url, timeout=self.timeout)
            if response.status_code == 204:
                return None
            if response.status_code == 404:
                raise QueueNotFoundError(f"Queue not found: {queue_id}")
            if response.status_code == 400:
                raise InvalidRequestError(f"Invalid: {self._extract_error(response)}")
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            if isinstance(e, (QueueNotFoundError, InvalidRequestError)):
                raise
            raise QueueClientError(f"Failed to dequeue: {e}") from e

    def submit_result(self, queue_id: str, task_id: str, output: str, status: str) -> None:
        """Submit result for completed task. Status: SUCCESS or FAILURE."""
        url = f"{self.base_url}/queue/{queue_id}/result"
        payload = {"taskId": task_id, "output": output, "status": status}

        try:
            response = requests.post(url, json=payload, timeout=self.timeout)
            if response.status_code == 404:
                raise QueueNotFoundError(f"Queue not found: {queue_id}")
            if response.status_code == 400:
                raise InvalidRequestError(f"Invalid: {self._extract_error(response)}")
            response.raise_for_status()
        except requests.RequestException as e:
            if isinstance(e, (QueueNotFoundError, InvalidRequestError)):
                raise
            raise QueueClientError(f"Failed to submit result: {e}") from e

    def get_result(self, queue_id: str, task_id: str) -> Optional[Dict[str, Any]]:
        """Get result for task. Returns None if not found."""
        url = f"{self.base_url}/queue/{queue_id}/result/{task_id}"

        try:
            response = requests.get(url, timeout=self.timeout)
            if response.status_code == 404:
                return None
            if response.status_code == 400:
                raise InvalidRequestError(f"Invalid: {self._extract_error(response)}")
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            if isinstance(e, (QueueNotFoundError, InvalidRequestError)):
                raise
            raise QueueClientError(f"Failed to get result: {e}") from e

    def get_queue_status(self, queue_id: str) -> Dict[str, Any]:
        """Get queue status with task counts."""
        url = f"{self.base_url}/queue/{queue_id}/status"

        try:
            response = requests.get(url, timeout=self.timeout)
            if response.status_code == 404:
                raise QueueNotFoundError(f"Queue not found: {queue_id}")
            if response.status_code == 400:
                raise InvalidRequestError(f"Invalid: {self._extract_error(response)}")
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            if isinstance(e, (QueueNotFoundError, InvalidRequestError)):
                raise
            raise QueueClientError(f"Failed to get status: {e}") from e

    def _extract_error(self, response: requests.Response) -> str:
        """Extract error message from response."""
        try:
            data = response.json()
            return data.get('message', data.get('error', response.text))
        except (json.JSONDecodeError, ValueError):
            return response.text or response.reason