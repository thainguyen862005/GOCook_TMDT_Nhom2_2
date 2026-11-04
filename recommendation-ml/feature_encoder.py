from __future__ import annotations

import math
import re
import unicodedata
import zlib
from collections import defaultdict

DEFAULT_CONFIG = {
    "schemaVersion": 2,
    "algorithm": "HASHED_CHAR_NGRAM_COSINE_ITEM_KNN",
    "hashDimension": 16384,
    "minNgram": 3,
    "maxNgram": 5,
    "defaultK": 5,
    "maxNeighbors": 30,
    "fieldWeights": {
        "name": 1.3,
        "category": 2.5,
        "dishItems": 2.0,
        "description": 0.45,
        "categoryExact": 4.0
    }
}


def normalize(value: object) -> str:
    value = "" if value is None else str(value)
    value = value.lower().replace("đ", "d")
    value = "".join(ch for ch in unicodedata.normalize("NFD", value)
                    if unicodedata.category(ch) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", value).strip()


def hash_token(token: str, dimension: int) -> int:
    return (zlib.crc32(token.encode("ascii")) & 0xFFFFFFFF) % dimension


def encode(menu: dict, cfg: dict = DEFAULT_CONFIG) -> dict[int, float]:
    dimensions = int(cfg["hashDimension"])
    minimum = int(cfg["minNgram"])
    maximum = int(cfg["maxNgram"])
    weights = cfg["fieldWeights"]
    counts: dict[int, float] = defaultdict(float)

    for key in ("category", "dishItems", "name", "description"):
        weight = float(weights[key])
        for word in normalize(menu.get(key, "")).split():
            padded = f" {word} "
            for n in range(minimum, maximum + 1):
                for start in range(max(0, len(padded) - n + 1)):
                    index = hash_token("g:" + padded[start:start + n], dimensions)
                    counts[index] += weight

    category = normalize(menu.get("category", ""))
    if category:
        index = hash_token("cat:" + category.replace(" ", "_"), dimensions)
        counts[index] += float(weights["categoryExact"])

    norm = math.sqrt(sum(x * x for x in counts.values()))
    if norm == 0:
        return {}
    return {i: count / norm for i, count in counts.items()}


def dot(a: dict[int, float], b: dict[int, float]) -> float:
    if len(a) > len(b):
        a, b = b, a
    return max(0.0, min(1.0, sum(v * b.get(i, 0.0) for i, v in a.items())))
