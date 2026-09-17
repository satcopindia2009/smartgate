"""Error envelope helpers matching API contract §4."""
from __future__ import annotations

from typing import Any, Optional

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse


class AppError(Exception):
    def __init__(
        self,
        code: str,
        message: str,
        status_code: int = 400,
        details: Optional[Any] = None,
    ):
        self.code = code
        self.message = message
        self.status_code = status_code
        self.details = details
        super().__init__(message)


def error_body(code: str, message: str, details: Any = None) -> dict:
    err: dict[str, Any] = {"code": code, "message": message}
    if details is not None:
        err["details"] = details
    return {"error": err}


def _validation_message(errors: list[dict[str, Any]]) -> str:
    if not errors:
        return "Validation failed"
    err = errors[0]
    loc = [str(x) for x in err.get("loc", []) if x not in ("body", "query", "path", "header")]
    field = loc[-1] if loc else None
    msg = err.get("msg") or "Validation failed"
    if msg.startswith("Value error, "):
        msg = msg[len("Value error, ") :]
    if field and err.get("type") in ("missing", "value_error.missing"):
        return f"{field} is required"
    if field and "required" in msg.lower():
        return f"{field} is required"
    if field:
        return f"{field}: {msg}"
    return msg


async def app_error_handler(_request: Request, exc: AppError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content=error_body(exc.code, exc.message, exc.details),
    )


async def validation_error_handler(
    _request: Request, exc: RequestValidationError
) -> JSONResponse:
    details = []
    for err in exc.errors():
        details.append(
            {
                "loc": list(err.get("loc", [])),
                "msg": err.get("msg"),
                "type": err.get("type"),
            }
        )
    return JSONResponse(
        status_code=400,
        content=error_body("VALIDATION", _validation_message(details), details),
    )


async def unhandled_error_handler(_request: Request, exc: Exception) -> JSONResponse:
    return JSONResponse(
        status_code=500,
        content=error_body("INTERNAL", "Unexpected server error", str(exc)),
    )
