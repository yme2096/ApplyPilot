import io
import unittest

import fitz
from docx import Document
from fastapi import HTTPException

from app import MAX_BYTES, parse_bytes


def synthetic_pdf(text: str = "Fictional Candidate\nPython engineer") -> bytes:
    document = fitz.open()
    page = document.new_page()
    page.insert_text((72, 72), text)
    data = document.tobytes()
    document.close()
    return data


class ResumeParserTests(unittest.TestCase):
    def test_parses_pdf_text_and_marks_extraction_for_review(self):
        parsed = parse_bytes(synthetic_pdf(), "synthetic.pdf")
        self.assertIn("Fictional Candidate", parsed.extractedText)
        self.assertIn("Python", parsed.structuredData["skills"])
        self.assertTrue(parsed.structuredData["needsReview"])

    def test_rejects_malformed_pdf_with_controlled_422(self):
        with self.assertRaises(HTTPException) as raised:
            parse_bytes(b"%PDF-1.7\nnot a complete PDF", "bad.pdf")
        self.assertEqual(raised.exception.status_code, 422)

    def test_rejects_wrong_extension_and_oversized_payload(self):
        with self.assertRaises(HTTPException) as unsupported:
            parse_bytes(b"content", "resume.txt")
        self.assertEqual(unsupported.exception.status_code, 415)
        with self.assertRaises(HTTPException) as too_large:
            parse_bytes(b"%PDF-" + b"x" * MAX_BYTES, "large.pdf")
        self.assertEqual(too_large.exception.status_code, 413)

    def test_parses_docx_paragraphs_and_tables(self):
        document = Document()
        document.add_paragraph("Fictional Candidate")
        table = document.add_table(rows=1, cols=1)
        table.cell(0, 0).text = "Java experience"
        output = io.BytesIO()
        document.save(output)
        parsed = parse_bytes(output.getvalue(), "synthetic.docx")
        self.assertIn("Fictional Candidate", parsed.extractedText)
        self.assertIn("Java experience", parsed.extractedText)
