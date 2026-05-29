import re

def chunk_text(text: str, min_chunk_size: int = 500, max_chunk_size: int = 800, overlap: int = 50) -> list:
    """将文本分块，每块 500-800 字，块之间保留重叠"""
    paragraphs = re.split(r'\n\s*\n', text)
    paragraphs = [p.strip() for p in paragraphs if p.strip()]

    chunks = []
    current_chunk = ""
    current_size = 0

    for para in paragraphs:
        para_len = len(para)
        if current_size + para_len <= max_chunk_size:
            current_chunk += para + "\n\n"
            current_size += para_len + 2
        else:
            if current_chunk:
                chunks.append(current_chunk.strip())
            if overlap > 0 and len(current_chunk) > overlap:
                overlap_text = current_chunk[-overlap:]
                current_chunk = overlap_text + para + "\n\n"
            else:
                current_chunk = para + "\n\n"
            current_size = len(para)

    if current_chunk.strip():
        chunks.append(current_chunk.strip())

    final_chunks = []
    for chunk in chunks:
        if len(chunk) > max_chunk_size:
            for i in range(0, len(chunk), max_chunk_size - overlap):
                part = chunk[i:i + max_chunk_size]
                if part.strip():
                    final_chunks.append(part.strip())
        else:
            final_chunks.append(chunk)

    return final_chunks
