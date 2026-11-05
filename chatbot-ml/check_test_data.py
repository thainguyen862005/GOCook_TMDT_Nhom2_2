from pathlib import Path
from collections import Counter
import csv
import re
import unicodedata

BASE = Path(__file__).resolve().parent
TRAIN = BASE / "data/training/intents_merged.csv"
TEST = BASE / "data/evaluation/independent_test.csv"

def load_csv(path):
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))
    return None


def normalize(text):
    text = unicodedata.normalize("NFC", text)
    text = text.casefold().strip()
    text = re.sub(r"[^\w\s]", " ", text)
    return " ".join(text.split())


train = load_csv(TRAIN)
test = load_csv(TEST)

train_texts = {normalize(row["text"]) for row in train}

seen = set()
duplicates = []
overlaps = []

for row in test:
    text = normalize(row["text"])

    if text in seen:
        duplicates.append(row["text"])

    if text in train_texts:
        overlaps.append(row["text"])

    seen.add(text)

train_labels = {row["intent"] for row in train}
test_labels = {row["intent"] for row in test}

print("=== TEST DATA CHECK ===")
print("Training rows:", len(train))
print("Test rows:", len(test))
print("Test intents:", len(test_labels))
print("Missing intents:", train_labels - test_labels)
print("Unknown intents:", test_labels - train_labels)
print("Duplicate test sentences:", len(duplicates))
print("Overlapping train/test sentences:", len(overlaps))

print("\nTest distribution:")
for intent, count in sorted(Counter(row["intent"] for row in test).items()):
    print(f"{intent}: {count}")

if (
        len(test) != 44
        or train_labels != test_labels
        or duplicates
        or overlaps
):
    raise SystemExit("TEST DATA INVALID")

print("\nTEST DATA VALID!")
