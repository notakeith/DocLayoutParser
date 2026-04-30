from fastapi import FastAPI, UploadFile, File
from PIL import Image
import uvicorn
import io
import re
import os
from datetime import datetime

from surya.foundation import FoundationPredictor
from surya.recognition import RecognitionPredictor
from surya.detection import DetectionPredictor

app = FastAPI()

print("🔄 Загрузка моделей Surya OCR...")
foundation_predictor = FoundationPredictor()
recognition_predictor = RecognitionPredictor(foundation_predictor)
detection_predictor = DetectionPredictor()
print("✅ Модели Surya загружены")

# ---------------------------------------------------------------------------
# Лингвистическая постобработка
# ---------------------------------------------------------------------------

# Таблица замены латиницы → кириллица (только однозначные визуальные совпадения)
# Ключи — латинские символы, значения — кириллические аналоги
LATIN_TO_CYR = {
    'A': 'А', 'B': 'В', 'C': 'С', 'E': 'Е', 'H': 'Н',
    'K': 'К', 'M': 'М', 'O': 'О', 'P': 'Р', 'T': 'Т',
    'X': 'Х', 'Y': 'У',
    'a': 'а', 'c': 'с', 'e': 'е', 'o': 'о', 'p': 'р',
    'x': 'х', 'y': 'у',
}

# Символы кириллицы — для определения "кириллического контекста" слова
_CYR_RE = re.compile(r'[а-яёА-ЯЁ]')
_LAT_CONFUSABLE_RE = re.compile(r'[ABCEHKMOPTXYaceopxy]')


def fix_latin_in_cyrillic_context(text: str) -> str:
    """
    Заменяет латинские буквы на кириллические аналоги внутри слов,
    которые содержат хотя бы одну кириллическую букву.

    Логика: если слово смешанное (есть хоть одна кириллическая буква),
    то латинские символы из таблицы — почти гарантированно ошибка OCR.

    Слова из чисто латинских букв (аббревиатуры, единицы измерения) — не трогаем.
    """
    def fix_word(word: str) -> str:
        if not _CYR_RE.search(word):
            return word
        return ''.join(LATIN_TO_CYR.get(ch, ch) for ch in word)

    parts = re.split(r'(\s+)', text)
    return ''.join(fix_word(part) for part in parts)


def merge_hyphenated_line_breaks(text: str) -> str:
    """
    Склеивает переносы слов через дефис на конце строки.

    Примеры:
      "уз-\nлам"     → "узлам"
      "огне-\nвой"   → "огневой"
      "ч.-1.30"      → без изменений (дефис не перенос)

    Паттерн: слово заканчивается дефисом, следующая строка начинается со строчной.
    """
    text = re.sub(
        r'([а-яёА-ЯЁa-zA-Z])-\s*\n\s*([а-яёА-ЯЁa-zA-Z])',
        r'\1\2',
        text
    )
    return text


def clean_ocr_artifacts(text: str) -> str:
    """
    Убираем типичные артефакты OCR:
    - Множественные пробелы → один
    - Пробелы перед знаками препинания
    - Лишние переводы строк (3+ → 2)
    """
    # Множественные пробелы (не трогаем переводы строк)
    text = re.sub(r'[ \t]{2,}', ' ', text)
    # Пробел перед знаком препинания
    text = re.sub(r' ([.,;:!?])', r'\1', text)
    # Более 2 переводов строк подряд → 2
    text = re.sub(r'\n{3,}', '\n\n', text)
    # Пробелы в начале/конце строк
    text = '\n'.join(line.strip() for line in text.split('\n'))
    return text.strip()


def postprocess_text(text: str) -> str:
    """
    Полный пайплайн постобработки (без LLM).
    Порядок важен: сначала переносы, потом латиница, потом артефакты.
    """
    text = merge_hyphenated_line_breaks(text)
    text = fix_latin_in_cyrillic_context(text)
    text = clean_ocr_artifacts(text)
    return text


