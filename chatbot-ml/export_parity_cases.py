
from pathlib import Path
import csv
import json
import joblib

BASE = Path(__file__).resolve().parent

MODEL = BASE / "models" / "intent_classifier.joblib"
TEST = BASE / "data" / "evaluation" / "independent_test.csv"

OUTPUT = (
        BASE.parent / "src" / "test" / "resources"
        / "chatbot" / "parity_cases.json"
)

model = joblib.load(MODEL)

with TEST.open("r", encoding="utf-8-sig", newline="") as f:
    rows = list(csv.DictReader(f))

texts = [row["text"] for row in rows]

# Add some practical GO Cook questions.
texts.extend([
    "Xin chào",
    "Tìm đầu bếp ở Gia Lai",
    "Thanh toán bằng VNPay như thế nào",
    "Gợi ý món ăn phù hợp với tôi",
])

# These are Python reference predictions, not ground-truth labels.
probabilities = model.predict_proba(texts)
classes = model.named_steps["classifier"].classes_

vectorizer = model.named_steps["tfidf"]

cases = []

for text, probs in zip(texts, probabilities):
    # The Java implementation deliberately returns UNKNOWN
    # when no feature matches the model vocabulary.
    if vectorizer.transform([text]).nnz == 0:
        raise ValueError(f"No known features: {text}")

    ranking = sorted(
        range(len(probs)),
        key=lambda i: probs[i],
        reverse=True,
    )

    first, second = ranking[:2]

    cases.append({
        "text": text,
        "intent": str(classes[first]),
        "confidence": float(probs[first]),
        "margin": float(probs[first] - probs[second]),
    })

OUTPUT.parent.mkdir(parents=True, exist_ok=True)

with OUTPUT.open("w", encoding="utf-8") as f:
    json.dump(
        cases, f,
        ensure_ascii=False,
        indent=2,
    )

print("=== PYTHON REFERENCE ===")
print("Total cases:", len(cases))
print("Output:", OUTPUT)

for item in cases[-4:]:
    print(
        item["intent"],
        f"{item['confidence']:.4f}",
        item["text"],
    )
