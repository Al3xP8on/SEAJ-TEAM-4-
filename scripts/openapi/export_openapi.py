#!/usr/bin/env python3
"""
Export the trading API's OpenAPI spec from the running Spring Boot app (springdoc).
(need the backend running, spin up via app service.)
"""
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
API_URL = os.environ.get("API_URL", "http://localhost:8081/api")
OUTPUT = REPO_ROOT / os.environ.get("OUTPUT", "docs/openapi/trading-api.json")
TIMEOUT_SECONDS = 15

def fetch_spec(url: str) -> dict:
    try:
        with urllib.request.urlopen(url, timeout=TIMEOUT_SECONDS) as response:
            return json.load(response)
    except (urllib.error.URLError, TimeoutError) as error:
        sys.exit(f"Could not fetch {url} ({error}). Is the backend running? (docker-compose up -d app)")
    except json.JSONDecodeError:
        sys.exit(f"{url} did not return JSON")


def main() -> None:
    spec = fetch_spec(f"{API_URL}/v3/api-docs")

    # exit if not an OpenAPI spec.
    if "openapi" not in spec or "paths" not in spec:
        sys.exit("Response is not an OpenAPI document")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    # indent with spaces for readability
    OUTPUT.write_text(json.dumps(spec, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"OpenAPI {spec['openapi']}: {len(spec['paths'])} paths")
    print(f"Spec written to {OUTPUT.relative_to(REPO_ROOT)}")

if __name__ == "__main__":
    main()
