import uuid
import json
import sys
import argparse
from pathlib import Path
from typing import List
from datetime import datetime

# Import dependencies (adjust paths as needed based on project structure)
from src.config import load_config, Config
from src.queue_client import QueueClient
from src.pdf_processor import PDFProcessor


class PDFProducer:
    """
    PDFProducer handles PDF processing and task submission to the queue service.
    """
    
    def __init__(self, config_path: str = "config.yaml"):
        """
        Initialize the PDFProducer with configuration.
        
        Args:
            config_path: Path to the configuration YAML file
        
        Raises:
            FileNotFoundError: If config file doesn't exist
            ValueError: If configuration is invalid
        """
        self.config = load_config(config_path)
        
        # Initialize queue client with service URL from config
        self.queue_client = QueueClient(
            base_url=self.config.queue_service.base_url
        )
        
        # Initialize PDF processor with output directory from config
        self.pdf_processor = PDFProcessor(
            pages_output_dir=self.config.storage.pages_dir
        )
        
        # Ensure metadata directory exists
        self.metadata_dir = Path(self.config.storage.metadata_dir)
        self.metadata_dir.mkdir(parents=True, exist_ok=True)
    
    def process_pdf(self, pdf_path: str, queue_name: str = None) -> str:
        """
        Main entry point for processing a PDF file.
        
        This method:
        1. Validates the PDF exists and is valid
        2. Generates a unique PDF ID
        3. Creates a queue in the task queue service
        4. Splits the PDF into individual page images
        5. Submits one task per page to the queue
        6. Saves metadata for later aggregation
        
        Args:
            pdf_path: Path to the PDF file to process
            queue_name: Optional custom name for the queue (defaults to pdf_id)
        
        Returns:
            str: The queue ID (UUID) where tasks were submitted
        
        Raises:
            FileNotFoundError: If PDF file doesn't exist
            ValueError: If PDF is invalid or corrupted
        """
        # Step 1: Validate PDF exists and is valid
        pdf_file = self._validate_pdf_path(pdf_path)
        
        print(f"Processing PDF: {pdf_file.name}")
        
        # Validate PDF is not corrupted
        if not self.pdf_processor.validate_pdf(str(pdf_file)):
            raise ValueError(f"Invalid or corrupted PDF file: {pdf_path}")
        
        # Get total page count for progress reporting
        total_pages = self.pdf_processor.get_pdf_page_count(str(pdf_file))
        print(f"PDF has {total_pages} pages")
        
        # Step 2: Generate unique PDF ID
        pdf_id = str(uuid.uuid4())
        
        # Step 3: Create queue via queue service API
        queue_name = queue_name or f"pdf-{pdf_id[:8]}"
        print(f"Creating queue: {queue_name}")
        queue_id = self.queue_client.create_queue(queue_name)
        print(f"Queue created with ID: {queue_id}")
        
        # Step 4: Split PDF into page images
        print(f"Converting PDF to images...")
        try:
            image_paths = self.pdf_processor.split_pdf_to_images(
                pdf_path=str(pdf_file),
                pdf_id=pdf_id
            )
        except Exception as e:
            raise RuntimeError(f"Failed to convert PDF to images: {e}")
        
        print(f"Generated {len(image_paths)} page images")
        
        # Step 5: Submit tasks to queue
        print(f"Submitting tasks to queue...")
        task_ids = []
        
        for page_num, page_path in enumerate(image_paths, start=1):
            # Create task parameters
            task_params = {
                "job": "generate_quiz",  # Job type identifier
                "pdf_id": pdf_id,
                "page_num": page_num,
                "page_path": page_path,
                "pdf_name": pdf_file.name
            }
            
            # Submit task with priority (earlier pages = higher priority)
            try:
                task_id = self.queue_client.enqueue_task(
                    queue_id=queue_id,
                    params=task_params,
                    priority=page_num
                )
                task_ids.append(task_id)
                
                # Progress indicator
                if page_num % 10 == 0 or page_num == total_pages:
                    print(f"  Submitted {page_num}/{total_pages} tasks")
                    
            except Exception as e:
                print(f"Warning: Failed to submit task for page {page_num}: {e}", 
                      file=sys.stderr)
        
        print(f"✓ Submitted {len(task_ids)} tasks successfully")
        
        # Step 6: Save metadata for aggregator
        metadata_path = self._save_metadata(
            pdf_id=pdf_id,
            queue_id=queue_id,
            task_ids=task_ids,
            total_pages=total_pages,
            pdf_name=pdf_file.name
        )
        print(f"Metadata saved to: {metadata_path}")
        
        # Step 7: Return queue_id
        return queue_id
    
    def _save_metadata(
        self, 
        pdf_id: str, 
        queue_id: str, 
        task_ids: List[str], 
        total_pages: int,
        pdf_name: str
    ) -> Path:
        """
        Save job metadata for aggregator to use later.
        
        Args:
            pdf_id: Unique PDF identifier
            queue_id: Queue ID where tasks were submitted
            task_ids: List of all task IDs submitted
            total_pages: Total number of pages in the PDF
            pdf_name: Original PDF filename
        
        Returns:
            Path: Path to the saved metadata file
        """
        metadata = {
            "pdf_id": pdf_id,
            "queue_id": queue_id,
            "pdf_name": pdf_name,
            "total_pages": total_pages,
            "task_ids": task_ids,
            "created_at": datetime.now().isoformat()
        }
        
        # Save to metadata directory
        metadata_path = self.metadata_dir / f"{pdf_id}_metadata.json"
        
        with open(metadata_path, 'w') as f:
            json.dump(metadata, f, indent=2)
        
        return metadata_path
    
    def _validate_pdf_path(self, pdf_path: str) -> Path:
        """
        Validate that PDF file exists.
        
        Args:
            pdf_path: Path to PDF file
        
        Returns:
            Path: Validated Path object
        
        Raises:
            FileNotFoundError: If PDF file doesn't exist
            ValueError: If path is not a PDF file
        """
        pdf_file = Path(pdf_path)
        
        if not pdf_file.exists():
            raise FileNotFoundError(f"PDF file not found: {pdf_path}")
        
        if not pdf_file.is_file():
            raise ValueError(f"Path is not a file: {pdf_path}")
        
        if pdf_file.suffix.lower() != '.pdf':
            raise ValueError(f"File is not a PDF: {pdf_path}")
        
        return pdf_file


