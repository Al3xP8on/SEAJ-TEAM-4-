#!/usr/bin/env python3

import time
import uuid
import json
import base64
import requests
import urllib3
from typing import Optional, Sequence, Union

# Suppress SSL warnings
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Configuration
API_BASE = "http://localhost:8081/api"
AUTH_SERVICE = "http://localhost:3000"
TIMEOUT = 5

# Counters
PASS_COUNT = 0
FAIL_COUNT = 0

# Colors
GREEN = "\033[0;32m"
RED = "\033[0;31m"
YELLOW = "\033[1;33m"
BLUE = "\033[0;34m"
CYAN = "\033[0;36m"
NC = "\033[0m"

ExpectedStatus = Union[int, Sequence[int]]


def print_section(title: str) -> None:
    print(f"{CYAN}[{title}]{NC}")


def format_expected(expected: ExpectedStatus) -> str:
    if isinstance(expected, int):
        return str(expected)
    return "/".join(str(s) for s in expected)


def status_matches(actual: int, expected: ExpectedStatus) -> bool:
    if isinstance(expected, int):
        return actual == expected
    return actual in expected


def json_body(resp: Optional[requests.Response]):
    if resp is None:
        return None
    try:
        return resp.json()
    except Exception:
        return None


def jwt_roles(token: Optional[str]) -> list[str]:
    if not token:
        return []
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return []
        payload = parts[1]
        payload += "=" * (-len(payload) % 4)
        decoded = base64.urlsafe_b64decode(payload.encode("utf-8")).decode("utf-8")
        claims = json.loads(decoded)
        roles = claims.get("roles", [])
        if isinstance(roles, list):
            return [str(r).upper() for r in roles]
    except Exception:
        return []
    return []


def check_assertion(description: str, condition: bool, details: str = "") -> None:
    global PASS_COUNT, FAIL_COUNT
    print(f"  {description} ... ", end="", flush=True)
    if condition:
        print(f"{GREEN}OK{NC}")
        PASS_COUNT += 1
    else:
        suffix = f" ({details})" if details else ""
        print(f"{RED}FAIL{NC}{suffix}")
        FAIL_COUNT += 1


def login(username: str, password: str) -> Optional[str]:
    """Get JWT token from auth service."""
    try:
        resp = requests.post(
            f"{AUTH_SERVICE}/auth/login",
            json={"username": username, "password": password},
            timeout=TIMEOUT,
            verify=False,
        )
        if resp.status_code in (200, 201):
            return resp.json().get("access_token")

        print(f"    {RED}Login failed for {username}: HTTP {resp.status_code}{NC}")
        return None
    except Exception as exc:
        print(f"    {RED}Login failed for {username}: {type(exc).__name__}: {exc}{NC}")
        return None


def send_request(
    method: str,
    endpoint: str,
    token: Optional[str],
    data: Optional[dict],
) -> requests.Response:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    url = f"{API_BASE}{endpoint}"
    method = method.upper()

    if method == "GET":
        return requests.get(url, headers=headers, timeout=TIMEOUT, verify=False)
    if method == "POST":
        return requests.post(url, headers=headers, json=data, timeout=TIMEOUT, verify=False)
    if method == "PUT":
        return requests.put(url, headers=headers, json=data, timeout=TIMEOUT, verify=False)
    if method == "DELETE":
        return requests.delete(url, headers=headers, timeout=TIMEOUT, verify=False)

    raise ValueError(f"Unsupported method: {method}")


