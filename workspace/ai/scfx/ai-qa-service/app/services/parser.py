import pdfplumber
from docx import Document
import re

def parse_pdf(file_path: str) -> str:
    """从 PDF 提取文本"""
    text_parts = []
    with pdfplumber.open(file_path) as pdf:
        for page in pdf.pages:
            page_text = page.extract_text()
            if page_text:
                text_parts.append(page_text)
    return "\n\n".join(text_parts)

def parse_docx(file_path: str) -> str:
    """从 Word 文档提取文本"""
    doc = Document(file_path)
    paragraphs = [p.text for p in doc.paragraphs if p.text.strip()]
    return "\n\n".join(paragraphs)

def parse_txt(file_path: str) -> str:
    """读取 TXT 文件"""
    with open(file_path, 'r', encoding='utf-8') as f:
        return f.read()

def parse_document(file_path: str, file_type: str) -> str:
    """根据文件类型解析"""
    if file_type == 'pdf':
        return parse_pdf(file_path)
    elif file_type in ['doc', 'docx']:
        return parse_docx(file_path)
    elif file_type == 'txt':
        return parse_txt(file_path)
    else:
        raise ValueError(f"Unsupported file type: {file_type}")

def clean_text(text: str) -> str:
    """清洗文本，去除特殊字符"""
    text = re.sub(r'\s+', ' ', text)
    text = re.sub(r'[\x00-\x08\x0b-\x0c\x0e-\x1f\x7f]', '', text)
    return text.strip()
