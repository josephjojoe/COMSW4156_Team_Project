"""
PDF Processing Module

Purpose: Split PDF into pages and convert to images

Class: PDFProcessor
    
    Fields:
        - pages_output_dir: Path (where to save page images)
    
    Methods:
        - __init__(pages_dir: str)
            Initialize with output directory path
            Create directory if it doesn't exist
        
        - split_pdf_to_images(pdf_path: str, pdf_id: str) -> List[str]
            Convert PDF pages to PNG images
            Args:
                pdf_path: Path to PDF file
                pdf_id: Unique identifier for this PDF (for naming)
            Returns:
                List of paths to generated page images
            Format: {pdf_id}_page_{num:04d}.png
        
        - get_pdf_page_count(pdf_path: str) -> int
            Get number of pages without extracting images
            Useful for validation
        
        - validate_pdf(pdf_path: str) -> bool
            Check if file is a valid PDF
            Returns True/False

Dependencies:
    - pdf2image (requires poppler-utils installed on system)
    - PyPDF2
    - Pillow
    - pathlib
    - logging (optional)

"""

