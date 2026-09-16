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
