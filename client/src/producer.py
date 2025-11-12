"""
Producer Module

Purpose: Upload PDF, split into pages, create queue, submit tasks

Class: PDFProducer
    
    Fields:
        - config: Config
        - queue_client: QueueClient
        - pdf_processor: PDFProcessor
    
    Methods:
        - __init__(config_path: str = "config.yaml")
            Load configuration
            Initialize queue_client and pdf_processor
        
        - process_pdf(pdf_path: str, queue_name: str = None) -> str
            Main entry point
            Flow:
                1. Validate PDF exists
                2. Generate unique pdf_id
                3. Create queue via queue service
                4. Split PDF into page images
                5. For each page, submit task to queue with:
                   - params: {job, pdf_id, page_num, page_path, pdf_name}
                   - priority: page number (earlier pages = higher priority)
                6. Save metadata file
                7. Return queue_id
        
        - _save_metadata(pdf_id: str, queue_id: UUID, task_ids: List[UUID], 
                        total_pages: int, pdf_name: str) -> Path
            Save job metadata for aggregator
            File: ./storage/metadata/{pdf_id}_metadata.json
        
        - _validate_pdf_path(pdf_path: str) -> Path
            Check if PDF file exists

CLI Entry Point:
    - main() function
        Parse command line arguments
        Usage: python producer.py <pdf_path> [--queue-name NAME]
        Call process_pdf()
        Print queue_id for workers to use

Metadata Format:
    {
        "pdf_id": "uuid",
        "queue_id": "uuid",
        "pdf_name": "textbook.pdf",
        "total_pages": 10,
        "task_ids": ["uuid1", "uuid2", ...],
        "created_at": "2025-01-01T10:00:00"
    }

Dependencies:
    - queue_client
    - pdf_processor
    - config
    - json
    - sys
    - argparse
"""

