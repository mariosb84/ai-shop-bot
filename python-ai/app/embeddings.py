from pathlib import Path
from fastembed import TextEmbedding

_model = None


def get_model():
    global _model
    if _model is None:
        model_path = Path("/app/model")
        if not model_path.exists():
            raise RuntimeError(f"Локальная модель не найдена: {model_path}")

        print(f"Загрузка модели из {model_path}...")
        _model = TextEmbedding(
            model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
            specific_model_path=str(model_path)
        )
        print("Модель загружена")
    return _model


def embed_text(text: str) -> list[float]:
    model = get_model()
    vector = list(model.embed([text]))[0]
    return vector.tolist()