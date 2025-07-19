# Fish Speech TTS Service Dockerfile - Build from source for platform compatibility
FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV GRADIO_SERVER_NAME=0.0.0.0
ENV HF_HUB_CACHE=/checkpoints/.cache
ENV TORCH_DEVICE=cpu

# Install system dependencies
RUN apt-get update && apt-get install -y \
    git \
    ffmpeg \
    libsndfile1 \
    portaudio19-dev \
    libsox-dev \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Clone Fish Speech repository
RUN git clone https://github.com/fishaudio/fish-speech.git /app/fish-speech

# Set working directory to fish-speech
WORKDIR /app/fish-speech

# Upgrade pip and install dependencies
RUN python -m pip install --upgrade pip

# Install Fish Speech requirements
RUN pip install --no-cache-dir -e .

# Install additional Korean language dependencies
RUN pip install --no-cache-dir \
    hangul-romanize>=1.0.0 \
    g2pkk>=0.1.1 \
    jamo>=0.4.1

# Create directories for models and cache
RUN mkdir -p /checkpoints/.cache
RUN mkdir -p /checkpoints/openaudio-s1-mini
RUN mkdir -p /references/korean

# Create model directories (models will be downloaded on first run)
# Using OpenAudio S1-mini for faster inference and platform compatibility

# Expose port for API server
EXPOSE 5002

# Health check (simplified for CPU inference)
HEALTHCHECK --interval=30s --timeout=60s --start-period=180s --retries=3 \
    CMD python -c "import requests; import time; time.sleep(2); requests.get('http://localhost:5002/health', timeout=10)" || exit 1

# Create startup script with HF authentication
RUN echo '#!/bin/bash\n\
echo "Starting Fish Speech API server..."\n\
echo "Checking HF authentication..."\n\
\n\
# Set up HF authentication if token is provided\n\
if [ ! -z "$HF_TOKEN" ]; then\n\
    echo "Setting up Hugging Face authentication"\n\
    huggingface-cli login --token $HF_TOKEN --add-to-git-credential\n\
fi\n\
\n\
echo "Checking models..."\n\
# Download models if not present\n\
if [ ! -f "/checkpoints/openaudio-s1-mini/config.json" ]; then\n\
    echo "Downloading Fish Speech openaudio-s1-mini models..."\n\
    huggingface-cli download fishaudio/openaudio-s1-mini --local-dir /checkpoints/openaudio-s1-mini || echo "Model download failed, check authentication"\n\
fi\n\
\n\
if [ -f "/checkpoints/openaudio-s1-mini/config.json" ]; then\n\
    echo "Using openaudio-s1-mini model"\n\
    echo "Checkpoint path: /checkpoints/openaudio-s1-mini"\n\
    echo "Listening on: 0.0.0.0:5002"\n\
    python -m tools.api_server \\\n\
        --listen 0.0.0.0:5002 \\\n\
        --llama-checkpoint-path /checkpoints/openaudio-s1-mini \\\n\
        --decoder-checkpoint-path /checkpoints/openaudio-s1-mini/codec.pth \\\n\
        --decoder-config-name modded_dac_vq\n\
else\n\
    echo "ERROR: Models not found. Please check HF_TOKEN authentication."\n\
    exit 1\n\
fi' > /app/start.sh

RUN chmod +x /app/start.sh

# Start Fish Speech API server
CMD ["/app/start.sh"]