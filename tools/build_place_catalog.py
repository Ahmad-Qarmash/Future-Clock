#!/usr/bin/env python3
"""Build Future Clock's offline place-search database from GeoNames exports.

Source files:
  https://download.geonames.org/export/dump/cities500.zip
  https://download.geonames.org/export/dump/countryInfo.txt
  https://download.geonames.org/export/dump/admin1CodesASCII.txt
"""

from __future__ import annotations

import argparse
import gzip
import os
import sqlite3
import tempfile
import zipfile
from pathlib import Path


def country_flag(code: str) -> str:
    if len(code) != 2 or not code.isalpha():
        return "🌐"
    return "".join(chr(0x1F1E6 + ord(char.upper()) - ord("A")) for char in code)


def read_countries(path: Path) -> dict[str, str]:
    countries: dict[str, str] = {}
    with path.open(encoding="utf-8") as source:
        for line in source:
            if not line.strip() or line.startswith("#"):
                continue
            fields = line.rstrip("\n").split("\t")
            if len(fields) > 4:
                countries[fields[0]] = fields[4]
    return countries


def read_admin1(path: Path) -> dict[str, str]:
    divisions: dict[str, str] = {}
    with path.open(encoding="utf-8") as source:
        for line in source:
            fields = line.rstrip("\n").split("\t")
            if len(fields) >= 2:
                divisions[fields[0]] = fields[1]
    return divisions


def compact_aliases(name: str, ascii_name: str, raw_aliases: str) -> str:
    seen = {name.casefold(), ascii_name.casefold()}
    aliases: list[str] = []
    if ascii_name and ascii_name.casefold() != name.casefold():
        aliases.append(ascii_name)
    for alias in raw_aliases.split(","):
        alias = alias.strip()
        folded = alias.casefold()
        if not alias or folded in seen or len(alias) > 100:
            continue
        seen.add(folded)
        aliases.append(alias)
        if len(aliases) >= 32:
            break
    return " ".join(aliases)


def build_database(cities_zip: Path, country_info: Path, admin1_codes: Path, output: Path) -> None:
    countries = read_countries(country_info)
    divisions = read_admin1(admin1_codes)
    output.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="future-clock-places-") as temp_dir:
        database_path = Path(temp_dir) / "places.sqlite"
        connection = sqlite3.connect(database_path)
        connection.executescript(
            """
            PRAGMA journal_mode=OFF;
            PRAGMA synchronous=OFF;
            PRAGMA temp_store=MEMORY;
            PRAGMA page_size=4096;
            PRAGMA user_version=2;

            CREATE TABLE places (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                country TEXT NOT NULL,
                country_code TEXT NOT NULL,
                flag TEXT NOT NULL,
                admin1 TEXT NOT NULL,
                timezone_id TEXT NOT NULL,
                population INTEGER NOT NULL CHECK (population >= 0),
                latitude REAL NOT NULL CHECK (latitude BETWEEN -90.0 AND 90.0),
                longitude REAL NOT NULL CHECK (longitude BETWEEN -180.0 AND 180.0)
            );

            CREATE INDEX places_population_idx ON places(population DESC);
            CREATE INDEX places_timezone_idx ON places(timezone_id);
            CREATE INDEX places_country_rank_idx
                ON places(country_code, population DESC, name COLLATE NOCASE, id);

            CREATE VIRTUAL TABLE places_fts USING fts4(
                name,
                aliases,
                country,
                admin1,
                timezone_id,
                tokenize=unicode61
            );

            CREATE TABLE metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """
        )

        place_rows: list[tuple[object, ...]] = []
        search_rows: list[tuple[object, ...]] = []
        count = 0
        newest_date = ""

        with zipfile.ZipFile(cities_zip) as archive:
            text_name = next(name for name in archive.namelist() if name.endswith(".txt"))
            with archive.open(text_name) as raw, \
                    __import__("io").TextIOWrapper(raw, encoding="utf-8", newline="") as source:
                for line in source:
                    fields = line.rstrip("\n").split("\t")
                    if len(fields) < 19 or fields[6] != "P" or not fields[17]:
                        continue

                    place_id = int(fields[0])
                    name = fields[1]
                    ascii_name = fields[2]
                    country_code = fields[8]
                    admin_key = f"{country_code}.{fields[10]}"
                    country = countries.get(country_code, country_code)
                    admin1 = divisions.get(admin_key, "")
                    timezone_id = fields[17]
                    population = int(fields[14] or 0)
                    latitude = float(fields[4])
                    longitude = float(fields[5])
                    aliases = compact_aliases(name, ascii_name, fields[3])

                    place_rows.append((
                        place_id,
                        name,
                        country,
                        country_code,
                        country_flag(country_code),
                        admin1,
                        timezone_id,
                        population,
                        latitude,
                        longitude,
                    ))
                    search_rows.append((
                        place_id,
                        name,
                        aliases,
                        country,
                        admin1,
                        timezone_id.replace("_", " ").replace("/", " "),
                    ))
                    newest_date = max(newest_date, fields[18])
                    count += 1

                    if len(place_rows) >= 5000:
                        connection.executemany(
                            "INSERT INTO places VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", place_rows
                        )
                        connection.executemany(
                            "INSERT INTO places_fts(docid, name, aliases, country, admin1, timezone_id) "
                            "VALUES (?, ?, ?, ?, ?, ?)",
                            search_rows,
                        )
                        place_rows.clear()
                        search_rows.clear()

        if place_rows:
            connection.executemany(
                "INSERT INTO places VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", place_rows
            )
            connection.executemany(
                "INSERT INTO places_fts(docid, name, aliases, country, admin1, timezone_id) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                search_rows,
            )

        connection.executemany(
            "INSERT INTO metadata(key, value) VALUES (?, ?)",
            (
                ("catalog_version", "2"),
                ("place_count", str(count)),
                ("source_date", newest_date),
                ("source", "GeoNames cities500"),
            ),
        )
        connection.commit()
        connection.execute("VACUUM")
        connection.close()

        with database_path.open("rb") as source, output.open("wb") as raw_output:
            with gzip.GzipFile(filename="places.sqlite", mode="wb", fileobj=raw_output, mtime=0) as compressed:
                while chunk := source.read(1024 * 1024):
                    compressed.write(chunk)

    print(f"Built {count:,} places at {output} ({output.stat().st_size / 1024 / 1024:.1f} MiB gzip)")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cities", type=Path, required=True)
    parser.add_argument("--countries", type=Path, required=True)
    parser.add_argument("--admin1", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build_database(args.cities, args.countries, args.admin1, args.output)


if __name__ == "__main__":
    main()
