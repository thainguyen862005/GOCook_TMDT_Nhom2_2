from pathlib import Path
from collections import Counter
import csv
import joblib

from sklearn.pipeline import Pipeline
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_recall_fscore_support, classification_report, confusion_matrix

BASE = Path(__file__).resolve().parent

TRAIN_FILE = BASE / "data/training/intents_merged.csv"
TEST_FILE = BASE / "data/evaluation/independent_test.csv"

MODEL_DIR = BASE / "models"
REPORT_DIR = BASE / "reports"

MODEL_DIR.mkdir(exist_ok=True)
REPORT_DIR.mkdir(exist_ok=True)


def load_dataset(path):
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))

    texts = [row["text"].strip() for row in rows]
    labels = [row["intent"].strip() for row in rows]

    if not rows or any(not x for x in texts + labels):
        raise ValueError(f"Invalid dataset: {path}")

    return texts, labels


def main():
    print("=== GO COOK CHATBOT TRAINING ===")

    X_train, y_train = load_dataset(TRAIN_FILE)
    X_test, y_test = load_dataset(TEST_FILE)

    if set(y_train) != set(y_test):
        raise ValueError("Train/test intent labels differ")

    print("Training samples:", len(X_train))
    print("Test samples:", len(X_test))
    print("Intent classes:", len(set(y_train)))


    model = Pipeline([
        (
            "tfidf",
            TfidfVectorizer(
                analyzer="char",
                ngram_range=(2, 4),
                lowercase=True,
                max_features=6000,
                sublinear_tf=True,
            ),
        ),
        (
            "classifier",
            LogisticRegression(
                max_iter=2000,
                class_weight="balanced",
                random_state=42,
            ),
        ),
    ])

    print("\nTraining model...")
    model.fit(X_train, y_train)

    print("Training completed!")

    predictions = model.predict(X_test)

    accuracy = accuracy_score(y_test, predictions)

    precision, recall, f1, _ = (
        precision_recall_fscore_support(
            y_test,
            predictions,
            average="macro",
            zero_division=0,
        )
    )

    report = classification_report(
        y_test,
        predictions,
        zero_division=0,
    )

    print("\n=== EVALUATION ===")
    print(f"Accuracy:  {accuracy:.4f}")
    print(f"Precision: {precision:.4f}")
    print(f"Recall:    {recall:.4f}")
    print(f"Macro-F1:  {f1:.4f}")

    print("\nClassification report:")
    print(report)

    # Save the trained Python model.
    model_path = MODEL_DIR / "intent_classifier.joblib"
    joblib.dump(model, model_path)

    # Save evaluation metrics.
    metrics_path = REPORT_DIR / "metrics.txt"

    with metrics_path.open(
            "w", encoding="utf-8"
    ) as f:
        f.write("GO Cook Chatbot - Evaluation\n")
        f.write(f"Train samples: {len(X_train)}\n")
        f.write(f"Test samples: {len(X_test)}\n")
        f.write(f"Accuracy: {accuracy:.4f}\n")
        f.write(f"Macro Precision: {precision:.4f}\n")
        f.write(f"Macro Recall: {recall:.4f}\n")
        f.write(f"Macro F1: {f1:.4f}\n\n")
        f.write(report)

    # Save each prediction to inspect incorrect cases.
    predictions_path = REPORT_DIR / "predictions.csv"

    with predictions_path.open(
            "w", encoding="utf-8-sig", newline=""
    ) as f:
        writer = csv.writer(f)
        writer.writerow([
            "text", "actual_intent",
            "predicted_intent", "correct"
        ])

        for text, actual, predicted in zip(
                X_test, y_test, predictions
        ):
            writer.writerow([
                text,
                actual,
                predicted,
                actual == predicted,
                ])

    # Save confusion matrix.
    labels = sorted(set(y_train))

    matrix = confusion_matrix(
        y_test, predictions, labels=labels
    )

    matrix_path = REPORT_DIR / "confusion_matrix.csv"

    with matrix_path.open(
            "w", encoding="utf-8-sig", newline=""
    ) as f:
        writer = csv.writer(f)
        writer.writerow(["actual/predicted"] + labels)

        for label, row in zip(labels, matrix):
            writer.writerow([label] + list(row))

    print("\n=== OUTPUT FILES ===")
    print("Model:", model_path)
    print("Metrics:", metrics_path)
    print("Predictions:", predictions_path)
    print("Confusion matrix:", matrix_path)

    errors = sum(
        actual != predicted
        for actual, predicted in zip(y_test, predictions)
    )

    print("\nIncorrect predictions:", errors)
    print("Completed!")


if __name__ == "__main__":
    main()
