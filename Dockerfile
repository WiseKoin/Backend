# Base image
FROM python:3.11-slim

# Do not create pyc + log realtime
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# Set workdir
WORKDIR /app

# Install dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements to cache
COPY requirements.txt .

# Upgrade pip + install packages
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Expose port
EXPOSE 8000

# Run app
CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8000"]
