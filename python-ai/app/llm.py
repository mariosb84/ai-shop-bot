import httpx
from .config import DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL


async def chat_with_deepseek(user_message: str, system_prompt: str = None) -> str:
    if system_prompt is None:
        system_prompt = "Ты — вежливый консультант интернет-магазина. Отвечай кратко и по делу."

    headers = {
        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": "deepseek-chat",
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message},
        ],
        "temperature": 0.7,
        "max_tokens": 500,
    }


    async with httpx.AsyncClient(timeout=30.0, verify=False) as client:
        response = await client.post(
            f"{DEEPSEEK_BASE_URL}/chat/completions",
            headers=headers,
            json=payload,
        )
        response.raise_for_status()
        data = response.json()
        return data["choices"][0]["message"]["content"]