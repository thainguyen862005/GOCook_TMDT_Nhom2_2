
from pathlib import Path
import json
import joblib

BASE = Path(__file__).resolve().parent
MODEL_FILE = BASE / "models" / "intent_classifier.joblib"
OUTPUT_FILE = BASE.parent / "src" / "main" / "resources" / "chatbot" / "intent-model.json"

def main():
    if not MODEL_FILE.exists():
        raise FileNotFoundError(f"Model not found: {MODEL_FILE}")

    model = joblib.load(MODEL_FILE)

    vectorizer = model.named_steps["tfidf"]
    classifier = model.named_steps["classifier"]

    features = vectorizer.get_feature_names_out().tolist()
    classes = classifier.classes_.tolist()

    weights = classifier.coef_.tolist()
    bias = classifier.intercept_.tolist()

    if len(features) != len(vectorizer.idf_):
        raise ValueError("Feature/IDF size mismatch")

    if len(classes) != len(weights):
        raise ValueError("Class/weight size mismatch")

    data = {
        "model_type": "tfidf_logistic_regression",
        "version": 1,
        "classes": classes,
        "vectorizer": {
            "analyzer": "char",
            "ngram_min": vectorizer.ngram_range[0],
            "ngram_max": vectorizer.ngram_range[1],
            "lowercase": vectorizer.lowercase,
            "sublinear_tf": vectorizer.sublinear_tf,
            "norm": vectorizer.norm,
            "features": features,
            "idf": vectorizer.idf_.tolist(),
        },
        "classifier": {
            "weights": weights,
            "bias": bias,
        },
    }

    OUTPUT_FILE.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with OUTPUT_FILE.open(
            "w", encoding="utf-8"
    ) as file:
        json.dump(
            data,
            file,
            ensure_ascii=False,
            separators=(",", ":"),
        )

    print("=== MODEL EXPORT ===")
    print("Output:", OUTPUT_FILE)
    print("Intent classes:", len(classes))
    print("Features:", len(features))
    print("Weight rows:", len(weights))
    print("Model exported successfully!")


if __name__ == "__main__":
    main()
