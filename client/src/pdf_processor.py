import uuid
from pathlib import Path
from typing import List
from pdf2image import convert_from_path
from pypdf import PdfReader 
from PIL import Image

class PDFProcessor:
    def __init__(self, pages_output_dir: str):
        self.pages_output_dir = Path(pages_output_dir)
        self.pages_output_dir.mkdir(parents=True, exist_ok=True)

    def validate_pdf(self, pdf_path: str) -> bool:
        try:
            PdfReader(pdf_path)
            return True
        except Exception:
            return False

    def get_pdf_page_count(self, pdf_path: str) -> int:
        reader = PdfReader(pdf_path)
        return len(reader.pages)

    def split_pdf_to_images(self, pdf_path: str, pdf_id: str) -> List[str]:
        output_paths = []
        images = convert_from_path(pdf_path)
        for index in range(1, len(images) + 1):
            page = images[index - 1]
            filename = f"{pdf_id}_page_{index}.png"
            out_path = self.pages_output_dir / filename
            page.save(out_path, format="PNG")
            output_paths.append(str(out_path))
        return output_paths


