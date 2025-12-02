"""
Unit Tests for Producer Module

Tests the PDFProducer class using a hybrid approach:
- Real Config objects (just data, no side effects)
- Mock QueueClient and PDFProcessor with spec (avoids HTTP calls and PDF conversion)

Using spec= ensures mocks match the real class interface.
This catches errors when the real implementation changes.

Test Coverage:
- PDF path validation
- Metadata saving
- Main process_pdf workflow with mocks
- Error handling
"""

import json
import pytest
from pathlib import Path
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime

from src.producer import PDFProducer
from src.config import Config, QueueServiceConfig, StorageConfig, LLMConfig, WorkerConfig, AnkiConfig
from src.queue_client import QueueClient
from src.pdf_processor import PDFProcessor


# ===== Test Fixtures =====

@pytest.fixture
def mock_config():
    """
    Real Config object with test data - better than pure mocks!
    
    Why use real Config instead of Mock?
    1. Config is just data - no external dependencies
    2. If Config structure changes, these tests will catch it
    3. Ensures our test data matches real Config validation
    """
    return Config(
        queue_service=QueueServiceConfig(
            base_url="http://localhost:8080"
        ),
        storage=StorageConfig(
            pdf_dir="storage/pdfs",
            pages_dir="storage/pages",
            results_dir="storage/results",
            metadata_dir="storage/metadata"
        ),
        llm=LLMConfig(
            provider="mock",
            api_key=None,
            model="mock-model",
            max_questions_per_page=5
        ),
        worker=WorkerConfig(
            poll_interval=2.0,
            max_retries=3,
            retry_backoff=2.0
        ),
        anki=AnkiConfig(
            deck_name="Test Deck",
            output_dir="output"
        )
    )


@pytest.fixture
def mock_queue_client():
    """
    Mock QueueClient with spec - prevents mocking non-existent methods
    
    Why use spec=QueueClient?
    1. Prevents typos: mock_client.creete_queue() would raise AttributeError
    2. Catches API changes: if QueueClient.create_queue is renamed, tests fail
    3. IDE autocomplete works correctly
    """
    client = Mock(spec=QueueClient)
    # create_queue returns just the ID string (not dict)
    client.create_queue.return_value = "test-queue-id-1234"
    # enqueue_task returns just the task ID string (not dict)
    client.enqueue_task.return_value = "test-task-id-5678"
    return client


@pytest.fixture
def mock_pdf_processor():
    """Mock PDFProcessor with spec - prevents mocking non-existent methods"""
    processor = Mock(spec=PDFProcessor)
    processor.validate_pdf.return_value = True
    processor.get_pdf_page_count.return_value = 3
    processor.split_pdf_to_images.return_value = [
        "storage/pages/test-pdf_page_1.png",
        "storage/pages/test-pdf_page_2.png",
        "storage/pages/test-pdf_page_3.png"
    ]
    return processor


@pytest.fixture
def producer(tmp_path, mock_config):
    """
    Create a PDFProducer instance with mocked dependencies
    
    We use tmp_path for the metadata directory to avoid
    writing to the actual filesystem during tests.
    """
    # Update config to use temporary directory
    mock_config.storage.metadata_dir = str(tmp_path / "metadata")
    
    with patch('src.producer.load_config', return_value=mock_config), \
         patch('src.producer.QueueClient'), \
         patch('src.producer.PDFProcessor'):
        
        producer = PDFProducer()
        producer.metadata_dir = Path(tmp_path / "metadata")
        producer.metadata_dir.mkdir(parents=True, exist_ok=True)
        
        return producer


# ===== Tests for _validate_pdf_path =====

def test_validate_pdf_path_success(producer, tmp_path):
    """Test validating a valid PDF path"""
    # Create a dummy PDF file
    pdf_file = tmp_path / "test.pdf"
    pdf_file.write_text("dummy pdf content")
    
    # Should return Path object without raising
    result = producer._validate_pdf_path(str(pdf_file))
    assert isinstance(result, Path)
    assert result.name == "test.pdf"


