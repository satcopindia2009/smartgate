"""JWT Bearer auth + role helpers."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Annotated, Optional

import jwt
from fastapi import Depends, Header
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import BOOTSTRAP_TOKEN, JWT_ALG, JWT_SECRET, JWT_TTL_SECONDS
from app.errors import AppError
from app.models import Role
from app import store

_bearer = HTTPBearer(auto_error=False)


def create_access_token(user: dict) -> tuple[str, int]:
    now = datetime.now(timezone.utc)
    exp = now + timedelta(seconds=JWT_TTL_SECONDS)
    payload = {
        "sub": user["id"],
        "userId": user["id"],
        "schoolId": user["schoolId"],
        "role": user["role"],
        "staffId": user.get("staffId"),
        "gateIds": user.get("gateIds"),
        "displayName": user["displayName"],
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp()),
    }
    token = jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALG)
    return token, JWT_TTL_SECONDS


def decode_token(token: str) -> dict:
    try:
        return jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALG])
    except jwt.ExpiredSignatureError as e:
        raise AppError("TOKEN_EXPIRED", "Access token expired", 401) from e
    except jwt.InvalidTokenError as e:
        raise AppError("TOKEN_INVALID", "Invalid access token", 401) from e


def get_current_user(
    creds: Annotated[Optional[HTTPAuthorizationCredentials], Depends(_bearer)] = None,
) -> dict:
    if creds is None or not creds.credentials:
        raise AppError("UNAUTHORIZED", "Bearer token required", 401)
    claims = decode_token(creds.credentials)
    user = store.get_user(claims["sub"])
    if not user or not user.get("active", True):
        raise AppError("UNAUTHORIZED", "User not found or inactive", 401)
    return user


CurrentUser = Annotated[dict, Depends(get_current_user)]

BOOTSTRAP_ACTOR = {
    "id": "U-BOOTSTRAP",
    "username": "bootstrap",
    "schoolId": None,
    "role": "bootstrap",
    "staffId": None,
    "gateIds": None,
    "displayName": "Platform bootstrap",
    "active": True,
    "bootstrap": True,
}


def get_optional_user(
    creds: Annotated[Optional[HTTPAuthorizationCredentials], Depends(_bearer)] = None,
) -> Optional[dict]:
    if creds is None or not creds.credentials:
        return None
    return get_current_user(creds)


def require_school_create(
    creds: Annotated[Optional[HTTPAuthorizationCredentials], Depends(_bearer)] = None,
    x_bootstrap_token: Annotated[Optional[str], Header(alias="X-Bootstrap-Token")] = None,
) -> dict:
    """Admin / Security Head JWT, or documented X-Bootstrap-Token (first-bootstrap)."""
    token = (x_bootstrap_token or "").strip()
    if token and token == BOOTSTRAP_TOKEN:
        return dict(BOOTSTRAP_ACTOR)
    if token:
        raise AppError("FORBIDDEN", "Invalid bootstrap token", 403)
    user = get_optional_user(creds)
    if user is None:
        raise AppError(
            "UNAUTHORIZED",
            "Bearer token or X-Bootstrap-Token required to create a school",
            401,
        )
    if user["role"] not in {Role.admin.value, Role.security_head.value}:
        raise AppError(
            "FORBIDDEN",
            f"Role '{user['role']}' not allowed for this action",
            403,
            {"allowed": ["admin", "security_head"]},
        )
    return user


def require_roles(*roles: Role | str):
    allowed = {r.value if isinstance(r, Role) else r for r in roles}

    def _dep(user: CurrentUser) -> dict:
        if user["role"] not in allowed:
            raise AppError(
                "FORBIDDEN",
                f"Role '{user['role']}' not allowed for this action",
                403,
                {"allowed": sorted(allowed)},
            )
        return user

    return _dep


def assert_gate_allowed(user: dict, gate_id: Optional[str]) -> Optional[str]:
    """Gate users are limited to JWT/user.gateIds (contract §1 User)."""
    if user.get("role") != "gate":
        return gate_id
    allowed = user.get("gateIds")
    if allowed is None:
        return gate_id
    if gate_id and gate_id not in allowed:
        raise AppError(
            "FORBIDDEN",
            f"Gate user is not assigned to {gate_id}",
            403,
            {"gateIds": allowed, "gateId": gate_id},
        )
    return gate_id
