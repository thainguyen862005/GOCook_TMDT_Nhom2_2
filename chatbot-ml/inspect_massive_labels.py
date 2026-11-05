from pathlib import Path
from collections import Counter, defaultdict
import ast
import pyarrow.parquet as pq
from huggingface_hub import hf_hub_download

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data" / "massive" / "vi-VN"

source_path = hf_hub_download(
    repo_id="AmazonScience/massive",
    filename="massive.py",
    repo_type="dataset",
    local_dir=str(BASE_DIR / "data" / "massive")
)

source = Path(source_path).read_text(encoding="utf-8")
tree = ast.parse(source)

def read_list(name):
    for node in tree.body:
        if not isinstance(node, ast.Assign):
            continue

        for target in node.targets:
            if isinstance(target, ast.Name) and target.id == name:
                return ast.literal_eval(node.value)

    raise ValueError(f"Missing label definition: {name}")


intents = read_list("_INTENTS")
scenarios = read_list("_SCENARIOS")

print("Intent labels:", len(intents))
print("Scenario labels:", len(scenarios))

files = sorted((DATA_DIR / "train").rglob("*.parquet"))

if not files:
    raise FileNotFoundError("MASSIVE train Parquet not found")

counts = Counter()
examples = defaultdict(list)

for file in files:
    table = pq.read_table(
        file,
        columns=["intent", "scenario", "utt"]
    )

    for row in table.to_pylist():
        intent_id = int(row["intent"])
        scenario_id = int(row["scenario"])

        intent_name = intents[intent_id]
        scenario_name = scenarios[scenario_id]

        counts[intent_name] += 1

        if len(examples[intent_name]) < 5:
            examples[intent_name].append(row["utt"])

print("\n=== CANDIDATE INTENTS FOR GO COOK ===")

candidates = [
    "general_greet",
    "takeaway_query",
    "takeaway_order",
    "cooking_query",
    "cooking_recipe",
    "recommendation_locations",
    "recommendation_events",
    "calendar_set"
]

for name in candidates:
    print(f"\n[{name}] - {counts[name]} sentences")

    for text in examples[name]:
        print(" -", text)

print("\n=== ALL INTENT LABELS ===")
for index, name in enumerate(intents):
    print(f"{index:02d}: {name} ({counts[name]})")

print("\nInspection completed!")
