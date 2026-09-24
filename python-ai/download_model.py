import os
import requests
from pathlib import Path

# Отключаем проверку SSL для всех запросов
os.environ["PYTHONHTTPSVERIFY"] = "0"
os.environ["CURL_CA_BUNDLE"] = ""
os.environ["REQUESTS_CA_BUNDLE"] = ""

REPO = "Qdrant/paraphrase-multilingual-MiniLM-L12-v2-onnx-Q"  # <-- Правильный ID
OUT_DIR = Path("/models/model")
OUT_DIR.mkdir(parents=True, exist_ok=True)

# Получаем список файлов через API
api_url = f"https://huggingface.co/api/models/{REPO}"
print(f"Запрос списка файлов: {api_url}")

# Используем requests с verify=False
response = requests.get(api_url, verify=False)
response.raise_for_status()
data = response.json()

files = [f["rfilename"] for f in data["siblings"]]
print(f"Файлов найдено: {len(files)}")
for f in files:
    print(f"  - {f}")

# Скачиваем каждый файл
base_url = f"https://huggingface.co/{REPO}/resolve/main"

for f in files:
    # Пропускаем служебные файлы
    if f.endswith(".md") or f.startswith("."):
        continue

    target = OUT_DIR / f
    target.parent.mkdir(parents=True, exist_ok=True)

    print(f"Скачиваю {f}...")
    url = f"{base_url}/{f}"
    try:
        # Скачиваем с verify=False
        r = requests.get(url, stream=True, verify=False)
        r.raise_for_status()
        with open(target, "wb") as f_out:
            for chunk in r.iter_content(chunk_size=8192):
                f_out.write(chunk)
        size = target.stat().st_size
        print(f"  ✅ {f} ({size} байт)")
    except Exception as e:
        print(f"  ❌ Ошибка {f}: {e}")

print("✅ Готово")