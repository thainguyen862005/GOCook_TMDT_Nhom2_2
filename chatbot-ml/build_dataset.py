from pathlib import Path
from collections import Counter, defaultdict
import csv
import re
import unicodedata

BASE = Path(__file__).resolve().parent
DATA = BASE / "data"
OUTPUT_DIR = DATA / "training"

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

OUTPUT = OUTPUT_DIR / "intents_merged.csv"
CONFLICTS = OUTPUT_DIR / "conflicts.csv"

ALLOWED_INTENTS = {
    "GREETING",
    "SEARCH_MENU",
    "SEARCH_CHEF",
    "RECOMMEND_MENU",
    "CART_HELP",
    "BOOKING_HELP",
    "PAYMENT_HELP",
    "ORDER_HELP",
    "FAQ",
    "CONTACT_ADMIN",
    "OUT_OF_SCOPE",
}

def read_csv(filename):
    path = DATA / filename

    with path.open("r", encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))
    return None


def normalize(text):
    text = unicodedata.normalize("NFC", text)
    text = text.casefold().strip()
    text = re.sub(r"[^\w\s]", " ", text)
    text = " ".join(text.split())

    return text


def add_rows(output, rows, source):
    for row in rows:
        text = (row.get("text") or "").strip()
        intent = (row.get("intent") or "").strip()

        if not text:
            raise ValueError(f"Empty text in {source}")

        if intent not in ALLOWED_INTENTS:
            raise ValueError(
                f"Invalid intent '{intent}' in {source}"
            )

        output.append({
            "text": text,
            "intent": intent,
            "source": source,
        })


def main():
    records = []

    add_rows(records, read_csv("intents.csv"),"gocook_original")

    massive = read_csv("massive_review_labeled.csv")
    massive_rows = []

    for row in massive:
        decision = (row.get("decision") or "").strip()
        decision = decision.upper()

        if decision not in {"KEEP", "SKIP"}:
            raise ValueError(
                f"Invalid MASSIVE decision: {decision}"
            )

        if decision == "KEEP":
            massive_rows.append({
                "text": row.get("text"),
                "intent": row.get("go_cook_intent"),
            })

    add_rows(records, massive_rows,"massive_auto_labeled")
    add_rows(records, read_csv("generated_gocook.csv"),"gocook_template")

    unique = {}
    conflicts = defaultdict(list)
    duplicates = 0

    for row in records:
        key = normalize(row["text"])

        if key in conflicts:
            conflicts[key].append(row)
            continue

        if key not in unique:
            unique[key] = row
            continue

        old = unique[key]

        if old["intent"] == row["intent"]:
            duplicates += 1
        else:
            conflicts[key].extend([
                unique.pop(key),
                row,
            ])

    with OUTPUT.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=["text", "intent", "source"])
        writer.writeheader()
        writer.writerows(unique.values())

    with CONFLICTS.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=["text", "intent", "source"])
        writer.writeheader()

        for items in conflicts.values():
            writer.writerows(items)

    print("=== GO COOK DATASET ===")
    print("Raw rows:", len(records))
    print("Duplicates removed:", duplicates)
    print("Conflicting texts:", len(conflicts))
    print("Ready rows:", len(unique))

    print("\nIntent distribution:")
    counts = Counter(row["intent"] for row in unique.values())

    for intent, count in sorted(counts.items()):
        print(f"{intent}: {count}")

    print("\nOutput:", OUTPUT)
    print("Conflicts:", CONFLICTS)
    print("Completed!")


if __name__ == "__main__":
    main()
