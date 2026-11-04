import csv
from pathlib import Path
from urllib.parse import urlparse

import psycopg

ROOT = Path(__file__).resolve().parent
ENV_FILE = ROOT.parent / ".env"
DATA_DIR = ROOT / "data"


def read_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise FileNotFoundError(f"Không tìm thấy {path}. Giữ .env tại thư mục gốc GO Cook.")

    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        values[key.strip()] = value
    return values


def main() -> None:
    settings = read_env(ENV_FILE)
    required = ("URL_DB", "USER_NAME_DB", "PASSWORD_DB")
    missing = [key for key in required if not settings.get(key)]
    if missing:
        raise ValueError(f"Thiếu khóa trong .env: {', '.join(missing)}")

    url = urlparse(settings["URL_DB"].removeprefix("jdbc:"))
    if url.scheme != "postgresql" or not url.hostname or not url.path.strip("/"):
        raise ValueError("URL_DB phải có dạng jdbc:postgresql://host:port/database")

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with psycopg.connect(
            host=url.hostname,
            port=url.port or 5432,
            dbname=url.path.lstrip("/"),
            user=settings["USER_NAME_DB"],
            password=settings["PASSWORD_DB"],
            options="-c default_transaction_read_only=on",
            connect_timeout=10,
    ) as conn:
        for sql_name, csv_name in (
                ("export_menus.sql", "menus.csv"),
                ("export_interactions.sql", "interactions.csv"),
        ):
            query = (ROOT / "sql" / sql_name).read_text(encoding="utf-8-sig")
            with conn.cursor() as cur:
                cur.execute(query)
                with (DATA_DIR / csv_name).open("w", newline="", encoding="utf-8-sig") as file:
                    writer = csv.writer(file)
                    writer.writerow([column.name for column in cur.description])
                    count = 0
                    while rows := cur.fetchmany(1000):
                        writer.writerows(rows)
                        count += len(rows)
            print(f"OK: data/{csv_name} — {count} dòng")


if __name__ == "__main__":
    main()
