#!/usr/bin/env python3
"""
Whisper STT Server with FastAPI
Provides Korean speech-to-text services for Novel MVP
"""

import os
import io
import logging
import tempfile
from typing import Optional, List, Dict, Any
from pathlib import Path

import whisper
import uvicorn
from fastapi import FastAPI, File, UploadFile, HTTPException, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import torch
import numpy as np
import librosa
from datetime import datetime

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Configuration
class Config:
    MODEL_NAME = os.getenv("WHISPER_MODEL", "base")  # base, small, medium, large
    DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
    LANGUAGE = "ko"  # Korean as default
    TEMPERATURE = 0.0
    MAX_FILE_SIZE = 25 * 1024 * 1024  # 25MB
    SAMPLE_RATE = 16000
    
config = Config()

# Pydantic models
class TranscriptionResponse(BaseModel):
    text: str
    language: Optional[str] = None
    duration: Optional[float] = None
    segments: Optional[List[Dict[str, Any]]] = None
    confidence: Optional[float] = None

class LanguageDetectionResponse(BaseModel):
    detected_language: str
    confidence: float

class HealthResponse(BaseModel):
    status: str
    model: str
    device: str
    timestamp: str

class ModelInfo(BaseModel):
    name: str
    size: str
    parameters: str
    languages: List[str]

