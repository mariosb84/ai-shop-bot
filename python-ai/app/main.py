from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from .llm import chat_with_deepseek
from .qdrant_client import ensure_collection, index_products, search_products

app = FastAPI(title="AI Shop Assistant")


class ChatRequest(BaseModel):
    message: str
    system_prompt: str | None = None


class ChatResponse(BaseModel):
    reply: str


class ProductItem(BaseModel):
    id: int
    name: str
    description: str | None = ""
    price: float


class IndexRequest(BaseModel):
    products: list[ProductItem]


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/index")
async def index(request: IndexRequest):
    try:
        products = [p.model_dump() for p in request.products]
        index_products(products)
        return {"indexed": len(products)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/search")
async def search(request: ChatRequest):
    try:
        results = search_products(request.message, limit=5)
        return {"products": results}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    try:
        # Ищем релевантные товары через RAG
        relevant = search_products(request.message, limit=5)

        if relevant:
            catalog = "\n".join(
                f"- {p['name']}: {p.get('description', '')} ({p['price']} ₽)"
                for p in relevant
            )
            system_prompt = (
                "Ты — вежливый консультант интернет-магазина. "
                "Отвечай кратко, дружелюбно и по делу. "
                "Вот релевантные товары из каталога:\n" + catalog + "\n\n"
                "Если подходящих товаров нет — скажи об этом честно."
            )
        else:
            system_prompt = (
                "Ты — вежливый консультант интернет-магазина. "
                "Сейчас подходящих товаров в каталоге не найдено."
            )

        reply = await chat_with_deepseek(request.message, system_prompt)
        return ChatResponse(reply=reply)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.on_event("startup")
async def startup():
    try:
        ensure_collection()
    except Exception as e:
        print(f"Ошибка при инициализации Qdrant: {e}")