def test_validate_pdf_path_file_not_found(producer):
    """Test validation fails when file doesn't exist"""
    with pytest.raises(FileNotFoundError) as exc_info:
        producer._validate_pdf_path("nonexistent.pdf")
    
    assert "not found" in str(exc_info.value).lower()


def test_validate_pdf_path_not_a_file(producer, tmp_path):
    """Test validation fails when path is a directory"""
    directory = tmp_path / "not_a_file"
    directory.mkdir()
    
    with pytest.raises(ValueError) as exc_info:
        producer._validate_pdf_path(str(directory))
    
    assert "not a file" in str(exc_info.value).lower()


def test_validate_pdf_path_not_pdf_extension(producer, tmp_path):
    """Test validation fails when file is not a PDF"""
    txt_file = tmp_path / "not_a_pdf.txt"
    txt_file.write_text("some text")
    
    with pytest.raises(ValueError) as exc_info:
        producer._validate_pdf_path(str(txt_file))
    
    assert "not a pdf" in str(exc_info.value).lower()


# ===== Tests for _save_metadata =====

def test_save_metadata_creates_file(producer, tmp_path):
    """Test metadata file is created with correct structure"""
    pdf_id = "test-pdf-id"
    queue_id = "test-queue-id"
    task_ids = ["task-1", "task-2", "task-3"]
    total_pages = 3
    pdf_name = "test.pdf"
    
    # Save metadata
    result_path = producer._save_metadata(
        pdf_id=pdf_id,
        queue_id=queue_id,
        task_ids=task_ids,
        total_pages=total_pages,
        pdf_name=pdf_name
    )
    
    # Check file was created
    assert result_path.exists()
    assert result_path.name == f"{pdf_id}_metadata.json"
    
    # Check content structure
    with open(result_path, 'r') as f:
        metadata = json.load(f)
    
    assert metadata["pdf_id"] == pdf_id
    assert metadata["queue_id"] == queue_id
    assert metadata["pdf_name"] == pdf_name
    assert metadata["total_pages"] == total_pages
    assert metadata["task_ids"] == task_ids
    assert "created_at" in metadata
    
    # Verify created_at is a valid ISO timestamp
    datetime.fromisoformat(metadata["created_at"])


def test_save_metadata_overwrites_existing(producer, tmp_path):
    """Test that saving metadata overwrites existing file"""
    pdf_id = "duplicate-id"
    
    # Save first time
    producer._save_metadata(
        pdf_id=pdf_id,
        queue_id="queue-1",
        task_ids=["task-1"],
        total_pages=1,
        pdf_name="first.pdf"
    )
    
    # Save again with different data
    result_path = producer._save_metadata(
        pdf_id=pdf_id,
        queue_id="queue-2",
        task_ids=["task-2", "task-3"],
        total_pages=2,
        pdf_name="second.pdf"
    )
    
    # Check that second save overwrote the first
    with open(result_path, 'r') as f:
        metadata = json.load(f)
    
    assert metadata["queue_id"] == "queue-2"
    assert metadata["pdf_name"] == "second.pdf"
    assert len(metadata["task_ids"]) == 2


# ===== Tests for process_pdf (with mocks) =====

def test_process_pdf_workflow(tmp_path, mock_config, mock_queue_client, mock_pdf_processor):
    """Test the complete process_pdf workflow with mocked dependencies"""
    # Create a dummy PDF file
    pdf_file = tmp_path / "test.pdf"
    pdf_file.write_text("dummy pdf")
    
    # Set up metadata directory
    mock_config.storage.metadata_dir = str(tmp_path / "metadata")
    
    # Create producer with mocked dependencies
    with patch('src.producer.load_config', return_value=mock_config), \
         patch('src.producer.QueueClient', return_value=mock_queue_client), \
         patch('src.producer.PDFProcessor', return_value=mock_pdf_processor):
        
        producer = PDFProducer()
        
        # Execute process_pdf
        queue_id = producer.process_pdf(
            pdf_path=str(pdf_file),
            queue_name="test-queue"
        )
        
        # Verify queue was created
        mock_queue_client.create_queue.assert_called_once_with("test-queue")
        
        # Verify PDF was validated
        mock_pdf_processor.validate_pdf.assert_called_once()
        
        # Verify page count was retrieved
        mock_pdf_processor.get_pdf_page_count.assert_called_once()
        
        # Verify PDF was split into images
        mock_pdf_processor.split_pdf_to_images.assert_called_once()
        
        # Verify tasks were enqueued (3 pages = 3 tasks)
        assert mock_queue_client.enqueue_task.call_count == 3
        
        # Verify queue_id was returned
        assert queue_id == "test-queue-id-1234"
        
        # Verify metadata file was created
        metadata_files = list((tmp_path / "metadata").glob("*_metadata.json"))
        assert len(metadata_files) == 1


