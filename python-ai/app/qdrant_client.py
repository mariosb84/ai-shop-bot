import os
import re
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance, VectorParams, PointStruct,
    Filter, FieldCondition, MatchValue
)
from rank_bm25 import BM25Okapi

from .embeddings import embed_text

QDRANT_HOST = os.getenv("QDRANT_HOST", "localhost")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", 6333))
COLLECTION_NAME = "products"
VECTOR_SIZE = 384

# Константы для RRF
RRF_K = 60  # стандартный коэффициент сглаживания

_client = None
_products_cache: list[dict] = []


def get_client() -> QdrantClient:
    global _client
    if _client is None:
        _client = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)
    return _client


def ensure_collection():
    client = get_client()
    collections = [c.name for c in client.get_collections().collections]
    if COLLECTION_NAME not in collections:
        client.create_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=VectorParams(size=VECTOR_SIZE, distance=Distance.COSINE),
        )
        print(f"Создана коллекция {COLLECTION_NAME}")


def index_products(products: list[dict]):
    global _products_cache
    client = get_client()
    ensure_collection()

    points = []
    for p in products:
        text = f"{p['name']}. {p.get('description', '')}"
        vector = embed_text(text)
        points.append(PointStruct(
            id=p["id"],
            vector=vector,
            payload={
                "id": p["id"],
                "shop_id": p["shop_id"],
                "name": p["name"],
                "description": p.get("description", ""),
                "price": p["price"],
                "image_url": p.get("image_url", ""),
            }
        ))

    client.upsert(collection_name=COLLECTION_NAME, points=points)

    # Обновляем кэш для BM25
    shop_id = products[0]["shop_id"] if products else None
    if shop_id is not None:
        _products_cache = [p for p in _products_cache if p.get("shop_id") != shop_id]
        _products_cache.extend(products)

    print(f"✅ Проиндексировано {len(points)} товаров")


def clear_shop_products(shop_id: int):
    """Удаляет все товары магазина из Qdrant и из кэша."""
    global _products_cache
    client = get_client()

    client.delete(
        collection_name=COLLECTION_NAME,
        points_selector=Filter(
            must=[FieldCondition(key="shop_id", match=MatchValue(value=shop_id))]
        ),
    )

    _products_cache = [p for p in _products_cache if p.get("shop_id") != shop_id]
    print(f"✅ Очищены товары магазина shop_id={shop_id}")


# ========== ВСПОМОГАТЕЛЬНЫЕ ДЛЯ BM25 ==========

# Стоп-слова (не несут смысла для поиска товаров)
STOP_WORDS = {
    "есть", "покажи", "какие", "какой", "какая", "что", "у", "в", "на", "и", "а",
    "то", "для", "мне", "можно", "хочу", "нужно", "нужен", "нужна", "дай", "давай",
    "найди", "поищи", "интересует", "интересно", "скажи", "подскажи", "плиз",
    "пожалуйста", "если", "ли", "бы", "был", "была", "были", "будет", "будь",
    "продаже", "продажа", "наличии", "наличие",
}


def _normalize(word: str) -> str:
    """Убирает частые русские падежные окончания."""
    endings = [
        "ами", "ями", "ов", "ев", "ей", "ой", "ый", "ий", "ая", "яя",
        "ые", "ие", "ам", "ям", "ах", "ях", "у", "ю", "а", "я", "ы", "и", "е", "о",
    ]
    for end in endings:
        if len(word) > len(end) + 2 and word.endswith(end):
            return word[: -len(end)]
    return word


def _tokenize(text: str) -> list[str]:
    """Токенизация: убираем знаки препинания, стоп-слова, нормализуем."""
    words = text.lower().split()
    tokens = []
    for w in words:
        w = re.sub(r"[^\w]", "", w)
        if not w or w in STOP_WORDS:
            continue
        normalized = _normalize(w)
        if normalized:
            tokens.append(normalized)
    return tokens


# ========== BM25 ПОИСК ==========

def _bm25_search(query: str, shop_id: int, limit: int) -> list[dict]:
    """Поиск по ключевым словам через BM25."""
    shop_products = [p for p in _products_cache if p.get("shop_id") == shop_id]
    if not shop_products:
        return []

    corpus = [
        _tokenize(f"{p['name']} {p.get('description', '')}")
        for p in shop_products
    ]

    query_tokens = _tokenize(query)
    if not query_tokens:
        return []

    bm25 = BM25Okapi(corpus)
    scores = bm25.get_scores(query_tokens)

    ranked = sorted(zip(scores, shop_products), key=lambda x: x[0], reverse=True)
    return [p for score, p in ranked[:limit] if score > 0]


# ========== ВЕКТОРНЫЙ ПОИСК ==========

def _vector_search(query: str, shop_id: int, limit: int) -> list[dict]:
    """Семантический поиск через Qdrant."""
    client = get_client()
    query_vector = embed_text(query)

    results = client.query_points(
        collection_name=COLLECTION_NAME,
        query=query_vector,
        query_filter=Filter(
            must=[FieldCondition(key="shop_id", match=MatchValue(value=shop_id))]
        ),
        limit=limit,
    ).points
    return [hit.payload for hit in results]


# ========== RRF (Reciprocal Rank Fusion) ==========

def _reciprocal_rank_fusion(
    bm25_results: list[dict],
    vector_results: list[dict],
    limit: int,
) -> list[dict]:
    """
    Объединяет результаты двух поисковиков через RRF.
    Формула: score = 1 / (rank + K), где K=60.
    """
    scores: dict[int, float] = {}
    products_by_id: dict[int, dict] = {}

    # BM25 результаты
    for rank, product in enumerate(bm25_results):
        pid = product["id"]
        scores[pid] = scores.get(pid, 0) + 1.0 / (rank + 1 + RRF_K)
        products_by_id[pid] = product

    # Векторные результаты
    for rank, product in enumerate(vector_results):
        pid = product["id"]
        scores[pid] = scores.get(pid, 0) + 1.0 / (rank + 1 + RRF_K)
        products_by_id[pid] = product

    # Сортируем по сумме очков
    sorted_ids = sorted(scores.keys(), key=lambda pid: scores[pid], reverse=True)

    return [products_by_id[pid] for pid in sorted_ids[:limit]]


# ========== ГЛАВНАЯ ФУНКЦИЯ ПОИСКА ==========

def search_products(query: str, shop_id: int, limit: int = 5) -> list[dict]:
    """
    Гибридный поиск с RRF:
    1. BM25 — поиск по точным словам
    2. Векторный — поиск по смыслу
    3. RRF — правильное объединение результатов
    """
    # Ищем больше кандидатов, чем нужно, чтобы RRF было из чего выбирать
    search_limit = max(limit * 3, 10)

    bm25_results = _bm25_search(query, shop_id, search_limit)
    vector_results = _vector_search(query, shop_id, search_limit)

    # Объединяем через RRF
    combined = _reciprocal_rank_fusion(bm25_results, vector_results, limit)

    print(
        f"🔍 Поиск '{query}': BM25={len(bm25_results)}, "
        f"vector={len(vector_results)}, итого={len(combined)}"
    )
    return combined