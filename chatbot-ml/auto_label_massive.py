from pathlib import Path
from collections import Counter
import csv

BASE_DIR = Path(__file__).resolve().parent
INPUT = BASE_DIR / "data" / "massive_review.csv"
OUTPUT = BASE_DIR / "data" / "massive_review_labeled.csv"

AUTO_LABELS = {
    "general_greet": "GREETING",
    "qa_maths": "OUT_OF_SCOPE",
    "play_music": "OUT_OF_SCOPE",
    "weather_query": "OUT_OF_SCOPE",
    "iot_hue_lightoff": "OUT_OF_SCOPE",
    "transport_ticket": "OUT_OF_SCOPE",
}

with INPUT.open("r", encoding="utf-8-sig", newline="") as f:
    reader = csv.DictReader(f)
    rows = list(reader)
    fields = reader.fieldnames

for row in rows:
    source = row["source_intent"].strip()
    label = AUTO_LABELS.get(source)

    if label:
        row["go_cook_intent"] = label
        row["decision"] = "KEEP"
    else:
        row["go_cook_intent"] = ""
        row["decision"] = "SKIP"

with OUTPUT.open("w", encoding="utf-8-sig", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=fields)
    writer.writeheader()
    writer.writerows(rows)

print("Output:", OUTPUT)
print("Total:", len(rows))
print("Decisions:", dict(Counter(row["decision"] for row in rows)))
print("Labels:", dict(Counter(
    row["go_cook_intent"]
    for row in rows
    if row["decision"] == "KEEP"
)))
print("Completed!")