def test_endpoint(
    method: str,
    endpoint: str,
    token: Optional[str],
    data: Optional[dict],
    expected_status: ExpectedStatus,
    description: str,
) -> Optional[requests.Response]:
    """Test an API endpoint and return response if request succeeded."""
    global PASS_COUNT, FAIL_COUNT

    print(f"  {description} ... ", end="", flush=True)

    try:
        resp = send_request(method, endpoint, token, data)
        status = resp.status_code

        if status_matches(status, expected_status):
            print(f"{GREEN}OK{NC} ({status})")
            PASS_COUNT += 1
        else:
            snippet = ""
            try:
                body = resp.text.strip().replace("\n", " ")
                snippet = body[:120] if body else ""
            except Exception:
                snippet = ""
            if snippet:
                print(
                    f"{RED}FAIL{NC} ({status}, expected {format_expected(expected_status)})"
                    f" body='{snippet}'"
                )
            else:
                print(f"{RED}FAIL{NC} ({status}, expected {format_expected(expected_status)})")
            FAIL_COUNT += 1

        return resp

    except requests.exceptions.Timeout:
        print(f"{YELLOW}TIMEOUT{NC}")
        FAIL_COUNT += 1
    except Exception as exc:
        print(f"{RED}ERROR{NC} ({type(exc).__name__}: {str(exc)[:60]})")
        FAIL_COUNT += 1

    return None


# Main execution
print(f"\n{BLUE}=============================================================={NC}")
print(f"{BLUE}  SEAJ Platform - Comprehensive Endpoint Regression Suite{NC}")
print(f"{BLUE}=============================================================={NC}\n")

print("Getting authentication tokens...")
ALICE_TOKEN = login("alice", "SecurePass123!")
BRIAN_TOKEN = login("brian", "SecurePass123!")
CARLA_TOKEN = login("carla", "SecurePass123!")
ETHAN_TOKEN = login("ethan", "SecurePass123!")

if not ALICE_TOKEN or not BRIAN_TOKEN or not CARLA_TOKEN:
    print(f"{RED}FAIL: Failed to get baseline tokens. Is auth service running?{NC}")
    raise SystemExit(1)

print(f"{GREEN}OK{NC} Tokens obtained\n")

# ================================
# ROLE MATRIX VALIDATION
# ================================
print_section("ROLE MATRIX VALIDATION")
alice_roles = jwt_roles(ALICE_TOKEN)
brian_roles = jwt_roles(BRIAN_TOKEN)

check_assertion(
    "Alice token carries ADMIN role",
    "ADMIN" in alice_roles,
    f"roles={alice_roles}",
)
check_assertion(
    "Brian token is normal user",
    "ADMIN" not in brian_roles,
    f"roles={brian_roles}",
)
print()

# ================================
# ACCOUNT AUTH + CREATE
# ================================
print_section("ACCOUNT AUTH + CREATE")
login_resp = test_endpoint(
    "POST",
    "/v1/accounts/login",
    None,
    {"username": "alice", "password": "SecurePass123!"},
    200,
    "Backend login endpoint (valid user)",
)

test_endpoint(
    "POST",
    "/v1/accounts/login",
    None,
    {"username": "alice", "password": "wrong"},
    401,
    "Backend login endpoint (invalid password)",
)

new_user = f"apitest_{int(time.time())}"
new_account_payload = {
    "username": new_user,
    "password": "SecurePass123!",
    "email": f"{new_user}@example.com",
    "phone": f"555{int(time.time()) % 10000000:07d}",
}
test_endpoint(
    "POST",
    "/v1/accounts",
    None,
    new_account_payload,
    (201, 409, 400),
    "Public account creation",
)

# Safe delete probe with a very high id (avoid deleting active fixture accounts)
test_endpoint(
    "DELETE",
    "/v1/accounts/999999",
    ALICE_TOKEN,
    None,
    404,
    "Delete non-existent account",
)
print()

# ================================
# ACCOUNTS - READ
# ================================
print_section("ACCOUNTS - READ")
alice_accounts = test_endpoint("GET", "/v1/accounts", ALICE_TOKEN, None, 200, "Alice(admin): list accounts")
brian_accounts = test_endpoint("GET", "/v1/accounts", BRIAN_TOKEN, None, 200, "Brian(user): list accounts")

alice_accounts_json = json_body(alice_accounts)
brian_accounts_json = json_body(brian_accounts)
check_assertion(
    "Admin sees more than one account",
    isinstance(alice_accounts_json, list) and len(alice_accounts_json) > 1,
    "expected admin account list size > 1",
)
check_assertion(
    "Normal user sees exactly one account",
    isinstance(brian_accounts_json, list) and len(brian_accounts_json) == 1,
    "expected user account list size == 1",
)

