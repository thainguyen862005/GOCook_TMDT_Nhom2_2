from pathlib import Path
from collections import defaultdict
import ast
import csv
import random
import pyarrow.parquet as pq

BASE_DIR = Path(__file__).resolve().parent
MASSIVE_DIR = BASE_DIR / "data" / "massive"
TRAIN_DIR = MASSIVE_DIR / "vi-VN" / "train"
OUTPUT = BASE_DIR / "data" / "massive_review.csv"

CANDIDATES = {
    "general_greet": 25,
    "takeaway_query": 30,
    "takeaway_order": 30,
    "recommendation_locations": 30,
    "cooking_query": 4,
    "cooking_recipe": 20,
}

OUT_OF_SCOPE = {
    "qa_maths": 6,
    "play_music": 6,
    "weather_query": 6,
    "iot_hue_lightoff": 6,
    "transport_ticket": 6,
}

ALLOWED_INTENTS = [
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
]


def load_intent_names():
    source = (MASSIVE_DIR / "massive.py").read_text(encoding="utf-8")
    tree = ast.parse(source)

    for node in tree.body:
        if not isinstance(node, ast.Assign):
            continue

        for target in node.targets:
            if isinstance(target, ast.Name) and target.id == "_INTENTS":
                return ast.literal_eval(node.value)

    raise RuntimeError("Cannot read MASSIVE intents")


def main():
    intent_names = load_intent_names()
    grouped = defaultdict(list)

    files = sorted(TRAIN_DIR.rglob("*.parquet"))
    if not files:
        raise FileNotFoundError("MASSIVE train not found")

    for file in files:
        table = pq.read_table(file, columns=["id", "intent", "utt"])

        for row in table.to_pylist():
            name = intent_names[int(row["intent"])]

            if name not in CANDIDATES and name not in OUT_OF_SCOPE:
                continue

            text = str(row["utt"]).strip()
            if text:
                grouped[name].append({
                    "id": str(row["id"]),
                    "text": text,
                })

    rng = random.Random(42)
    output_rows = []

    groups = {**CANDIDATES, **OUT_OF_SCOPE}

    for name, limit in groups.items():
        items = grouped[name]

        unique = {}
        for item in items:
            key = " ".join(item["text"].lower().split())
            unique.setdefault(key, item)

        items = list(unique.values())
        rng.shuffle(items)

        for item in items[:limit]:
            suggestion = ""

            if name == "general_greet":
                suggestion = "GREETING"
            elif name in OUT_OF_SCOPE:
                suggestion = "OUT_OF_SCOPE"

            output_rows.append({
                "source_id": item["id"],
                "source_intent": name,
                "text": item["text"],
                "suggested_intent": suggestion,
                "go_cook_intent": "",
                "decision": "",
            })

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    with OUTPUT.open(
            "w",
            newline="",
            encoding="utf-8-sig"
    ) as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "source_id",
                "source_intent",
                "text",
                "suggested_intent",
                "go_cook_intent",
                "decision",
            ],
        )

        writer.writeheader()
        writer.writerows(output_rows)

    print("Output:", OUTPUT)
    print("Total review candidates:", len(output_rows))
    print("Allowed GO Cook intents:")
    print(", ".join(ALLOWED_INTENTS))
    print("Completed!")


if __name__ == "__main__":
    main()