def main():
    """
    CLI entry point for PDF processing.
    
    This establishes the standard CLI pattern for the project:
    - Uses argparse for argument parsing
    - Includes --config flag for configuration
    - Provides helpful error messages
    - Shows next steps after completion
    
    Usage:
        python producer.py <pdf_path> [--queue-name NAME] [--config PATH]
    
    Examples:
        python producer.py textbook.pdf
        python producer.py textbook.pdf --queue-name "biology-ch1"
        python producer.py textbook.pdf --config custom_config.yaml
    """
    # Set up argument parser
    parser = argparse.ArgumentParser(
        description='Process PDF and submit tasks to queue service',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python producer.py textbook.pdf
  python producer.py textbook.pdf --queue-name "biology-ch1"
  python producer.py textbook.pdf --config custom_config.yaml

The queue ID will be displayed after processing. Use this ID to start workers:
  python worker.py <queue_id>
        """
    )
    
    # Required arguments
    parser.add_argument(
        'pdf_path',
        type=str,
        help='Path to PDF file to process'
    )
    
    # Optional arguments
    parser.add_argument(
        '--queue-name',
        type=str,
        default=None,
        help='Optional custom name for the queue (defaults to generated name)'
    )
    
    parser.add_argument(
        '--config',
        type=str,
        default='config.yaml',
        help='Path to configuration file (default: config.yaml)'
    )
    
    # Parse arguments
    args = parser.parse_args()
    
    # Execute with error handling
    try:
        # Initialize producer
        producer = PDFProducer(config_path=args.config)
        
        # Process PDF
        print("=" * 60)
        queue_id = producer.process_pdf(
            pdf_path=args.pdf_path,
            queue_name=args.queue_name
        )
        print("=" * 60)
        
        # Display success message and next steps
        print(f"\n✓ PDF processed successfully!")
        print(f"\nQueue ID: {queue_id}")
        print(f"\nNext steps:")
        print(f"  1. Start one or more workers:")
        print(f"     python worker.py {queue_id}")
        print(f"\n  2. After workers complete, aggregate results:")
        print(f"     python aggregator.py {queue_id}")
        print()
        
        sys.exit(0)
        
    except FileNotFoundError as e:
        print(f"Error: {e}", file=sys.stderr)
        print(f"Please check that the file path is correct.", file=sys.stderr)
        sys.exit(1)
        
    except ValueError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
        
    except Exception as e:
        print(f"Error: An unexpected error occurred: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()