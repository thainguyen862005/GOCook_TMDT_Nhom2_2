import argparse
import csv
import json
from pathlib import Path

import pandas as pd
from scipy.sparse import lil_matrix
from sklearn.neighbors import NearestNeighbors
from feature_encoder import DEFAULT_CONFIG, encode


def read_menus(path: Path) -> list[dict]:
    data = pd.read_csv(path, dtype=str, keep_default_na=False)
    required = {"id", "name", "category", "dishItems", "description"}
    missing = sorted(required - set(data.columns))
    if missing:
        raise ValueError(f"Missing CSV columns: {missing}. Expected {sorted(required)}")
    if data["id"].duplicated().any():
        raise ValueError("Duplicate menu IDs in training CSV")
    return data.to_dict("records")


def to_sparse(menus: list[dict], config: dict):
    matrix = lil_matrix((len(menus), config["hashDimension"]))
    for row, menu in enumerate(menus):
        for col, value in encode(menu, config).items():
            matrix[row, col] = value
    return matrix.tocsr()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--menus", required=True, type=Path)
    parser.add_argument("--config-out", type=Path,
                        default=Path("../src/main/resources/recommendation/knn-config.json"))
    parser.add_argument("--preview-out", type=Path, default=Path("reports/neighbors_preview.csv"))
    parser.add_argument("--k", type=int, default=5)
    args = parser.parse_args()
    if not 1 <= args.k <= 30:
        parser.error("--k must be between 1 and 30")

    menus = read_menus(args.menus)
    if not menus:
        raise ValueError("No menus in CSV")
    config = {**DEFAULT_CONFIG, "defaultK": args.k}
    args.config_out.parent.mkdir(parents=True, exist_ok=True)
    args.config_out.write_text(json.dumps(config, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    args.preview_out.parent.mkdir(parents=True, exist_ok=True)
    with args.preview_out.open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.writer(file)
        writer.writerow(["source_id", "source_name", "neighbor_id", "neighbor_name", "similarity"])
        if len(menus) >= 2:
            matrix = to_sparse(menus, config)
            model = NearestNeighbors(metric="cosine", algorithm="brute",
                                     n_neighbors=min(args.k + 1, len(menus)))
            distances, neighbors = model.fit(matrix).kneighbors(matrix)
            for i, (dists, indexes) in enumerate(zip(distances, neighbors)):
                for distance, j in zip(dists, indexes):
                    if i != j and distance < 1:
                        writer.writerow([menus[i]["id"], menus[i]["name"],
                                         menus[j]["id"], menus[j]["name"], round(max(0, 1 - float(distance)), 6)])
    print(f"OK: {len(menus)} menus; config={args.config_out}; preview={args.preview_out}")
    print("Java runtime indexes LIVE PostgreSQL menus; preview CSV is NOT deployed.")


if __name__ == "__main__":
    main()
