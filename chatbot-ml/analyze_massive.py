from pathlib import Path
from collections import Counter, defaultdict
import pyarrow.parquet as pq

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data" / "massive" / "vi-VN"

def load_split(split):
    folder = DATA_DIR / split
    files = sorted(folder.rglob("*.parquet"))

    if not files:
        raise FileNotFoundError(f"No Parquet files in: {folder}")

    rows = []
    for file in files:
        table = pq.read_table(file)
        rows.extend(table.to_pylist())
    return rows

def main():
    print("=== MASSIVE DATASET ANALYSIS ===")

    train = load_split("train")
    validation = load_split("validation")
    test = load_split("test")

    print("\nDataset sizes: ")
    print("Train:", len(train))
    print("Validation:", len(validation))
    print("Test:", len(test))

    print("\nAvailable columns: ")
    print(list(train[0].keys()))

    intents = Counter(str(row["intent"]) for row in train)
    scenarios = Counter(str(row["scenario"]) for row in train)
    print("\nTotal intents: ", len(intents))
    print("Total scenarios: ", len(scenarios))

    print("\nScenarios: ")
    for name, count in scenarios.most_common():
        print(f"{name}: {count}")

    print("\nTop 20 intents: ")
    for name, count in intents.most_common(20):
        print(f"{name}: {count}")

    examples = defaultdict(list)
    for row in train:
        intent = str(row["intent"])
        if len(examples[intent]) < 2:
            examples[intent].append(row["utt"])

    print("\nExample utterances: ")
    for intent in sorted(examples)[:15]:
        print(f"\n[{intent}]")
        for text in examples[intent]:
            print(" -", text)

    print("\nAnalysis completed!")

if __name__ == "__main__":
    main()