# Initialize FastAPI app
app = FastAPI(
    title="Whisper STT Server",
    description="OpenAI Whisper Speech-to-Text API for Novel MVP",
    version="1.0.0"
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global model instance
whisper_model = None

def load_whisper_model():
    """Load Whisper model on startup"""
    global whisper_model
    try:
        logger.info(f"Loading Whisper model: {config.MODEL_NAME} on {config.DEVICE}")
        whisper_model = whisper.load_model(config.MODEL_NAME, device=config.DEVICE)
        logger.info("Whisper model loaded successfully")
    except Exception as e:
        logger.error(f"Failed to load Whisper model: {e}")
        raise

def preprocess_audio(audio_file: bytes) -> np.ndarray:
    """Preprocess audio file for Whisper"""
    try:
        # Load audio from bytes
        audio_data, sr = librosa.load(io.BytesIO(audio_file), sr=config.SAMPLE_RATE)
        
        # Normalize audio
        audio_data = audio_data.astype(np.float32)
        
        # Pad or trim to 30 seconds (Whisper's input length)
        max_length = config.SAMPLE_RATE * 30  # 30 seconds
        if len(audio_data) > max_length:
            audio_data = audio_data[:max_length]
        else:
            # Pad with zeros if shorter than 30 seconds
            audio_data = np.pad(audio_data, (0, max_length - len(audio_data)))
        
        return audio_data
    except Exception as e:
        logger.error(f"Audio preprocessing error: {e}")
        raise

@app.on_event("startup")
async def startup_event():
    """Initialize Whisper model on startup"""
    load_whisper_model()

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint"""
    return HealthResponse(
        status="healthy" if whisper_model is not None else "unhealthy",
        model=config.MODEL_NAME,
        device=config.DEVICE,
        timestamp=datetime.now().isoformat()
    )

@app.get("/v1/models")
async def get_models():
    """Get available Whisper models"""
    models = [
        {"id": "tiny", "size": "39M", "description": "Fastest, lowest accuracy"},
        {"id": "base", "size": "74M", "description": "Good balance of speed and accuracy"},
        {"id": "small", "size": "244M", "description": "Better accuracy, slower"},
        {"id": "medium", "size": "769M", "description": "High accuracy"},
        {"id": "large", "size": "1550M", "description": "Best accuracy, slowest"},
    ]
    return {"data": models}

@app.post("/v1/audio/transcriptions", response_model=TranscriptionResponse)
async def transcribe_audio(
    file: UploadFile = File(...),
    model: str = Form(config.MODEL_NAME),
    language: Optional[str] = Form(config.LANGUAGE),
    response_format: str = Form("json"),
    temperature: float = Form(config.TEMPERATURE),
    timestamp_granularities: Optional[str] = Form(None)
):
    """
    Transcribe audio to text using Whisper
    """
    if whisper_model is None:
        raise HTTPException(status_code=503, detail="Whisper model not loaded")
    
    try:
        # Validate file size
        content = await file.read()
        if len(content) > config.MAX_FILE_SIZE:
            raise HTTPException(
                status_code=413, 
                detail=f"File too large. Maximum size: {config.MAX_FILE_SIZE} bytes"
            )
        
        # Preprocess audio
        audio_data = preprocess_audio(content)
        
        # Transcription options
        options = {
            "language": language,
            "temperature": temperature,
        }
        
        # Add timestamp granularities if requested
        if timestamp_granularities:
            options["word_timestamps"] = True
        
        # Perform transcription
        logger.info(f"Transcribing audio with language: {language}")
        result = whisper_model.transcribe(audio_data, **options)
        
        # Calculate confidence (approximate)
        confidence = 1.0 - (result.get("avg_logprob", -1.0) / -1.0) if "avg_logprob" in result else None
        
        response = TranscriptionResponse(
            text=result["text"].strip(),
            language=result.get("language"),
            duration=result.get("duration"),
            segments=result.get("segments"),
            confidence=confidence
        )
        
        logger.info(f"Transcription completed: '{response.text[:50]}...'")
        return response
        
    except Exception as e:
        logger.error(f"Transcription error: {e}")
        raise HTTPException(status_code=500, detail=f"Transcription failed: {str(e)}")

@app.post("/v1/audio/detect-language", response_model=LanguageDetectionResponse)
async def detect_language(file: UploadFile = File(...)):
    """
    Detect language from audio
    """
    if whisper_model is None:
        raise HTTPException(status_code=503, detail="Whisper model not loaded")
    
    try:
        content = await file.read()
        if len(content) > config.MAX_FILE_SIZE:
            raise HTTPException(status_code=413, detail="File too large")
        
        # Preprocess audio
        audio_data = preprocess_audio(content)
        
        # Use only first 30 seconds for language detection
        audio_segment = audio_data[:config.SAMPLE_RATE * 30]
        
        # Detect language
        audio_tensor = torch.from_numpy(audio_segment).to(config.DEVICE)
        mel = whisper.log_mel_spectrogram(audio_tensor, n_mels=whisper_model.dims.n_mels)
        
        _, probs = whisper_model.detect_language(mel)
        detected_language = max(probs, key=probs.get)
        confidence = probs[detected_language]
        
        logger.info(f"Detected language: {detected_language} (confidence: {confidence:.2f})")
        
        return LanguageDetectionResponse(
            detected_language=detected_language,
            confidence=confidence
        )
        
    except Exception as e:
        logger.error(f"Language detection error: {e}")
        raise HTTPException(status_code=500, detail=f"Language detection failed: {str(e)}")

@app.post("/transcribe-quick")
async def transcribe_quick(file: UploadFile = File(...)):
    """
    Quick transcription endpoint for real-time chat
    """
    try:
        content = await file.read()
        audio_data = preprocess_audio(content)
        
        # Quick transcription with minimal options
        result = whisper_model.transcribe(
            audio_data,
            language=config.LANGUAGE,
            temperature=0.0,
            word_timestamps=False
        )
        
        return {"text": result["text"].strip()}
        
    except Exception as e:
        logger.error(f"Quick transcription error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/transcribe-stream")
async def transcribe_stream(file: UploadFile = File(...)):
    """
    Streaming transcription for real-time processing
    """
    try:
        content = await file.read()
        audio_data = preprocess_audio(content)
        
        # Transcribe with segments for streaming
        result = whisper_model.transcribe(
            audio_data,
            language=config.LANGUAGE,
            word_timestamps=True,
            condition_on_previous_text=True
        )
        
        # Return segments for streaming
        segments = []
        for segment in result.get("segments", []):
            segments.append({
                "text": segment["text"].strip(),
                "start": segment["start"],
                "end": segment["end"],
                "confidence": 1.0 - abs(segment.get("avg_logprob", -1.0))
            })
        
        return {
            "text": result["text"].strip(),
            "segments": segments,
            "language": result.get("language")
        }
        
    except Exception as e:
        logger.error(f"Stream transcription error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/info")
async def get_info():
    """Get server information"""
    return {
        "service": "Whisper STT Server",
        "version": "1.0.0",
        "model": config.MODEL_NAME,
        "device": config.DEVICE,
        "supported_languages": [
            "ko", "en", "ja", "zh", "es", "fr", "de", "ru", "it", "pt",
            "pl", "tr", "nl", "ar", "sv", "he", "hu", "uk", "vi", "th"
        ],
        "features": [
            "Real-time transcription",
            "Language detection", 
            "Korean language optimized",
            "Streaming support",
            "Multiple model sizes"
        ]
    }

if __name__ == "__main__":
    # Development server
    uvicorn.run(
        "app:app",
        host="0.0.0.0",
        port=5001,
        reload=True,
        log_level="info"
    )