# ---------------------------------------------------------------------------
# LLM-коррекция (опциональная, через MLX на M1)
# ---------------------------------------------------------------------------


import httpx

LLM_URL = os.environ.get("LLM_URL", "http://127.0.0.1:5001")
LLM_ENABLED = 1

async def correct_with_llm(tagged_text: str) -> str | None:
    if not LLM_ENABLED:
        return None
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(
                f"{LLM_URL}/correct",
                json={"tagged_text": tagged_text, "max_tokens": 512}
            )
            resp.raise_for_status()
            data = resp.json()
            return None if data["skipped"] else data["corrected"]
    except Exception as e:
        print(f"⚠️  LLM недоступна: {e}")
        return None


# ---------------------------------------------------------------------------
# Форматирование результатов Surya
# ---------------------------------------------------------------------------

def format_word_confidence_tags(predictions, low_threshold=0.5):
    result_lines = []

    for prediction in predictions:
        for line in prediction.text_lines:
            if hasattr(line, 'words') and line.words:
                tagged_words = []
                for word in line.words:
                    if word.confidence < low_threshold:
                        percent = round(word.confidence * 100)
                        tagged_words.append(f"<low confidence=\"{percent}\">{word.text}</low>")
                    else:
                        tagged_words.append(word.text)
                result_lines.append(' '.join(tagged_words))
            else:
                if line.confidence < low_threshold:
                    percent = round(line.confidence * 100)
                    result_lines.append(f"<low confidence=\"{percent}\">{line.text}</low>")
                else:
                    result_lines.append(line.text)

    return '\n'.join(result_lines)


def get_plain_text(predictions):
    result_lines = []
    for prediction in predictions:
        for line in prediction.text_lines:
            result_lines.append(line.text)
    return '\n'.join(result_lines)


def strip_confidence_tags(tagged_text: str) -> str:
    """Убирает <low ...> теги, оставляет текст внутри."""
    return re.sub(r'<low[^>]*>(.*?)</low>', r'\1', tagged_text)


# ---------------------------------------------------------------------------
# FastAPI endpoints
# ---------------------------------------------------------------------------


@app.post("/recognize")
async def recognize(file: UploadFile = File(...)):
    contents = await file.read()
    timestamp = datetime.now().timestamp()

    image = Image.open(io.BytesIO(contents)).convert("RGB")
    predictions = recognition_predictor([image], det_predictor=detection_predictor)

    total_lines = len(predictions[0].text_lines)
    total_words = 0
    low_words = 0
    for line in predictions[0].text_lines:
        if hasattr(line, 'words') and line.words:
            for word in line.words:
                total_words += 1
                if word.confidence < 0.5:
                    low_words += 1

    tagged_text = format_word_confidence_tags(predictions, low_threshold=0.5)
    plain_text = get_plain_text(predictions)

    plain_postprocessed = postprocess_text(plain_text)

    llm_corrected = None
    llm_used = False

    if LLM_ENABLED:
        tagged_postprocessed = postprocess_text(tagged_text)
        llm_result = await correct_with_llm(tagged_postprocessed)
        if llm_result:
            llm_corrected = clean_ocr_artifacts(llm_result)
            llm_used = True

    print(
        f"[{timestamp:.3f}] "
        f"Строк: {total_lines}, Слов: {total_words}, "
        f"Неуверенных: {low_words}, LLM: {'да' if llm_used else 'нет'}"
    )

    return {
        "text": llm_corrected if llm_corrected else plain_postprocessed,

        "variants": {
            "raw": plain_text,
            "postprocessed": plain_postprocessed,
            "tagged": tagged_text,
            "llm": llm_corrected,
        },

        "stats": {
            "lines": total_lines,
            "words": total_words,
            "low_confidence_words": low_words,
            "llm_used": llm_used,
        }
    }


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "engine": "surya-ocr",
        "llm_enabled": LLM_ENABLED,
    }


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)