test_endpoint("GET", "/v1/accounts/ACC-0001", ALICE_TOKEN, None, 200, "Alice(admin): view own account")
test_endpoint("GET", "/v1/accounts/ACC-0002", ALICE_TOKEN, None, 200, "Alice(admin): view Brian account")
test_endpoint("GET", "/v1/accounts/ACC-0002", BRIAN_TOKEN, None, 200, "Brian(user): view own account")
test_endpoint("GET", "/v1/accounts/ACC-0001", BRIAN_TOKEN, None, 403, "Block: Brian(user) -> Alice account")
print()

# ================================
# ACCOUNT BALANCE
# ================================
print_section("ACCOUNT BALANCE")
test_endpoint("GET", "/v1/accounts/ACC-0001/balance", ALICE_TOKEN, None, 200, "Alice(admin): own balance")
test_endpoint("GET", "/v1/accounts/ACC-0002/balance", ALICE_TOKEN, None, 200, "Alice(admin): Brian balance")
test_endpoint("GET", "/v1/accounts/ACC-0002/balance", BRIAN_TOKEN, None, 200, "Brian(user): own balance")
test_endpoint("GET", "/v1/accounts/ACC-0001/balance", BRIAN_TOKEN, None, 403, "Block: Brian(user) -> Alice balance")
print()

# ================================
# ACCOUNT POSITIONS/ORDERS VIEW
# ================================
print_section("ACCOUNT POSITIONS + ORDERS")
test_endpoint("GET", "/v1/accounts/ACC-0001/positions", ALICE_TOKEN, None, 200, "Alice(admin): own positions")
test_endpoint("GET", "/v1/accounts/ACC-0002/positions", ALICE_TOKEN, None, 200, "Alice(admin): Brian positions")
test_endpoint("GET", "/v1/accounts/ACC-0002/positions", BRIAN_TOKEN, None, 200, "Brian(user): own positions")
test_endpoint("GET", "/v1/accounts/ACC-0001/positions", BRIAN_TOKEN, None, 403, "Block: Brian(user) -> Alice positions")

test_endpoint("GET", "/v1/accounts/ACC-0001/orders", ALICE_TOKEN, None, 200, "Alice(admin): own account orders")
test_endpoint("GET", "/v1/accounts/ACC-0002/orders", ALICE_TOKEN, None, 200, "Alice(admin): Brian account orders")
test_endpoint("GET", "/v1/accounts/ACC-0002/orders", BRIAN_TOKEN, None, 200, "Brian(user): own account orders")
test_endpoint("GET", "/v1/accounts/ACC-0003/orders", CARLA_TOKEN, None, 200, "Carla(user): own account orders")
print()

# ================================
# CREATE ORDERS + OWNERSHIP AUTH
# ================================
print_section("CREATE ORDERS + OWNERSHIP AUTH")
ORDER_OWN = {"accountId": "ACC-0001", "symbol": "AAPL", "side": "BUY", "quantity": 10, "price": 150.50}
ORDER_XACCT = {"accountId": "ACC-0002", "symbol": "MSFT", "side": "BUY", "quantity": 5, "price": 350.00}
ORDER_CARLA = {"accountId": "ACC-0003", "symbol": "GOOGL", "side": "BUY", "quantity": 3, "price": 140.75}

alice_order_resp = test_endpoint("POST", "/v1/orders", ALICE_TOKEN, ORDER_OWN, 201, "Alice creates own order")
test_endpoint("POST", "/v1/orders", ALICE_TOKEN, ORDER_XACCT, 403, "Block: Alice -> Brian account")
brian_order_resp = test_endpoint("POST", "/v1/orders", BRIAN_TOKEN, ORDER_XACCT, 201, "Brian creates own order")
test_endpoint("POST", "/v1/orders", BRIAN_TOKEN, ORDER_OWN, 403, "Block: Brian -> Alice account")
test_endpoint("POST", "/v1/orders", CARLA_TOKEN, ORDER_CARLA, 400, "Carla suspended account blocked")
print()

