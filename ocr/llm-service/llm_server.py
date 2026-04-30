import re
import os
from fastapi import FastAPI
from pydantic import BaseModel
import uvicorn

app = FastAPI()

# MODEL_PATH = os.environ.get("LLM_MODEL", "mlx-community/Qwen2.5-3B-Instruct-4bit")
MODEL_PATH = os.environ.get("LLM_MODEL", "mlx-community/Qwen2.5-7B-Instruct-4bit")

print(f"🔄 Загрузка LLM: {MODEL_PATH}")
from mlx_lm import load, generate
model, tokenizer = load(MODEL_PATH)
print("✅ LLM загружена")

# ---------------------------------------------------------------------------
# Промпт
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = """Ты корректор OCR-текста. Тебе дан текст из военного документа 1940-х годов, распознанный автоматически.

OCR часто делает такие ошибки:
- пропускает буквы: "мнометным" → "минометным", "пртиллерийским" → "артиллерийским"
- переставляет буквы: "нелет" → "налет", "Четире" → "Четыре"
- заменяет буквы: "атареям" → "батареям", "габлицы" → "таблицы", "рв.мин" → "гв.мин"
- склеивает слова без пробела: "всютактическую" → "всю тактическую"
- слова в тегах <low confidence="N"> распознаны особенно плохо

Исправь ВСЕ OCR-ошибки опираясь на военный контекст документа.
Сокращения типа "пр-ка", "ч.", "РС", "зали" — оставь как есть (это термины эпохи).
Верни ТОЛЬКО исправленный текст. Никаких пояснений."""

# ---------------------------------------------------------------------------
# Защита от prompt injection
# ---------------------------------------------------------------------------

_INJECTION_PATTERNS = [
    r'игнорируй\s+(все\s+)?предыдущие\s+инструкции',
    r'ignore\s+(all\s+)?previous\s+instructions',
    r'forget\s+(all\s+)?instructions',
    r'забудь\s+(все\s+)?инструкции',
    r'ты\s+теперь\s+',
    r'you\s+are\s+now\s+',
    r'act\s+as\s+',
    r'jailbreak',
    r'<\s*system\s*>',
    r'\[INST\]',
    r'<<SYS>>',
]
_INJECTION_RE = re.compile('|'.join(_INJECTION_PATTERNS), re.IGNORECASE)


def check_injection(text: str) -> bool:
    """True если обнаружена попытка инъекции."""
    return bool(_INJECTION_RE.search(text))


def validate_llm_response(original: str, response: str) -> tuple[bool, str]:
    """
    Проверяем что LLM вернула адекватный результат.
    Возвращает (is_valid, reason).
    """
    orig_len = len(original.strip())
    resp_len = len(response.strip())

    if resp_len == 0:
        return False, "пустой ответ"

    if orig_len > 50 and resp_len < orig_len * 0.4:
        return False, f"ответ слишком короткий ({resp_len} vs {orig_len})"

    if resp_len > orig_len * 3:
        return False, f"ответ слишком длинный ({resp_len} vs {orig_len})"

    suspicious = ['<system>', '[INST]', '<<SYS>>', 'assistant:', 'human:']
    for marker in suspicious:
        if marker.lower() in response.lower():
            return False, f"подозрительный маркер: {marker}"

    return True, "ok"


def strip_tags(text: str) -> str:
    return re.sub(r'<low[^>]*>(.*?)</low>', r'\1', text, flags=re.DOTALL)


def wrap_for_llm(tagged_text: str) -> str:
    """
    Оборачиваем в маркеры — модель видит содержимое как данные, а не команды.
    Основная защита от prompt injection через содержимое документа.
    """
    return f"[НАЧАЛО OCR-ТЕКСТА]\n{tagged_text}\n[КОНЕЦ OCR-ТЕКСТА]"


def strip_all_ocr_tags(text: str) -> str:
    """
    Убирает ВСЕ OCR-теги из текста перед отправкой в LLM:
    - корректные: <low confidence="43">слово</low>
    - битые закрывающие без открывающих: </low>, </lоw> (с кириллической о)
    - любые остаточные варианты

    Модель должна получать чистый текст — теги её путают и она их копирует в ответ.
    """
    # Полные теги с содержимым
    text = re.sub(r'<low[^>]*>(.*?)</l[оo]w>', r'\1', text, flags=re.DOTALL | re.IGNORECASE)
    # Открывающие теги без закрывающих
    text = re.sub(r'<low[^>]*>', '', text, flags=re.IGNORECASE)
    # Закрывающие теги без открывающих (включая кириллическую "о" в </lоw>)
    text = re.sub(r'</l[оo]w>', '', text, flags=re.IGNORECASE)
    return text

# ---------------------------------------------------------------------------
# API
# ---------------------------------------------------------------------------

class CorrectRequest(BaseModel):
    tagged_text: str
    max_tokens: int = 1024
    document_hint: str = ""
    field_hint: str = ""


class CorrectResponse(BaseModel):
    corrected: str
    skipped: bool = False
    injection_blocked: bool = False
    validation_failed: bool = False


@app.post("/correct", response_model=CorrectResponse)
async def correct(req: CorrectRequest):
    if check_injection(req.tagged_text):
        print("⚠️  Обнаружена попытка prompt injection — возвращаем сырой текст")
        return CorrectResponse(
            corrected=strip_tags(req.tagged_text),
            injection_blocked=True,
        )

    low_count = req.tagged_text.count('<low')
    if low_count == 0 and len(req.tagged_text) < 80:
        return CorrectResponse(corrected=strip_tags(req.tagged_text), skipped=True)

    system = SYSTEM_PROMPT
    if req.document_hint or req.field_hint:
        lines = []
        if req.document_hint:
            lines.append(f"Тип документа: {req.document_hint}")
        if req.field_hint:
            lines.append(f"Поле документа: {req.field_hint}")
        system += "\n\n" + "\n".join(lines)

    wrapped = wrap_for_llm(strip_all_ocr_tags(req.tagged_text))

    messages = [
        {"role": "system", "content": system},
        {"role": "user", "content": wrapped},
    ]

    prompt = tokenizer.apply_chat_template(
        messages, tokenize=False, add_generation_prompt=True
    )

    result = generate(
        model, tokenizer,
        prompt=prompt,
        max_tokens=req.max_tokens,
        verbose=False,
    )
    result = result.strip()

    is_valid, reason = validate_llm_response(req.tagged_text, result)
    if not is_valid:
        print(f"⚠️  LLM ответ отклонён: {reason}")
        return CorrectResponse(
            corrected=strip_tags(req.tagged_text),
            validation_failed=True,
        )

    return CorrectResponse(corrected=result)


@app.get("/health")
async def health():
    return {"status": "ok", "model": MODEL_PATH}


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=5001)