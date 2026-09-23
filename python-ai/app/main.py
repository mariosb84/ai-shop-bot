from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from .llm import chat_with_deepseek

app = FastAPI(title="AI Shop Assistant")


class ChatRequest(BaseModel):
    message: str
    system_prompt: str | None = None


class ChatResponse(BaseModel):
    reply: str


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    try:
        reply = await chat_with_deepseek(request.message, request.system_prompt)
        return ChatResponse(reply=reply)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))