"""Satcop Smart Visitor MVP FastAPI stub."""
from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.errors import AppError, app_error_handler, unhandled_error_handler
from app.seed import seed
from app.routers import (
    auth,
    blacklist,
    exports,
    gates,
    media,
    outbox,
    passes,
    reports,
    staff,
    visits,
)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    seed()
    yield


app = FastAPI(
    title="Satcop Smart Visitor API",
    version="0.1.0",
    description="MVP Wave 1–2 in-memory stub for Mobile/Admin demos. Demo watermark; no production deploy.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.add_exception_handler(AppError, app_error_handler)
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
