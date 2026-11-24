from fastapi import UploadFile, File, Form
import io
import pandas as pd

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from .llm import chat_with_deepseek
from .vector_store import (
    ensure_collection,
    index_products,
    search_products,
    clear_shop_products,
)

app = FastAPI(title="AI Shop Assistant")


class ChatRequest(BaseModel):
    message: str
    shop_id: int
    system_prompt: str | None = None


class ProductShort(BaseModel):
    id: int
    name: str
    category: str | None = ""
    description: str | None = ""
    price: float
    image_url: str | None = ""


class ChatResponse(BaseModel):
    reply: str
    products: list[ProductShort]


class ProductItem(BaseModel):
    id: int
    shop_id: int
    name: str
    category: str | None = ""
    description: str | None = ""
    price: float
    image_url: str | None = ""


class IndexRequest(BaseModel):
    products: list[ProductItem]


class ClearShopRequest(BaseModel):
    shop_id: int


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


@app.post("/clear-shop")
async def clear_shop(request: ClearShopRequest):
    try:
        clear_shop_products(request.shop_id)
        return {"cleared": True, "shop_id": request.shop_id}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/load-catalog")
async def load_catalog(shop_id: int = Form(...), file: UploadFile = File(...)):
    try:
        content = await file.read()
        df = pd.read_excel(io.BytesIO(content))

        required = {"name", "price"}
        missing = required - set(df.columns)
        if missing:
            raise HTTPException(status_code=400, detail=f"Нет колонок: {missing}")

        for col in ("category", "description", "image_url"):
            if col not in df.columns:
                df[col] = ""

        df = df.dropna(subset=["name", "price"])

        products = []
        for _, row in df.iterrows():
            products.append({
                "name": str(row["name"]).strip(),
                "category": str(row["category"]).strip() if pd.notna(row["category"]) else "",
                "description": str(row["description"]).strip() if pd.notna(row["description"]) else "",
                "price": float(row["price"]),
                "image_url": str(row["image_url"]).strip() if pd.notna(row["image_url"]) else "",
            })

        clear_shop_products(shop_id)
        return {"shop_id": shop_id, "products": products}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    try:
        relevant = search_products(request.message, shop_id=request.shop_id, limit=3)

        if relevant:
            catalog_lines = []
            for p in relevant:
                line = f"- {p['name']}"
                if p.get("category"):
                    line += f" [категория: {p['category']}]"
                line += f": {p.get('description', '')} ({p['price']} ₽)"
                catalog_lines.append(line)

            catalog = "\n".join(catalog_lines)
            system_prompt = (
                "Ты — вежливый консультант интернет-магазина. "
                "Отвечай кратко, дружелюбно и по делу. "
                "Вот релевантные товары из каталога:\n" + catalog + "\n\n"
                "ВАЖНО: указывай только те цены, что даны в каталоге выше. "
                "Не придумывай товары и цены, которых нет. "
                "Если подходящих товаров нет — скажи об этом честно."
            )
        else:
            system_prompt = (
                "Ты — вежливый консультант интернет-магазина. "
                "Сейчас подходящих товаров в каталоге не найдено."
            )

        reply = await chat_with_deepseek(request.message, system_prompt)

        products_short = [
            ProductShort(
                id=p["id"],
                name=p["name"],
                category=p.get("category", ""),
                description=p.get("description", ""),
                price=p["price"],
                image_url=p.get("image_url", ""),
            )
            for p in relevant
        ]

        return ChatResponse(reply=reply, products=products_short)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.on_event("startup")
async def startup():
    try:
        ensure_collection()
    except Exception as e:
        print(f"Ошибка при инициализации Qdrant: {e}")
