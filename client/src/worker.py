"""
Worker Module

Purpose: Poll queue, process pages with LLM, submit results

Class: QuizWorker
    
    Fields:
        - worker_id: str (UUID or process ID for identification)
        - queue_id: UUID
        - config: Config
        - queue_client: QueueClient
        - llm_service: LLMService
        - results_dir: Path
    
    Methods:
        - __init__(queue_id: str, config_path: str = "config.yaml")
            Initialize worker
            Generate unique worker_id
            Load configuration
            Initialize queue_client and llm_service
        
        - run()
            Main worker loop (infinite until KeyboardInterrupt)
            Flow:
                1. Poll dequeue_task() from queue
                2. If no task (None), sleep poll_interval seconds, retry
                3. If task received:
                   a. Log which worker is processing which page
                   b. Extract page_path from task params
                   c. Call LLM to generate quiz questions
                   d. Save questions to JSON file in results_dir
                   e. Submit result with file path and status
                4. Handle errors gracefully
                5. Loop forever
        
        - _process_task(task: dict) -> None
            Handle a single task
            Extract params, call LLM, save result
        
        - _save_result_to_file(pdf_id: str, page_num: int, questions: List[dict]) -> str
            Save quiz questions to JSON file
            Returns file path
            Format: {pdf_id}_page_{page_num}_result.json
        
        - _handle_task_failure(task: dict, error: Exception) -> None
            Submit FAILURE result when task fails
            Log error details

CLI Entry Point:
    - main() function
        Parse arguments
        Usage: python worker.py <queue_id>
        Create worker instance
        Start run() loop

Error Handling:
    - LLM API timeout -> submit FAILURE result
    - Invalid image -> submit FAILURE result  
    - Network errors -> retry with backoff
    - KeyboardInterrupt -> clean shutdown

Worker Identification:
    - Each worker has unique process ID
    - Logs show: "[Worker-12345] Processing page 3"
    - Makes it clear multiple workers are running

Dependencies:
    - queue_client
    - llm_service
    - config
    - time
    - sys
    - os
    - json
    - logging

"""

