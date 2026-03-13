#!/usr/bin/env python3
"""
Fetches and cleans a French Scrabble wordlist (ODS-derived) from GitHub.
Output: cocro-bff/src/main/resources/wordlist-fr.txt
One uppercase word per line, sorted by length then alphabetically.
Min length: 2 chars. Only letters (a-z + accents), no digits/hyphens.
"""

import re
import sys
import urllib.request
from pathlib import Path

# ODS-derived French wordlist (lorenbrichter/Words — public domain)
SOURCE_URL = "https://raw.githubusercontent.com/lorenbrichter/Words/master/Words/fr.txt"

OUTPUT_PATH = Path(__file__).parent.parent / "cocro-bff/src/main/resources/wordlist-fr.txt"

VALID_PATTERN = re.compile(r"^[a-záàâäéèêëíìîïóòôöúùûüýÿæœç]+$", re.IGNORECASE)

def fetch(url: str) -> list[str]:
    print(f"Downloading {url} ...")
    with urllib.request.urlopen(url, timeout=30) as resp:
        return resp.read().decode("utf-8").splitlines()

def clean(words: list[str]) -> list[str]:
    seen = set()
    result = []
    for word in words:
        w = word.strip().upper()
        if len(w) < 2:
            continue
        if not VALID_PATTERN.match(w):
            continue
        if w in seen:
            continue
        seen.add(w)
        result.append(w)
    # Sort by length, then alphabetically
    result.sort(key=lambda w: (len(w), w))
    return result

def main():
    words = fetch(SOURCE_URL)
    print(f"  → {len(words)} raw lines")

    words = clean(words)
    print(f"  → {len(words)} clean words")

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text("\n".join(words) + "\n", encoding="utf-8")
    print(f"  → Written to {OUTPUT_PATH}")

    # Stats
    by_len = {}
    for w in words:
        by_len.setdefault(len(w), 0)
        by_len[len(w)] += 1
    print("\nDistribution by length:")
    for length in sorted(by_len):
        print(f"  {length:2d} letters: {by_len[length]:6d} words")

if __name__ == "__main__":
    main()
