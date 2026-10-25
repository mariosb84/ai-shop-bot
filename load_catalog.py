"""
Скрипт загрузки каталога товаров магазина из Excel.

Использование:
    python load_catalog.py --shop_id 1 --file catalog.xlsx

Формат Excel:
    | name | description | price | image_url |
"""
import argparse
import sys

import pandas as pd
import psycopg2
import requests


# Настройки подключения (те же, что и в docker-compose)
DB_HOST = "localhost"
DB_PORT = 5433
DB_NAME = "aishop_db"
DB_USER = "postgres"
DB_PASSWORD = "postgres"

# Адрес Python-сервиса (для очистки и индексации Qdrant)
AI_SERVICE_URL = "http://localhost:8000"


def read_excel(file_path: str) -> pd.DataFrame:
    """Читает Excel и валидирует колонки."""
    print(f"📖 Читаю файл: {file_path}")
    df = pd.read_excel(file_path)

    required = {"name", "price"}
    missing = required - set(df.columns)
    if missing:
        print(f"❌ В файле отсутствуют обязательные колонки: {missing}")
        sys.exit(1)

    # Приводим опциональные колонки к дефолтным значениям
    if "description" not in df.columns:
        df["description"] = ""
    if "image_url" not in df.columns:
        df["image_url"] = ""
    if "category" not in df.columns:
            df["category"] = ""

    # Убираем пустые строки
    df = df.dropna(subset=["name", "price"])

    print(f"✅ Загружено {len(df)} строк из Excel")
    return df


def delete_old_products(shop_id: int) -> list[int]:
    """Удаляет старые товары магазина из PostgreSQL и возвращает их ID."""
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USER, password=DB_PASSWORD,
    )
    cur = conn.cursor()

    # Получаем ID старых товаров
    cur.execute("SELECT id FROM products WHERE shop_id = %s", (shop_id,))
    old_ids = [row[0] for row in cur.fetchall()]

    if old_ids:
        cur.execute("DELETE FROM products WHERE shop_id = %s", (shop_id,))
        print(f"🗑️  Удалено {len(old_ids)} старых товаров магазина {shop_id}")

    conn.commit()
    cur.close()
    conn.close()
    return old_ids


def insert_new_products(shop_id: int, df: pd.DataFrame) -> list[dict]:
    """Вставляет новые товары в PostgreSQL и возвращает их с ID."""
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USER, password=DB_PASSWORD,
    )
    cur = conn.cursor()

    inserted = []
    for _, row in df.iterrows():
        cur.execute(
            """
            INSERT INTO products (name, category, description, price, image_url, active, shop_id)
            VALUES (%s, %s, %s, %s, %s, TRUE, %s)
            RETURNING id
            """,
            (
                str(row["name"]).strip(),
                str(row["category"]).strip() if pd.notna(row["category"]) else "",
                str(row["description"]).strip() if pd.notna(row["description"]) else "",
                float(row["price"]),
                str(row["image_url"]).strip() if pd.notna(row["image_url"]) else "",
                shop_id,
            ),
        )
        new_id = cur.fetchone()[0]
        inserted.append({
            "id": new_id,
            "shop_id": shop_id,
            "name": str(row["name"]).strip(),
            "category": str(row["category"]).strip() if pd.notna(row["category"]) else "",
            "description": str(row["description"]).strip() if pd.notna(row["description"]) else "",
            "price": float(row["price"]),
            "image_url": str(row["image_url"]).strip() if pd.notna(row["image_url"]) else "",
        })

    conn.commit()
    cur.close()
    conn.close()
    print(f"✅ Вставлено {len(inserted)} новых товаров в PostgreSQL")
    return inserted


def clear_qdrant(shop_id: int):
    """Очищает товары магазина в Qdrant."""
    try:
        r = requests.post(
            f"{AI_SERVICE_URL}/clear-shop",
            json={"shop_id": shop_id},
            timeout=30,
        )
        r.raise_for_status()
        print(f"🗑️  Qdrant: {r.json()}")
    except Exception as e:
        print(f"⚠️  Ошибка очистки Qdrant: {e}")


def index_qdrant(products: list[dict]):
    """Индексирует новые товары в Qdrant."""
    try:
        r = requests.post(
            f"{AI_SERVICE_URL}/index",
            json={"products": products},
            timeout=300,
        )
        r.raise_for_status()
        print(f"🔍 Qdrant индексация: {r.json()}")
    except Exception as e:
        print(f"⚠️  Ошибка индексации Qdrant: {e}")


def main():
    parser = argparse.ArgumentParser(description="Загрузка каталога магазина из Excel")
    parser.add_argument("--shop_id", type=int, required=True, help="ID магазина")
    parser.add_argument("--file", type=str, required=True, help="Путь к Excel-файлу")
    args = parser.parse_args()

    print(f"🚀 Начинаю загрузку каталога для магазина {args.shop_id}")
    print("=" * 60)

    # 1. Читаем Excel
    df = read_excel(args.file)

    # 2. Удаляем старые товары
    delete_old_products(args.shop_id)

    # 3. Очищаем Qdrant
    clear_qdrant(args.shop_id)

    # 4. Вставляем новые
    inserted = insert_new_products(args.shop_id, df)

    # 5. Индексируем в Qdrant
    index_qdrant(inserted)

    print("=" * 60)
    print(f"🎉 Готово! Загружено {len(inserted)} товаров")


if __name__ == "__main__":
    main()