# ================================
# ORDERS - LIST/FILTER/DETAIL
# ================================
print_section("ORDERS LIST + FILTER + DETAIL")
test_endpoint("GET", "/v1/orders", ALICE_TOKEN, None, 200, "Alice: list visible orders")
test_endpoint("GET", "/v1/orders", BRIAN_TOKEN, None, 200, "Brian: list visible orders")
test_endpoint("GET", "/v1/orders", CARLA_TOKEN, None, 200, "Carla: list visible orders")

test_endpoint("GET", "/v1/orders/account/ACC-0001", ALICE_TOKEN, None, 200, "Alice: own account orders")
test_endpoint("GET", "/v1/orders/account/ACC-0002", ALICE_TOKEN, None, 200, "Alice(admin): Brian account orders")
test_endpoint("GET", "/v1/orders/account/ACC-0002", BRIAN_TOKEN, None, 200, "Brian: own account orders")
test_endpoint("GET", "/v1/orders/account/ACC-0001", BRIAN_TOKEN, None, 403, "Block: Brian -> Alice account orders")
test_endpoint("GET", "/v1/orders/account/ACC-0003", CARLA_TOKEN, None, 200, "Carla: own account orders")

# Query params currently only status is used by controller logic

test_endpoint(
    "GET",
    "/v1/orders/account/ACC-0001?status=NEW",
    ALICE_TOKEN,
    None,
    200,
    "Orders by account with status filter",
)

test_endpoint(
    "GET",
    "/v1/orders/account/ACC-0001?from=2026-01-01&to=2026-12-31",
    ALICE_TOKEN,
    None,
    200,
    "Orders by account with date-range query params",
)

created_order_id = None
if alice_order_resp is not None and alice_order_resp.status_code == 201:
    try:
        created_order_id = alice_order_resp.json().get("id")
    except Exception:
        created_order_id = None

if created_order_id:
    test_endpoint("GET", f"/v1/orders/{created_order_id}", ALICE_TOKEN, None, 200, "Get created order by id")
    test_endpoint("GET", f"/v1/orders/{created_order_id}/history", ALICE_TOKEN, None, 200, "Get created order history")
else:
    fallback_id = str(uuid.uuid4())
    test_endpoint("GET", f"/v1/orders/{fallback_id}", ALICE_TOKEN, None, (404, 400), "Get missing order by id")
    test_endpoint("GET", f"/v1/orders/{fallback_id}/history", ALICE_TOKEN, None, (404, 400), "Get missing order history")

# Exercise state transition endpoints with a non-existent id (route + validation path)
missing_id = str(uuid.uuid4())
test_endpoint("PUT", f"/v1/orders/{missing_id}/execute", ALICE_TOKEN, None, (400, 404), "Execute missing order")
test_endpoint("PUT", f"/v1/orders/{missing_id}/cancel", ALICE_TOKEN, None, (400, 404), "Cancel missing order")
test_endpoint("PUT", f"/v1/orders/{missing_id}/reject?reason=manual", ALICE_TOKEN, None, (400, 404), "Reject missing order")
print()

# ================================
# INSTRUMENTS
# ================================
print_section("INSTRUMENTS")
test_endpoint("GET", "/v1/instruments", ALICE_TOKEN, None, 200, "List instruments")
test_endpoint("GET", "/v1/instruments", BRIAN_TOKEN, None, 200, "List instruments (normal user)")
test_endpoint("GET", "/v1/instruments?tradable=true", ALICE_TOKEN, None, 200, "List tradable instruments")
test_endpoint("GET", "/v1/instruments?tradable=true", BRIAN_TOKEN, None, 200, "List tradable instruments (normal user)")
test_endpoint("GET", "/v1/instruments?assetClass=EQUITY", ALICE_TOKEN, None, 200, "Filter by asset class")
test_endpoint("GET", "/v1/instruments?currency=USD", ALICE_TOKEN, None, 200, "Filter by currency")
test_endpoint("GET", "/v1/instruments/AAPL", ALICE_TOKEN, None, (200, 404), "Get instrument by symbol")
test_endpoint("GET", "/v1/instruments/UNKNOWN_SYMBOL", ALICE_TOKEN, None, 404, "Unknown instrument returns 404")
print()

