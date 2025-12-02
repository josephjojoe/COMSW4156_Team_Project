from pathlib import Path
from pypdf import PdfWriter
from src.pdf_processor import PDFProcessor

def test_validate_pdf_true(tmp_path):
    pdf_path = Path("tests/resources/valid_test.pdf")
    processor = PDFProcessor(pages_output_dir=tmp_path)
    assert processor.validate_pdf(str(pdf_path)) is True

def test_get_pdf_page_count(tmp_path):
    pdf_file = Path("tests/resources/valid_test.pdf")
    processor = PDFProcessor(pages_output_dir=tmp_path)
    assert processor.get_pdf_page_count(str(pdf_file)) == 24

def test_split_pdf_to_images(tmp_path):
    pdf_file = Path("tests/resources/valid_test.pdf")
    processor = PDFProcessor(pages_output_dir=tmp_path)
    output = processor.split_pdf_to_images(str(pdf_file), "test")

    assert len(output) > 0
    for path in output:
        assert Path(path).exists()
