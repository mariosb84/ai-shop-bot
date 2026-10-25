import os
import re

from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance, VectorParams, PointStruct,
    Filter, FieldCondition, MatchValue
)
from rank_bm25 import BM25Okapi
import pymorphy3

from .embeddings import embed_text

QDRANT_HOST = os.getenv("QDRANT_HOST", "localhost")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", 6333))
COLLECTION_NAME = "products"
VECTOR_SIZE = 384

# RRF
RRF_K = 60

_client = None
_products_cache: list[dict] = []

# Морфологический анализатор
_morph = pymorphy3.MorphAnalyzer()

# Стоп-слова
STOP_WORDS = {
    "есть", "покажи", "какие", "какой", "какая", "что", "у", "в", "на", "и", "а",
    "то", "для", "мне", "можно", "хочу", "нужно", "нужен", "нужна", "дай", "давай",
    "найди", "поищи", "интересует", "интересно", "скажи", "подскажи", "плиз",
    "пожалуйста", "если", "ли", "бы", "был", "была", "были", "будет", "будь",
    "продаже", "продажа", "наличии", "наличие",
}


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
        # В вектор идёт название + категория + описание
        text = (
            f"{p['name']}. "
            f"{p.get('category', '')}. "
            f"{p.get('description', '')}"
        )
        vector = embed_text(text)
        points.append(PointStruct(
            id=p["id"],
            vector=vector,
            payload={
                "id": p["id"],
                "shop_id": p["shop_id"],
                "name": p["name"],
                "category": p.get("category", ""),
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


def _normalize(word: str) -> str:
    """Лемматизация через pymorphy3."""
    parsed = _morph.parse(word)
    if parsed:
        return parsed[0].normal_form
    return word


def _tokenize(text: str) -> list[str]:
    """Токенизация: знаки препинания, стоп-слова, лемматизация."""
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


def _bm25_search(query: str, shop_id: int, limit: int) -> list[dict]:
    """BM25 с весом названия x3 и категорией."""
    shop_products = [p for p in _products_cache if p.get("shop_id") == shop_id]
    if not shop_products:
        return []

    corpus = [
        _tokenize(
            f"{p['name']} {p['name']} {p['name']} "
            f"{p.get('category', '')} "
            f"{p.get('description', '')}"
        )
        for p in shop_products
    ]

    query_tokens = _tokenize(query)
    if not query_tokens:
        return []

    bm25 = BM25Okapi(corpus)
    scores = bm25.get_scores(query_tokens)

    ranked = sorted(zip(scores, shop_products), key=lambda x: x[0], reverse=True)
    return [p for score, p in ranked[:limit] if score > 0]


def _vector_search(query: str, shop_id: int, limit: int) -> list[dict]:
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


def _reciprocal_rank_fusion(
    bm25_results: list[dict],
    vector_results: list[dict],
    limit: int,
) -> list[dict]:
    scores: dict[int, float] = {}
    products_by_id: dict[int, dict] = {}

    for rank, product in enumerate(bm25_results):
        pid = product["id"]
        scores[pid] = scores.get(pid, 0) + 1.0 / (rank + 1 + RRF_K)
        products_by_id[pid] = product

    for rank, product in enumerate(vector_results):
        pid = product["id"]
        scores[pid] = scores.get(pid, 0) + 1.0 / (rank + 1 + RRF_K)
        products_by_id[pid] = product

    sorted_ids = sorted(scores.keys(), key=lambda pid: scores[pid], reverse=True)
    return [products_by_id[pid] for pid in sorted_ids[:limit]]


def search_products(query: str, shop_id: int, limit: int = 5) -> list[dict]:
    search_limit = max(limit * 3, 10)

    bm25_results = _bm25_search(query, shop_id, search_limit)
    vector_results = _vector_search(query, shop_id, search_limit)

    combined = _reciprocal_rank_fusion(bm25_results, vector_results, limit)

    print(
        f"🔍 Поиск '{query}': BM25={len(bm25_results)}, "
        f"vector={len(vector_results)}, итого={len(combined)}"
    )
    return combined