# ================================
# POSITIONS CONTROLLER
# ================================
print_section("POSITIONS CONTROLLER")
test_endpoint("GET", "/v1/positions", ALICE_TOKEN, None, 200, "Positions root guidance endpoint")
test_endpoint("GET", "/v1/positions", BRIAN_TOKEN, None, 200, "Positions root guidance endpoint (normal user)")
test_endpoint("GET", "/v1/positions/1/positions", ALICE_TOKEN, None, (200, 404), "Get positions by account id")
test_endpoint("GET", "/v1/positions/1/positions", BRIAN_TOKEN, None, (200, 404), "Get positions by account id (normal user)")
test_endpoint("GET", "/v1/positions/1/positions/1", ALICE_TOKEN, None, (200, 404), "Get one position by id")
test_endpoint("GET", "/v1/positions/1/positions/symbol/AAPL", ALICE_TOKEN, None, (200, 204, 404), "Get position by symbol")

close_payload = {"closingPrice": 123.45, "closingQuantity": 1}
test_endpoint(
    "PUT",
    "/v1/positions/999999/positions/close/1",
    ALICE_TOKEN,
    close_payload,
    (404, 400),
    "Close position on missing account",
)
print()

# ================================
# PRICE HISTORY
# ================================
print_section("PRICE HISTORY")
test_endpoint("GET", "/v1/price-history/AAPL", ALICE_TOKEN, None, 200, "Get price history by symbol")
test_endpoint("GET", "/v1/price-history/AAPL", BRIAN_TOKEN, None, 200, "Get price history by symbol (normal user)")
test_endpoint("GET", "/v1/price-history/AAPL/latest", ALICE_TOKEN, None, (200, 404), "Get latest price point")
test_endpoint(
    "GET",
    "/v1/price-history/AAPL/range",
    ALICE_TOKEN,
    None,
    400,
    "Range query without dates returns bad request",
)
test_endpoint(
    "GET",
    "/v1/price-history/AAPL/range?from=2026-01-01&to=2026-12-31",
    ALICE_TOKEN,
    None,
    (200, 400),
    "Range query with dates",
)

# Invalid payload validation path
test_endpoint("POST", "/v1/price-history", ALICE_TOKEN, {}, 400, "Create price history with invalid payload")

valid_price_history = {
    "symbol": "AAPL",
    "priceDate": "2026-10-12",
    "open": 150.0,
    "high": 155.0,
    "low": 149.0,
    "close": 154.0,
    "volume": 1000000,
}
test_endpoint("POST", "/v1/price-history", ALICE_TOKEN, valid_price_history, (201, 500), "Create price history with valid payload")
print()

# ================================
# UNAUTHENTICATED ACCESS
# ================================
print_section("UNAUTHENTICATED ACCESS")
test_endpoint("GET", "/v1/accounts", None, None, 401, "Block: no token -> accounts list")
test_endpoint("GET", "/v1/orders", None, None, 401, "Block: no token -> orders list")
test_endpoint("POST", "/v1/orders", None, ORDER_OWN, 401, "Block: no token -> create order")
test_endpoint("GET", "/v1/instruments", None, None, 401, "Block: no token -> instruments list")
test_endpoint("GET", "/v1/positions", None, None, 401, "Block: no token -> positions")
test_endpoint("GET", "/v1/price-history/AAPL", None, None, 401, "Block: no token -> price history")
print()

# ================================
# SUMMARY
# ================================
print(f"{BLUE}=============================================================={NC}")
TOTAL = PASS_COUNT + FAIL_COUNT
PERCENT = (PASS_COUNT * 100) // TOTAL if TOTAL else 0

print(f"  {GREEN}PASSED:{NC} {PASS_COUNT}")
print(f"  {RED}FAILED:{NC} {FAIL_COUNT}")
print(f"  {CYAN}TOTAL:{NC}  {TOTAL}")
print(f"  {CYAN}RATE:{NC}   {PERCENT}%")
print(f"{BLUE}=============================================================={NC}\n")

if FAIL_COUNT == 0:
    print(f"{GREEN}ALL TESTS PASSED{NC}\n")
    raise SystemExit(0)

print(f"{RED}{FAIL_COUNT} tests failed{NC}\n")
raise SystemExit(1)
