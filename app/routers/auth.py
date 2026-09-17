from __future__ import annotations

from fastapi import APIRouter

from app.auth import CurrentUser, create_access_token
from app.config import WATERMARK
from app.errors import AppError
from app.models import LoginRequest, LoginResponse, MeResponse, UserPublic
from app import store

router = APIRouter(prefix="/auth", tags=["auth"])


def _public_user(u: dict) -> dict:
    return UserPublic(
        id=u["id"],
        schoolId=u["schoolId"],
        role=u["role"],
        staffId=u.get("staffId"),
        gateIds=u.get("gateIds"),
        displayName=u["displayName"],
        phone=u.get("phone"),
        email=u.get("email"),
    ).model_dump()


@router.post("/login", response_model=LoginResponse)
def login(body: LoginRequest):
    user = store.get_user_by_username(body.username)
    if not user or user.get("password") != body.password:
        raise AppError("INVALID_CREDENTIALS", "Invalid username or password", 401)
    if not user.get("active", True):
        raise AppError("USER_INACTIVE", "User is inactive", 403)
    token, ttl = create_access_token(user)
    return {
        "accessToken": token,
        "tokenType": "Bearer",
        "expiresIn": ttl,
        "user": _public_user(user),
        "meta": {"watermark": WATERMARK},
    }


@router.get("/me", response_model=MeResponse)
def me(user: CurrentUser):
    return {**_public_user(user), "meta": {"watermark": WATERMARK}}