def test_process_pdf_uses_generated_queue_name_when_none_provided(
    tmp_path, mock_config, mock_queue_client, mock_pdf_processor
):
    """Test that a queue name is generated when none is provided"""
    pdf_file = tmp_path / "test.pdf"
    pdf_file.write_text("dummy pdf")
    
    mock_config.storage.metadata_dir = str(tmp_path / "metadata")
    
    with patch('src.producer.load_config', return_value=mock_config), \
         patch('src.producer.QueueClient', return_value=mock_queue_client), \
         patch('src.producer.PDFProcessor', return_value=mock_pdf_processor):
        
        producer = PDFProducer()
        queue_id = producer.process_pdf(pdf_path=str(pdf_file))
        
        # Verify create_queue was called with a generated name (starts with "pdf-")
        call_args = mock_queue_client.create_queue.call_args[0]
        assert call_args[0].startswith("pdf-")


def test_process_pdf_task_params_structure(
    tmp_path, mock_config, mock_queue_client, mock_pdf_processor
):
    """Test that task parameters have the correct structure"""
    pdf_file = tmp_path / "test.pdf"
    pdf_file.write_text("dummy pdf")
    
    mock_config.storage.metadata_dir = str(tmp_path / "metadata")
    
    with patch('src.producer.load_config', return_value=mock_config), \
         patch('src.producer.QueueClient', return_value=mock_queue_client), \
         patch('src.producer.PDFProcessor', return_value=mock_pdf_processor):
        
        producer = PDFProducer()
        producer.process_pdf(pdf_path=str(pdf_file))
        
        # Get the first task that was enqueued
        first_call = mock_queue_client.enqueue_task.call_args_list[0]
        
        # Extract the params argument
        params = first_call.kwargs['params']
        
        # Verify params structure
        assert "job" in params
        assert params["job"] == "generate_quiz"
        assert "pdf_id" in params
        assert "page_num" in params
        assert "page_path" in params
        assert "pdf_name" in params
        assert params["pdf_name"] == "test.pdf"


def test_process_pdf_priority_ordering(
    tmp_path, mock_config, mock_queue_client, mock_pdf_processor
):
    """Test that tasks are submitted with priority based on page number"""
    pdf_file = tmp_path / "test.pdf"
    pdf_file.write_text("dummy pdf")
    
    mock_config.storage.metadata_dir = str(tmp_path / "metadata")
    
    with patch('src.producer.load_config', return_value=mock_config), \
         patch('src.producer.QueueClient', return_value=mock_queue_client), \
         patch('src.producer.PDFProcessor', return_value=mock_pdf_processor):
        
        producer = PDFProducer()
        producer.process_pdf(pdf_path=str(pdf_file))
        
        # Check priorities of all enqueued tasks
        for i, call in enumerate(mock_queue_client.enqueue_task.call_args_list):
            priority = call.kwargs['priority']
            # Priority should match page number (1-indexed)
            assert priority == i + 1


# ===== Tests for Error Handling =====

def test_process_pdf_invalid_pdf_raises_error(
    tmp_path, mock_config, mock_queue_client, mock_pdf_processor
):
    """Test that process_pdf raises ValueError for invalid PDFs"""
    pdf_file = tmp_path / "invalid.pdf"
    pdf_file.write_text("not a real pdf")
    
    # Mock validate_pdf to return False (invalid PDF)
    mock_pdf_processor.validate_pdf.return_value = False
    
    mock_config.storage.metadata_dir = str(tmp_path / "metadata")
    
    with patch('src.producer.load_config', return_value=mock_config), \
         patch('src.producer.QueueClient', return_value=mock_queue_client), \
         patch('src.producer.PDFProcessor', return_value=mock_pdf_processor):
        
        producer = PDFProducer()
        
        with pytest.raises(ValueError) as exc_info:
            producer.process_pdf(pdf_path=str(pdf_file))
        
        assert "invalid" in str(exc_info.value).lower() or \
               "corrupted" in str(exc_info.value).lower()


