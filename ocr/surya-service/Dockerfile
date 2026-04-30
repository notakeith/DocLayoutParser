FROM python:3.10-slim

RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY surya_server.py .

RUN mkdir -p /root/.cache/torch /root/.cache/huggingface

ENV PYTHONUNBUFFERED=1
ENV LLM_URL=http://llm-service:5001
ENV LLM_ENABLED=1

EXPOSE 5000

CMD ["python", "-m", "uvicorn", "surya_server:app", "--host", "0.0.0.0", "--port", "5000"]