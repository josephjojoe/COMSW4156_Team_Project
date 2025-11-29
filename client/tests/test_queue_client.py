"""
Unit Tests for QueueClient

AI assistance used for: Coverage checking, preliminary structure, looking for logic holes.
"""

import unittest
from unittest.mock import Mock, patch
import json
import requests
from src.queue_client import QueueClient, QueueClientError, QueueNotFoundError, InvalidRequestError

class TestQueueClient(unittest.TestCase):
    def setUp(self):
        self.base_url = "http://localhost:8080"
        self.client = QueueClient(self.base_url)
        self.queue_id = "a3b5c7d9-1234-5678-90ab-cdef12345678"
        self.task_id = "b4c6d8e0-2345-6789-01bc-def123456789"

    def test_init_valid(self):
        """Test initialization with valid URL."""
        client = QueueClient("http://localhost:8080")
        self.assertEqual(client.base_url, "http://localhost:8080")

    def test_init_strips_slash(self):
        """Test trailing slash removal."""
        client = QueueClient("http://localhost:8080/")
        self.assertEqual(client.base_url, "http://localhost:8080")

    def test_init_empty_raises(self):
        """Test empty URL raises error."""
        with self.assertRaises(ValueError):
            QueueClient("")

    @patch('requests.post')
    def test_create_queue_success(self, mock_post):
        """Test successful queue creation."""
        mock_post.return_value = Mock(status_code=200, json=lambda: {"id": self.queue_id})
        queue_id = self.client.create_queue("test")
        self.assertEqual(queue_id, self.queue_id)

    @patch('requests.post')
    def test_create_queue_invalid(self, mock_post):
        """Test invalid queue name."""
        mock_post.return_value = Mock(status_code=400, json=lambda: {"message": "Invalid"})
        with self.assertRaises(InvalidRequestError):
            self.client.create_queue("")

    @patch('requests.post')
    def test_enqueue_success(self, mock_post):
        """Test successful task enqueue."""
        mock_post.return_value = Mock(status_code=200, json=lambda: {"taskId": self.task_id})
        task_id = self.client.enqueue_task(self.queue_id, {"test": "data"}, 1)
        self.assertEqual(task_id, self.task_id)

    @patch('requests.post')
    def test_enqueue_not_found(self, mock_post):
        """Test enqueue on missing queue."""
        mock_post.return_value = Mock(status_code=404)
        with self.assertRaises(QueueNotFoundError):
            self.client.enqueue_task("invalid", {}, 1)

    @patch('requests.post')
    def test_enqueue_invalid(self, mock_post):
        """Test enqueue with invalid params."""
        mock_post.return_value = Mock(status_code=400, json=lambda: {"message": "Invalid"})
        with self.assertRaises(InvalidRequestError):
            self.client.enqueue_task("bad-uuid", {}, 1)

    @patch('requests.get')
    def test_dequeue_success(self, mock_get):
        """Test successful dequeue."""
        task = {"id": self.task_id, "params": "{}", "priority": 1}
        mock_get.return_value = Mock(status_code=200, json=lambda: task)
        result = self.client.dequeue_task(self.queue_id)
        self.assertEqual(result['id'], self.task_id)

    @patch('requests.get')
    def test_dequeue_empty(self, mock_get):
        """Test dequeue from empty queue."""
        mock_get.return_value = Mock(status_code=204)
        result = self.client.dequeue_task(self.queue_id)
        self.assertIsNone(result)

    @patch('requests.get')
    def test_dequeue_not_found(self, mock_get):
        """Test dequeue from missing queue."""
        mock_get.return_value = Mock(status_code=404)
        with self.assertRaises(QueueNotFoundError):
            self.client.dequeue_task("invalid")

    @patch('requests.post')
    def test_submit_result_success(self, mock_post):
        """Test successful result submission."""
        mock_post.return_value = Mock(status_code=200)
        self.client.submit_result(self.queue_id, self.task_id, "output", "SUCCESS")
        mock_post.assert_called_once()

    @patch('requests.post')
    def test_submit_result_invalid(self, mock_post):
        """Test submit with invalid status."""
        mock_post.return_value = Mock(status_code=400, json=lambda: {"message": "Invalid"})
        with self.assertRaises(InvalidRequestError):
            self.client.submit_result(self.queue_id, self.task_id, "", "BAD")

    @patch('requests.get')
    def test_get_result_success(self, mock_get):
        """Test successful result retrieval."""
        result = {"taskId": self.task_id, "output": "test", "status": "SUCCESS"}
        mock_get.return_value = Mock(status_code=200, json=lambda: result)
        resp = self.client.get_result(self.queue_id, self.task_id)
        self.assertEqual(resp['taskId'], self.task_id)

    @patch('requests.get')
    def test_get_result_not_found(self, mock_get):
        """Test get missing result."""
        mock_get.return_value = Mock(status_code=404)
        result = self.client.get_result(self.queue_id, self.task_id)
        self.assertIsNone(result)

    @patch('requests.get')
    def test_get_status_success(self, mock_get):
        """Test queue status retrieval."""
        status = {"id": self.queue_id, "pendingTaskCount": 5, "completedResultCount": 3}
        mock_get.return_value = Mock(status_code=200, json=lambda: status)
        resp = self.client.get_queue_status(self.queue_id)
        self.assertEqual(resp['pendingTaskCount'], 5)

    @patch('requests.get')
    def test_get_status_not_found(self, mock_get):
        """Test status for missing queue."""
        mock_get.return_value = Mock(status_code=404)
        with self.assertRaises(QueueNotFoundError):
            self.client.get_queue_status("invalid")

    @patch('requests.get')
    def test_get_status_complete(self, mock_get):
        """Test status when all complete."""
        status = {"pendingTaskCount": 0, "completedResultCount": 10, "hasPendingTasks": False}
        mock_get.return_value = Mock(status_code=200, json=lambda: status)
        resp = self.client.get_queue_status(self.queue_id)
        self.assertFalse(resp['hasPendingTasks'])

    def test_extract_error_json(self):
        """Test error extraction from JSON."""
        mock_resp = Mock(json=lambda: {"message": "Error"}, text="fallback")
        msg = self.client._extract_error(mock_resp)
        self.assertEqual(msg, "Error")

    def test_extract_error_text(self):
        """Test error extraction from text."""
        mock_resp = Mock(text="Error text", reason="Reason")
        mock_resp.json.side_effect = json.JSONDecodeError("", "", 0)
        msg = self.client._extract_error(mock_resp)
        self.assertEqual(msg, "Error text")

    @patch('requests.post')
    def test_timeout(self, mock_post):
        """Test timeout handling."""
        mock_post.side_effect = requests.Timeout("Timeout")
        with self.assertRaises(QueueClientError):
            self.client.create_queue("test")

    @patch('requests.get')
    def test_connection_error(self, mock_get):
        """Test connection error handling."""
        mock_get.side_effect = requests.ConnectionError("Failed")
        with self.assertRaises(QueueClientError):
            self.client.dequeue_task(self.queue_id)

if __name__ == '__main__':
    unittest.main()

