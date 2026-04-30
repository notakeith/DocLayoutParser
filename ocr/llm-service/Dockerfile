FROM python:3.10-slim


RUN apt-get update && apt-get install -y \
    g++ \
    make \
    cmake \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY llm_server.py .

ENV PYTHONUNBUFFERED=1
ENV LLM_MODEL=mlx-community/Qwen2.5-7B-Instruct-4bit

# ENV LLM_MODEL=Qwen/Qwen2.5-7B-Instruct-GGUF

EXPOSE 5001

CMD ["python", "-m", "uvicorn", "llm_server:app", "--host", "0.0.0.0", "--port", "5001"]