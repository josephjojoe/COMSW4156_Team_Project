"""
Aggregator Module

Purpose: Wait for completion, collect results, generate Anki deck

Class: QuizAggregator
    
    Fields:
        - config: Config
        - queue_client: QueueClient
        - output_dir: Path
    
    Methods:
        - __init__(config_path: str = "config.yaml")
            Initialize aggregator
            Load configuration
            Initialize queue_client
        
        - aggregate(queue_id: str, pdf_id: str = None) -> Path
            Main entry point
            Flow:
                1. Load metadata file (from producer)
                2. Wait for queue completion
                3. Collect all results
                4. Load questions from result files
                5. Combine all questions
                6. Generate Anki CSV
                7. Print statistics
                8. Return path to generated deck
        
        - _load_metadata(pdf_id: str) -> dict
            Load metadata file saved by producer
            File: ./storage/metadata/{pdf_id}_metadata.json
        
        - _wait_for_completion(queue_id: UUID, expected_count: int) -> None
            Poll queue status endpoint until done
            Checks: pendingTaskCount == 0 AND completedResultCount == expected_count
            Prints progress updates
            Uses exponential backoff
        
        - _collect_all_results(queue_id: UUID, task_ids: List[UUID]) -> List[dict]
            Fetch all results by task_id
            Returns list of result objects
        
        - _load_questions_from_result(result: dict) -> List[dict]
            Parse result's output field (file path)
            Load JSON file with questions
        
        - _generate_anki_csv(questions: List[dict], output_path: Path) -> None
            Create Anki-importable CSV file
            Format: Question,Answer,Tags
        
        - _generate_statistics(questions: List[dict]) -> dict
            Calculate summary stats
            Total questions, pages processed, success rate

CLI Entry Point:
    - main() function
        Parse arguments
        Usage: python aggregator.py <queue_id>
        Call aggregate()
        Print success message

Anki CSV Format:
    Question,Answer,Tags
    "What is X?","X is...","textbook_page_1"
    "How does Y work?","Y works by...","textbook_page_2"

Dependencies:
    - queue_client
    - config
    - json
    - csv
    - pathlib
    - time
    - logging

"""

