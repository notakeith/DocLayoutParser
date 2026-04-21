from fastapi import FastAPI, UploadFile, File
from paddleocr import PaddleOCR
import numpy as np
import cv2
import uvicorn

from datetime import datetime

app = FastAPI()

ocr = PaddleOCR(
    lang='ru',
    paddlex_config="PaddleOCR.yaml"
)

@app.post("/recognize")
async def recognize(file: UploadFile = File(...)):
    contents = await file.read()
    nparr = np.frombuffer(contents, np.uint8)
    image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    cv2.imwrite(f"photos/{datetime.now().timestamp()}.jpg", image)

    result = ocr.predict(image)

    print(result)

    full_text = []
    for res in result:
        texts = res["rec_texts"]
        scores = res["rec_scores"]

        filtered = [t for t, s in zip(texts, scores) if s > 0.4]
        print(filtered)
        full_text.append(" ".join(filtered))
    return {"text": " ".join(full_text)}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)