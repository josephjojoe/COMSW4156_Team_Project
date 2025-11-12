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

"""

