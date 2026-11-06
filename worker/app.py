from __future__ import annotations

import io
import re
from fastapi import FastAPI, File, HTTPException, UploadFile, Body, Query
from fastapi.responses import Response
from pydantic import BaseModel
import fitz
from docx import Document

app = FastAPI(title="ApplyPilot document worker", version="0.1.0")
MAX_BYTES = 8 * 1024 * 1024


class Evidence(BaseModel):
    field: str
    value: str
    excerpt: str


class ParsedResume(BaseModel):
    extractedText: str
    structuredData: dict[str, object]
    evidence: list[Evidence]


class ExportRequest(BaseModel):
    extractedText: str


def extract_text(data: bytes, filename: str) -> str:
    if filename.lower().endswith(".pdf"):
        try:
            with fitz.open(stream=data, filetype="pdf") as document:
                return "\n".join(page.get_text("text") for page in document)
        except Exception as exc:
            raise HTTPException(status_code=422, detail="The PDF document could not be read") from exc
    try:
        document = Document(io.BytesIO(data))
        parts = [p.text for p in document.paragraphs if p.text.strip()]
        for table in document.tables:
            parts.extend(" | ".join(cell.text.strip() for cell in row.cells) for row in table.rows)
        return "\n".join(parts)
    except Exception as exc:
        raise HTTPException(status_code=422, detail="The DOCX document could not be read") from exc


@app.get("/health")
def health():
    return {"status": "ok"}


def parse_bytes(data: bytes, filename: str) -> ParsedResume:
    if not filename.lower().endswith((".pdf", ".docx")):
        raise HTTPException(status_code=415, detail="Only PDF and DOCX resumes are supported")
    if not data or len(data) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="Resume must be between 1 byte and 8 MB")
    if filename.lower().endswith(".pdf") and not data.startswith(b"%PDF-"):
        raise HTTPException(status_code=422, detail="File content is not a PDF")
    if filename.lower().endswith(".docx") and not data.startswith(b"PK"):
        raise HTTPException(status_code=422, detail="File content is not a DOCX archive")
    text = extract_text(data, filename)[:100_000]
    if not text.strip():
        raise HTTPException(status_code=422, detail="No selectable text found; scanned resumes need OCR")
    email = re.search(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", text, re.I)
    phone = re.search(r"(?<!\w)(?:\+?\d[\d ().-]{7,}\d)(?!\w)", text)
    skills = []
    catalog = ["Java", "Python", "JavaScript", "TypeScript", "React", "Spring Boot", "SQL", "PostgreSQL", "AWS", "Docker", "Git", "REST APIs"]
    for skill in catalog:
        match = re.search(rf"(?<![\w]){re.escape(skill)}(?![\w])", text, re.I)
        if match:
            skills.append(skill)
    evidence = []
    for field, match in (("email", email), ("phone", phone)):
        if match:
            evidence.append(Evidence(field=field, value=match.group(0), excerpt=text[max(0, match.start()-40):match.end()+40].replace("\n", " ")))
    for skill in skills:
        match = re.search(re.escape(skill), text, re.I)
        evidence.append(Evidence(field="skill", value=skill, excerpt=text[max(0, match.start()-40):match.end()+40].replace("\n", " ")))
    return ParsedResume(extractedText=text, structuredData={"email": email.group(0) if email else None, "phone": phone.group(0) if phone else None, "skills": skills, "needsReview": True}, evidence=evidence)


@app.post("/v1/resumes/parse", response_model=ParsedResume)
async def parse_resume(file: UploadFile = File(...)):
    return parse_bytes(await file.read(MAX_BYTES + 1), file.filename or "resume")


@app.post("/v1/resumes/parse-raw", response_model=ParsedResume)
def parse_resume_raw(data: bytes = Body(..., media_type="application/octet-stream"), filename: str = Query(..., max_length=255)):
    return parse_bytes(data, filename)


@app.post("/v1/resumes/export")
def export_resume(payload: ExportRequest, format: str = Query(..., pattern="^(pdf|docx)$")):
    text = payload.extractedText.strip()
    if not text or len(text) > 100_000:
        raise HTTPException(status_code=422, detail="Resume text must be between 1 and 100,000 characters")
    if format == "docx":
        document = Document()
        for line in text.splitlines():
            document.add_paragraph(line)
        output = io.BytesIO()
        document.save(output)
        return Response(output.getvalue(), media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document", headers={"Content-Disposition": "attachment; filename=resume.docx"})
    output = io.BytesIO()
    document = fitz.open()
    page = document.new_page()
    y = 48
    for line in text.splitlines() or [text]:
        words = line.split()
        wrapped: list[str] = []
        current = ""
        for word in words:
            candidate = f"{current} {word}".strip()
            if fitz.get_text_length(candidate, fontsize=10) > 510 and current:
                wrapped.append(current)
                current = word
            else:
                current = candidate
        if current:
            wrapped.append(current)
        for piece in wrapped or [""]:
            if y > 744:
                page = document.new_page()
                y = 48
            page.insert_text((48, y), piece, fontsize=10)
            y += 15
        y += 3
    document.save(output)
    document.close()
    return Response(output.getvalue(), media_type="application/pdf", headers={"Content-Disposition": "attachment; filename=resume.pdf"})
