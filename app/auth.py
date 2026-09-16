"""JWT Bearer auth + role helpers."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Annotated, Optional

import jwt
from fastapi import Depends, Header

from app.config import JWT_ALG, JWT_SECRET, JWT_TTL_SECONDS
from app.errors import AppError
from app.models import Role
from app import store


def create_access_token(user: dict) -> tuple[str, int]:
    now = datetime.now(timezone.utc)
    exp = now + timedelta(seconds=JWT_TTL_SECONDS)
    payload = {
        "sub": user["id"],
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
    authorization: Annotated[Optional[str], Header()] = None,
) -> dict:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise AppError("UNAUTHORIZED", "Bearer token required", 401)
    token = authorization.split(" ", 1)[1].strip()
    claims = decode_token(token)
    user = store.get_user(claims["sub"])
    if not user or not user.get("active", True):
        raise AppError("UNAUTHORIZED", "User not found or inactive", 401)
    return user


CurrentUser = Annotated[dict, Depends(get_current_user)]


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
