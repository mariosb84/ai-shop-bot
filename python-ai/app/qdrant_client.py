import os
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct

from .embeddings import embed_text

QDRANT_HOST = os.getenv("QDRANT_HOST", "localhost")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", 6333))
COLLECTION_NAME = "products"
VECTOR_SIZE = 384  # размер вектора для paraphrase-multilingual-MiniLM-L12-v2

_client = None


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
    """
    products: [{"id": 1, "name": "...", "description": "...", "price": 1500}, ...]
    """
    client = get_client()
    ensure_collection()

    points = []
    for p in products:
        text = f"{p['name']}. {p.get('description', '')}"
        vector = embed_text(text)
        points.append(PointStruct(id=p["id"], vector=vector, payload=p))

    client.upsert(collection_name=COLLECTION_NAME, points=points)
    print(f"Проиндексировано {len(points)} товаров")


def search_products(query: str, limit: int = 5) -> list[dict]:
    client = get_client()
    query_vector = embed_text(query)

    results = client.query_points(
        collection_name=COLLECTION_NAME,
        query=query_vector,
        limit=limit,
    ).points
    return [hit.payload for hit in results]