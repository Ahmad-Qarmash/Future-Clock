#!/usr/bin/env python3
"""Perform structural, relational, search, and timezone checks on the packaged catalog."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import shutil
import sqlite3
import subprocess
import tempfile
from pathlib import Path


SAMPLE_SEARCHES = (
    "New York",
    "Nazareth",
    "Hallstatt",
    "Longyearbyen",
    "Ushuaia",
    "Iqaluit",
    "Goroka",
    "Vaitape",
    "São Paulo",
    "st. john's",
    "America New York",
)
REQUIRED_COUNTRIES = ("US", "GB", "IL", "JP", "IN", "BR", "ZA", "AU")
REQUIRED_COLUMNS = (
    "id",
    "name",
    "country",
    "country_code",
    "flag",
    "admin1",
    "timezone_id",
    "population",
    "latitude",
    "longitude",
)


def match_expression(query: str) -> str:
    import re

    parts = [part for part in re.split(r"[^\w]+", query, flags=re.UNICODE) if part]
    return " AND ".join(f"{part}*" for part in parts[:8])


def scalar(database: sqlite3.Connection, sql: str, parameters: tuple[object, ...] = ()) -> object:
    return database.execute(sql, parameters).fetchone()[0]


def verify_java_timezones(java: Path, timezones: list[str], temp_dir: Path) -> None:
    ids_file = temp_dir / "timezone-ids.txt"
    ids_file.write_text("\n".join(timezones), encoding="utf-8")
    source = temp_dir / "VerifyTimeZones.java"
    source.write_text(
        """
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;