def test_process_pdf_conversion_failure_raises_error(
    tmp_path, mock_config, mock_queue_client, mock_pdf_processor
):
    """Test that process_pdf handles PDF conversion failures"""
    pdf_file = tmp_path / "test.pdf"
    pdf_file.write_text("dummy pdf")
    
    # Mock split_pdf_to_images to raise an exception
    mock_pdf_processor.split_pdf_to_images.side_effect = Exception("Conversion failed")
    
    mock_config.storage.metadata_dir = str(tmp_path / "metadata")
    
    with patch('src.producer.load_config', return_value=mock_config), \
         patch('src.producer.QueueClient', return_value=mock_queue_client), \
         patch('src.producer.PDFProcessor', return_value=mock_pdf_processor):
        
        producer = PDFProducer()
        
        with pytest.raises(RuntimeError) as exc_info:
            producer.process_pdf(pdf_path=str(pdf_file))
        
        assert "failed to convert" in str(exc_info.value).lower()


# ===== Integration-Style Test (less mocking) =====

def test_process_pdf_metadata_matches_tasks(
    tmp_path, mock_config, mock_queue_client, mock_pdf_processor
):
    """
    Test that metadata file contains correct task IDs.
    This is a more integration-style test.
    """
    pdf_file = tmp_path / "test.pdf"
    pdf_file.write_text("dummy pdf")
    
    mock_config.storage.metadata_dir = str(tmp_path / "metadata")
    
    # Mock enqueue_task to return different task IDs
    task_ids = ["task-001", "task-002", "task-003"]
    mock_queue_client.enqueue_task.side_effect = task_ids
    
    with patch('src.producer.load_config', return_value=mock_config), \
         patch('src.producer.QueueClient', return_value=mock_queue_client), \
         patch('src.producer.PDFProcessor', return_value=mock_pdf_processor):
        
        producer = PDFProducer()
        producer.process_pdf(pdf_path=str(pdf_file))
        
        # Load metadata file
        metadata_files = list((tmp_path / "metadata").glob("*_metadata.json"))
        assert len(metadata_files) == 1
        
        with open(metadata_files[0], 'r') as f:
            metadata = json.load(f)
        
        # Verify task IDs match what was returned by enqueue_task
        assert metadata["task_ids"] == task_ids
        assert len(metadata["task_ids"]) == 3


# ===== Testing CLI Main Function =====

def test_main_function_success(tmp_path, monkeypatch):
    """Test the CLI entry point works correctly"""
    # Create a test PDF
    pdf_file = tmp_path / "test.pdf"
    pdf_file.write_text("dummy pdf")
    
    # Mock sys.argv to simulate command line
    import sys
    monkeypatch.setattr(sys, 'argv', [
        'producer.py',
        str(pdf_file),
        '--queue-name', 'test-queue'
    ])
    
    with patch('src.producer.PDFProducer') as mock_producer_class:
        mock_instance = Mock()
        mock_instance.process_pdf.return_value = "queue-123"
        mock_producer_class.return_value = mock_instance
        
        # Should exit with 0 (success)
        with pytest.raises(SystemExit) as exc_info:
            from src.producer import main
            main()
        
        assert exc_info.value.code == 0
        mock_instance.process_pdf.assert_called_once()


def test_main_function_handles_file_not_found(monkeypatch, capsys):
    """Test CLI handles missing files gracefully"""
    import sys
    monkeypatch.setattr(sys, 'argv', [
        'producer.py',
        'nonexistent.pdf'
    ])
    
    with pytest.raises(SystemExit) as exc_info:
        from src.producer import main
        main()
    
    assert exc_info.value.code == 1
    captured = capsys.readouterr()
    assert "not found" in captured.err.lower()