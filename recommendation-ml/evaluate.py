import argparse
import csv
import json
from collections import Counter
from math import log2
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd
from feature_encoder import DEFAULT_CONFIG, dot, encode
from train import read_menus

METRICS = ("precision_at_5", "recall_at_5", "hitrate_at_5", "ndcg_at_5")


def ranking(vectors: dict, purchased: list[str], candidates: set[str], k: int) -> list[str]:
    scores = {}
    for seed in purchased[-80:]:
        if seed not in vectors:
            continue
        nearby = sorted(((mid, dot(vectors[seed], vec)) for mid, vec in vectors.items() if mid != seed),
                        key=lambda pair: (-pair[1], pair[0]))[:k]
        for mid, similarity in nearby:
            if mid in candidates and mid not in purchased and similarity > 1e-9:
                scores[mid] = max(scores.get(mid, 0), similarity)
    return sorted(scores, key=lambda mid: (-scores[mid], mid))[:5]


def save_chart(rows: list[dict], users: int, out: Path):
    fig, ax = plt.subplots(figsize=(9.5, 4.5) if users else (9, 3))
    if users:
        for n, row in enumerate(rows):
            ax.bar([i + (n - .5) * .36 for i in range(4)],
                   [row[key] for key in METRICS], width=.36, label=row["model"])
        ax.set_xticks(range(4), ["Precision@5", "Recall@5", "HitRate@5", "NDCG@5"])
        ax.set_ylim(0, 1)
        ax.set_ylabel("Macro average")
        ax.legend()
        ax.set_title(f"Offline KNN vs popularity | {users} eligible customers")
    else:
        ax.axis("off")
        ax.text(.5, .5, "INSUFFICIENT DATA\nNo customers have two distinct verified menu purchases",
                ha="center", va="center", fontsize=13, transform=ax.transAxes)
    fig.tight_layout()
    fig.savefig(out / "evaluation.png", dpi=160)
    plt.close(fig)


def evaluate(menus_path: Path, interactions_path: Path, out: Path, k: int):
    menus = read_menus(menus_path)
    ids = {str(menu["id"]) for menu in menus}
    vectors = {str(menu["id"]): encode(menu, DEFAULT_CONFIG) for menu in menus}
    frame = pd.read_csv(interactions_path, dtype=str, keep_default_na=False)
    required = {"user_id", "menu_id", "event_time"}
    if not required.issubset(frame.columns):
        raise ValueError(f"Interactions CSV must have columns {sorted(required)}")
    frame["parsed_time"] = pd.to_datetime(frame["event_time"], errors="coerce", utc=True)
    frame = frame.dropna(subset=["parsed_time"]).sort_values(["user_id", "parsed_time", "menu_id"])
    histories, targets = {}, {}
    for user, group in frame.groupby("user_id", sort=False):
        distinct = list(dict.fromkeys(mid for mid in group["menu_id"] if mid in ids))
        if len(distinct) >= 2:
            histories[str(user)], targets[str(user)] = distinct[:-1], distinct[-1]

    counts = Counter(mid for history in histories.values() for mid in history)
    popular = sorted(counts, key=lambda mid: (-counts[mid], mid))
    samples = {"Item-KNN": [], "Popularity": []}
    for user, target in targets.items():
        history = histories[user]
        eligible = ids - set(history)
        recommendations = (ranking(vectors, history, eligible, k),
                           [mid for mid in popular if mid in eligible][:5])
        for name, top5 in zip(samples, recommendations):
            hit = int(target in top5)
            rank = top5.index(target) + 1 if hit else 0
            samples[name].append((hit / 5, hit, hit, 1 / log2(rank + 1) if hit else 0.0))

    users = len(targets)
    rows = [{"model": name, **{key: (sum(s[i] for s in values) / len(values) if values else None)
                               for i, key in enumerate(METRICS)}, "eligible_users": users}
            for name, values in samples.items()]
    status = "OK_SYNTHETIC_OR_REAL_UNVERIFIED" if users else "INSUFFICIENT_DATA"
    summary = {"status": status, "catalog_size": len(menus), "interaction_rows": len(frame),
               "eligible_users": users, "k": k,
               "caution": "One held-out NEW menu/user; synthetic fixtures are NOT real-world accuracy."}
    out.mkdir(parents=True, exist_ok=True)
    with (out / "metrics.csv").open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=["model", *METRICS, "eligible_users"])
        writer.writeheader()
        writer.writerows(rows)
    (out / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    save_chart(rows, users, out)
    report = ["# GO Cook — KNN model evaluation", "", f"Status: **{status}**", "",
              f"Catalog: {len(menus)} menus | Interactions: {len(frame)} | Eligible users: {users}", "",
              "Holdout: last distinct menu per user; earlier distinct menus are the profile.",
              "All scores are macro-averages. Popularity is trained only on earlier events.", "",
              "| Model | Precision@5 | Recall@5 | HitRate@5 | NDCG@5 |", "|---|---:|---:|---:|---:|"]
    for row in rows:
        report.append(f"| {row['model']} | " + " | ".join(
            "N/A" if row[key] is None else f"{row[key]:.3f}" for key in METRICS) + " |")
    report += ["", "**Limitation:** This is an offline historical-item test, not a calibrated likelihood of customer preference.",
               "Synthetic samples must be labeled DEMO, never presented as production evaluation."]
    (out / "evaluation.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    print(f"{status}; eligible_users={users}; saved {out}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--menus", required=True, type=Path)
    parser.add_argument("--interactions", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--k", type=int, default=5)
    args = parser.parse_args()
    if not 1 <= args.k <= 30:
        parser.error("--k between 1 and 30")
    evaluate(args.menus, args.interactions, args.out, args.k)