public class VerifyTimeZones {
    public static void main(String[] args) throws Exception {
        Set<String> legacy = new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
        List<String> invalid = new ArrayList<>();
        for (String id : Files.readAllLines(Path.of(args[0]))) {
            try {
                ZoneId.of(id);
                if (!legacy.contains(id)) invalid.add(id + " (missing from TimeZone)");
            } catch (Exception error) {
                invalid.add(id + " (" + error.getClass().getSimpleName() + ")");
            }
        }
        if (!invalid.isEmpty()) {
            throw new IllegalStateException("Invalid Java timezone IDs: " + invalid);
        }
        System.out.println("Java resolved " + legacy.size() + " available IDs; catalog IDs are valid.");
    }
}
""".strip(),
        encoding="utf-8",
    )
    subprocess.run(
        [str(java), str(source), str(ids_file)],
        cwd=temp_dir,
        check=True,
        text=True,
    )


def verify(asset: Path, java: Path | None = None) -> None:
    assert asset.is_file(), f"Catalog asset does not exist: {asset}"
    digest = hashlib.sha256(asset.read_bytes()).hexdigest()

    with tempfile.TemporaryDirectory(prefix="future-clock-catalog-check-") as raw_temp_dir:
        temp_dir = Path(raw_temp_dir)
        database_path = temp_dir / "places.sqlite"
        with gzip.open(asset, "rb") as source, database_path.open("wb") as destination:
            shutil.copyfileobj(source, destination)

        database = sqlite3.connect(f"file:{database_path}?mode=ro", uri=True)
        try:
            integrity_rows = [row[0] for row in database.execute("PRAGMA integrity_check")]
            quick_check_rows = [row[0] for row in database.execute("PRAGMA quick_check")]
            foreign_key_failures = list(database.execute("PRAGMA foreign_key_check"))
            user_version = scalar(database, "PRAGMA user_version")

            objects = list(
                database.execute(
                    """
                    SELECT type, name, sql
                    FROM sqlite_master
                    WHERE name NOT LIKE 'sqlite_%'
                    ORDER BY type, name
                    """
                )
            )
            table_names = {name for kind, name, _ in objects if kind == "table"}
            index_names = {name for kind, name, _ in objects if kind == "index"}
            trigger_names = {name for kind, name, _ in objects if kind == "trigger"}
            columns = {row[1] for row in database.execute("PRAGMA table_info(places)")}

            place_count = scalar(database, "SELECT COUNT(*) FROM places")
            country_count = scalar(database, "SELECT COUNT(DISTINCT country_code) FROM places")
            timezone_count = scalar(database, "SELECT COUNT(DISTINCT timezone_id) FROM places")
            duplicate_ids = scalar(
                database,
                "SELECT COUNT(*) FROM (SELECT id FROM places GROUP BY id HAVING COUNT(*) > 1)",
            )
            duplicate_normalized = scalar(
                database,
                """
                SELECT COUNT(*) FROM (
                    SELECT lower(trim(name)), country_code, lower(trim(admin1)), timezone_id
                    FROM places
                    GROUP BY lower(trim(name)), country_code, lower(trim(admin1)), timezone_id
                    HAVING COUNT(*) > 1
                )
                """,
            )
            invalid_required = scalar(
                database,
                """
                SELECT COUNT(*) FROM places
                WHERE trim(name) = '' OR trim(country) = '' OR trim(country_code) = ''
                   OR trim(flag) = '' OR trim(timezone_id) = ''
                   OR population < 0
                """,
            )
            malformed_country_codes = scalar(
                database,
                """
                SELECT COUNT(*) FROM places
                WHERE length(country_code) != 2 OR country_code != upper(country_code)
                """,
            )
            invalid_country_mappings = scalar(
                database,
                """
                SELECT COUNT(*) FROM (
                    SELECT country_code
                    FROM places
                    GROUP BY country_code
                    HAVING COUNT(DISTINCT country) != 1
                       OR COUNT(DISTINCT flag) != 1
                       OR MIN(trim(country)) = ''
                )
                """,
            )
            malformed_coordinates = scalar(
                database,
                """
                SELECT COUNT(*) FROM places
                WHERE typeof(latitude) NOT IN ('real', 'integer')
                   OR typeof(longitude) NOT IN ('real', 'integer')
                   OR latitude NOT BETWEEN -90.0 AND 90.0
                   OR longitude NOT BETWEEN -180.0 AND 180.0
                """,
            )
            metadata_version = int(
                scalar(database, "SELECT value FROM metadata WHERE key = 'catalog_version'")
            )
            assert integrity_rows == ["ok"], f"integrity_check failed: {integrity_rows[:10]}"
            assert quick_check_rows == ["ok"], f"quick_check failed: {quick_check_rows[:10]}"
            assert not foreign_key_failures, f"Foreign-key failures: {foreign_key_failures[:10]}"
            assert {"places", "places_fts", "metadata"} <= table_names, table_names
            assert {
                "places_population_idx",
                "places_timezone_idx",
                "places_country_rank_idx",
            } <= index_names, index_names
            assert set(REQUIRED_COLUMNS) <= columns, columns
            assert user_version == 2, f"Unexpected user_version: {user_version}"
            assert metadata_version == user_version, (
                f"Catalog metadata version {metadata_version} != user_version {user_version}"
            )
            assert place_count >= 200_000, f"Expected at least 200,000 places, found {place_count:,}"
            assert country_count >= 230, f"Expected global coverage, found {country_count}"
            assert timezone_count >= 350, f"Expected broad timezone coverage, found {timezone_count}"
            assert duplicate_ids == 0, f"Duplicate primary IDs: {duplicate_ids}"
            assert invalid_required == 0, f"Invalid required fields: {invalid_required}"
            assert malformed_country_codes == 0, f"Malformed country codes: {malformed_country_codes}"
            assert invalid_country_mappings == 0, (
                f"Invalid country mappings: {invalid_country_mappings}"
            )
            assert malformed_coordinates == 0, f"Malformed coordinates: {malformed_coordinates}"

            present_countries = {
                row[0]
                for row in database.execute(
                    "SELECT DISTINCT country_code FROM places WHERE country_code IN (%s)"
                    % ",".join("?" for _ in REQUIRED_COUNTRIES),
                    REQUIRED_COUNTRIES,
                )
            }
            assert present_countries == set(REQUIRED_COUNTRIES), (
                f"Missing representative countries: {set(REQUIRED_COUNTRIES) - present_countries}"
            )

            for search in SAMPLE_SEARCHES:
                results = database.execute(
                    """
                    SELECT p.name, p.country, p.timezone_id, p.population
                    FROM places_fts
                    JOIN places p ON p.id = places_fts.docid
                    WHERE places_fts MATCH ?
                    ORDER BY
                        CASE
                            WHEN lower(p.name) = lower(?) THEN 0
                            WHEN lower(p.name) LIKE lower(?) THEN 1
                            ELSE 2
                        END,
                        p.population DESC,
                        p.name COLLATE NOCASE,
                        p.id
                    LIMIT 5
                    """,
                    (match_expression(search), search, f"{search}%"),
                ).fetchall()
                assert results, f"Representative search returned no result: {search}"
                result = results[0]
                print(f"{search}: {result[0]}, {result[1]} ({result[2]})")

            timezones = [
                row[0]
                for row in database.execute(
                    "SELECT DISTINCT timezone_id FROM places ORDER BY timezone_id"
                )
            ]
            if java is not None:
                verify_java_timezones(java, timezones, temp_dir)

            print(f"SHA-256: {digest}")
            print(
                f"Schema: user_version={user_version}; tables={sorted(table_names)}; "
                f"indexes={sorted(index_names)}; triggers={sorted(trigger_names)}"
            )
            print(
                "Checks: integrity=ok; quick_check=ok; foreign_keys=ok; "
                "coordinates=ok"
            )
            print(
                f"Verified {place_count:,} places, {country_count} country/territory codes, "
                f"and {timezone_count} IANA timezones."
            )
            print(
                f"Normalized duplicate place groups: {duplicate_normalized:,} "
                "(allowed for distinct GeoNames identifiers)."
            )
        finally:
            database.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("asset", type=Path)
    parser.add_argument("--java", type=Path, help="Java executable used to resolve every timezone ID")
    args = parser.parse_args()
    verify(args.asset, args.java)


if __name__ == "__main__":
    main()
