import os

os.environ.setdefault("SATCOP_PERSIST", "0")

"""Shared fixtures for MVP contract tests."""
from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.seed import seed


@pytest.fixture(autouse=True)
def _reseed():
    seed()
    yield


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


def login(client: TestClient, username: str, password: str) -> str:
    r = client.post("/v1/auth/login", json={"username": username, "password": password})
    assert r.status_code == 200, r.text
    return r.json()["accessToken"]


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


WEEKDAYS = ("mon", "tue", "wed", "thu", "fri", "sat", "sun")


def put_week_hours(
    client,
    token: str,
    *,
    closed: bool = False,
    open_time: str = "00:00",
    close_time: str = "23:59",
    overnight: bool = False,
):
    """Admin/SH helper — full 7-day PUT for after-hours tests."""
    days = []
    for wd in WEEKDAYS:
        days.append(
            {
                "weekday": wd,
                "timezone": "Asia/Kolkata",
                "openTime": None if closed else open_time,
                "closeTime": None if closed else close_time,
                "closed": closed,
                "overnight": overnight,
            }
        )
    r = client.put("/v1/access-rules/hours", headers=auth(token), json=days)
    assert r.status_code == 200, r.text
    return r
