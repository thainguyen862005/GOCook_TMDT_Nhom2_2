from pathlib import Path
import pyarrow.parquet as pq
from huggingface_hub import HfApi, hf_hub_download

REPO_ID = "AmazonScience/massive"
REVISION = "refs/convert/parquet"
OUTPUT_DIR =  Path(__file__).resolve().parent / "data" / "massive"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

api = HfApi()

files = api.list_repo_files(
    repo_id=REPO_ID,
    repo_type="dataset",
    revision=REVISION
)

for split in ("train", "validation", "test"):
    matching_files = sorted(
        path for path in files
        if path.startswith(f"vi-VN/{split}/")
        and path.endswith(".parquet")
    )

    if not matching_files:
        raise RuntimeError(
            f"No Parquet files found for vi-VN/{split}"
        )

    total = 0

    for filename in matching_files:
        print(f"Downloading: {filename}")

        local_path = hf_hub_download(
            repo_id=REPO_ID,
            filename=filename,
            repo_type="dataset",
            revision=REVISION,
            local_dir=str(OUTPUT_DIR)
        )

        parquet_file = pq.ParquetFile(local_path)
        total += parquet_file.metadata.num_rows

        example = parquet_file.read_row_group(0).slice(0, 1).to_pylist()[0]

        print("Example:", example.get("utt"))
        print("Intent:", example.get("intent"))

    print(f"{split}: {total} rows")

print("\nMASSIVE Vietnamese download completed!")
