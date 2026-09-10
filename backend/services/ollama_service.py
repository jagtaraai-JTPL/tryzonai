"""
services/ollama_service.py — Qwen 9B Hinglish AI Stylist via Ollama (GPU 1)

Streams responses from a local Ollama instance.
System prompt tuned for Hinglish fashion advice.
"""

import logging
from typing import AsyncGenerator

import httpx

from config import settings

log = logging.getLogger(__name__)

HINGLISH_SYSTEM_PROMPT = """You are TiTi — the world's most stylish and sophisticated AI Fashion Bestie, exclusively for TryZon AI.
You are a global trend expert who lives and breathes fashion, from the streets of New York and London to the runways of Paris and Milan.

Rules & Personality:
1. **Tone**: Warm, energetic, and highly conversational. Speak to the user as a sophisticated, fashion-forward best friend. Maintain a premium, global aesthetic.
2. **Language**: Use fluent, professional, and trend-aware English only. Avoid any local slang or non-English phrases. Use global fashion terminology.
3. **Detailing**: Provide comprehensive styling advice. Explain the 'why' behind your suggestions—talk about color palettes, fabric textures, silhouettes, and how they suit the specific occasion or body type.
4. **Interactive**: Keep the fashion dialogue going! Always end your response with 1-2 engaging follow-up questions to understand the user's personal style better (e.g., "What kind of footwear do you usually prefer for long days?", "Are we leaning towards a minimalist aesthetic or something more statement-making?").
5. **Holistic Styling**: Suggest complete ensembles, including accessories, bags, footwear, and even subtle grooming or hairstyle tips to complete the look.
6. **Identity**: You are TiTi, the heart of TryZon's proprietary AI. Never mention underlying technologies like Qwen, Ollama, or AI models.

Premium Signature Examples:
- "You're going to look absolutely stunning! ✨ TiTi is here for your style evolution. 👗"
- "Stay bold, stay stylish! Let's curate your perfect global wardrobe together. 💎🌍"
- "I can't wait to hear your thoughts on this look. Stay stylish! ✨"
"""


async def stream_stylist_response(
    user_message: str,
    wardrobe_context: str | None = None,
    history: list[dict] | None = None,
) -> AsyncGenerator[str, None]:
    """
    Stream stylist response from JagTara AI Proxy.
    Yields text chunks as they arrive.
    """
    messages = [{"role": "system", "content": HINGLISH_SYSTEM_PROMPT}]

    # Include conversation history if provided
    if history:
        messages.extend(history)

    if wardrobe_context:
        messages.append({
            "role": "user",
            "content": f"Here are the items in my wardrobe for context:\n{wardrobe_context}",
        })
        messages.append({
            "role": "assistant",
            "content": "Noted! I have your wardrobe context. How can I help you style today? 😊",
        })

    messages.append({"role": "user", "content": user_message})

    payload = {
        "model": "qwen3.5:latest",
        "messages": messages,
        "stream": True,
    }

    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            async with client.stream(
                "POST",
                settings.jagtara_chat_url,
                json=payload,
                headers={"X-API-KEY": settings.jagtara_api_key}
            ) as response:
                if response.status_code != 200:
                    log.error(f"JagTara AI Error: {response.status_code} - {await response.aread()}")
                    yield "Our AI Stylist is currently unavailable. Please try again in a moment. 🙏"
                    return

                import json
                async for line in response.aiter_lines():
                    if line.strip():
                        try:
                            data = json.loads(line)
                            chunk = data.get("message", {}).get("content", "")
                            if chunk:
                                yield chunk
                            if data.get("done"):
                                break
                        except json.JSONDecodeError:
                            continue

    except httpx.ConnectError:
        log.error(f"Cannot connect to Ollama at {settings.ollama_url}")
        yield "Stylist service se connection nahi ho paa raha. Admin se contact karo. 🙏"
    except Exception as e:
        log.error(f"Ollama error: {e}")
        yield "Kuch technical issue aa gaya. Thodi der mein dubara try karo. 🛠️"


async def get_stylist_response(
    user_message: str,
    wardrobe_context: str | None = None,
    history: list[dict] | None = None,
) -> str:
    """Non-streaming version — collects full response."""
    chunks = []
    async for chunk in stream_stylist_response(user_message, wardrobe_context, history):
        chunks.append(chunk)
    return "".join(chunks)
