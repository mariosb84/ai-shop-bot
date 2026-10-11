"""
Генерация тестового Excel-каталога на 50 товаров.
Запуск: python generate_test_catalog.py
"""
import random

import pandas as pd


# Категории и названия товаров
CATEGORIES = {
    "обувь": [
        "Кроссовки", "Ботинки", "Кеды", "Туфли", "Сандалии",
        "Мокасины", "Лоферы", "Угги", "Слипоны", "Балетки",
    ],
    "верхняя одежда": [
        "Куртка", "Пальто", "Пуховик", "Ветровка", "Плащ",
        "Парка", "Бомбер", "Тренч", "Дублёнка", "Косуха",
    ],
    "футболки": [
        "Футболка", "Лонгслив", "Поло", "Рубашка", "Топ",
    ],
    "штаны": [
        "Джинсы", "Брюки", "Шорты", "Леггинсы", "Чиносы",
    ],
    "аксессуары": [
        "Шарф", "Шапка", "Перчатки", "Ремень", "Сумка",
        "Рюкзак", "Кошелёк", "Очки", "Часы", "Браслет",
    ],
}

BRANDS = [
    "Classic", "Premium", "Urban", "Sport", "Casual",
    "Elegance", "Street", "Nordic", "Vintage", "Modern",
]

COLORS = [
    "чёрный", "белый", "серый", "синий", "красный",
    "зелёный", "бежевый", "коричневый", "бордовый",
]

SIZES = ["XS", "S", "M", "L", "XL", "XXL"]

MATERIALS = [
    "хлопок", "лён", "шерсть", "кожа", "замша",
    "полиэстер", "деним", "трикотаж",
]


def generate_product(index: int) -> dict:
    """Генерирует один товар."""
    category = random.choice(list(CATEGORIES.keys()))
    base_name = random.choice(CATEGORIES[category])
    brand = random.choice(BRANDS)
    color = random.choice(COLORS)
    material = random.choice(MATERIALS)

    name = f"{base_name} {brand}"

    # Описание
    if category in ["обувь", "штаны"]:
        size_info = f"размеры {random.randint(36, 46)}-{random.randint(37, 47)}"
    else:
        size_info = f"размеры {random.choice(SIZES)}-{random.choice(SIZES)}"

    description = (
        f"{material.capitalize()}, {size_info}, "
        f"цвет {color}, {random.choice(['мужская', 'женская', 'унисекс'])} модель"
    )

    # Цена от 500 до 25000
    price = random.choice([500, 800, 1200, 1500, 2000, 2500, 3000, 3500,
                           4500, 5000, 6000, 7500, 9000, 12000, 15000, 20000])

    image_url = f"https://picsum.photos/seed/product{index}/600/400"

    return {
        "name": name,
        "description": description,
        "price": price,
        "image_url": image_url,
    }


def main():
    # Генерируем 50 товаров
    products = [generate_product(i) for i in range(1, 51)]

    # Создаём DataFrame
    df = pd.DataFrame(products)

    # Сохраняем в Excel
    output_file = "catalog_test_50.xlsx"
    df.to_excel(output_file, index=False, engine="openpyxl")

    print(f"✅ Создан файл: {output_file}")
    print(f"📦 Товаров: {len(df)}")
    print()
    print("Первые 5 товаров:")
    print(df.head().to_string())


if __name__ == "__main__":
    main()