"""Satcop Smart Visitor MVP FastAPI stub."""
from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware

from app.errors import (
    AppError,
    app_error_handler,
    unhandled_error_handler,
    validation_error_handler,
)
from app.persist import load_state, persist_enabled, save_state
from app.seed import ensure_essentials, seed
from app.routers import (
    access_rules,
    auth,
    blacklist,
    dsr,
    emergency,
    exports,
    gates,
    media,
    outbox,
    passes,
    pickups,
    reports,
    schools,
    staff,
    students,
    visits,
    zones,
    retention,
)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    if persist_enabled():
        loaded = load_state()
        if loaded:
            ensure_essentials()
        else:
            seed()
        try:
            save_state()
        except OSError:
            pass
    else:
        seed()
    try:
        yield
    finally:
        if persist_enabled():
            try:
                save_state()
            except OSError:
                pass


app = FastAPI(
    title="Satcop Smart Visitor API",
    version="0.6.0",
    description=(
        "MVP FastAPI stub + Priority P2 Pickup (P1–P6+H1) + after-hours (A1–A6) "
        "+ escort/zones Access Rules (B4) + Emergency Blast (E3 / B1–B6). "
        "Contract §§0–5 visit lifecycle unchanged. SMS mock / WA skipped_hold. "
        "Demo watermark; no production / live-school deploy."
    ),
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.middleware("http")
async def _persist_after_mutating_request(request, call_next):
    response = await call_next(request)
    if (
        persist_enabled()
        and request.method in ("POST", "PUT", "PATCH", "DELETE")
        and response.status_code < 500
    ):
        try:
            save_state()
        except OSError:
            pass
    return response


app.add_exception_handler(AppError, app_error_handler)
app.add_exception_handler(RequestValidationError, validation_error_handler)
app.add_exception_handler(Exception, unhandled_error_handler)

app.include_router(auth.router, prefix="/v1")
app.include_router(gates.router, prefix="/v1")
app.include_router(staff.router, prefix="/v1")
app.include_router(media.router, prefix="/v1")
app.include_router(visits.router, prefix="/v1")
app.include_router(passes.router, prefix="/v1")
app.include_router(blacklist.router, prefix="/v1")
app.include_router(reports.router, prefix="/v1")
app.include_router(exports.router, prefix="/v1")
app.include_router(outbox.router, prefix="/v1")
app.include_router(students.router, prefix="/v1")
app.include_router(pickups.router, prefix="/v1")
app.include_router(access_rules.router, prefix="/v1")
app.include_router(zones.router, prefix="/v1")
app.include_router(emergency.router, prefix="/v1")
app.include_router(schools.router, prefix="/v1")
app.include_router(retention.router, prefix="/v1")
app.include_router(dsr.router, prefix="/v1")


@app.get("/health")
def health():
    return {"status": "ok", "service": "satcop-smart-visitor-api"}


@app.get("/")
def root():
    return {
        "service": "satcop-smart-visitor-api",
        "docs": "/docs",
        "openapi": "/openapi.json",
        "base": "/v1",
        "health": "/health",
    }
