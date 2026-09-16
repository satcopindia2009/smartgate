"""Pydantic v2 models — field names locked to API contract."""
from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Literal, Optional

from pydantic import BaseModel, Field


class Role(str, Enum):
    gate = "gate"
    host = "host"
    admin = "admin"
    security_head = "security_head"


class VisitorType(str, Enum):
    Parent = "Parent"
    Vendor = "Vendor"
    Guest = "Guest"
    Official = "Official"
    Alumni = "Alumni"


class IdType(str, Enum):
    Aadhaar = "Aadhaar"
    DL = "DL"
    Voter = "Voter"
    Passport = "Passport"
    Other = "Other"


class VisitStatus(str, Enum):
    pending = "pending"
    approved = "approved"
    rejected = "rejected"
    inside = "inside"
    completed = "completed"
    force_completed = "force_completed"


class CheckoutType(str, Enum):
    normal = "normal"
    force = "force"
    never = "never"


class Severity(str, Enum):
    Block = "Block"
    Alert = "Alert"


class MediaKind(str, Enum):
    live_photo = "live_photo"
    id_image = "id_image"
    signature = "signature"
    other = "other"


# --- Auth ---


class LoginRequest(BaseModel):
    username: str
    password: str


class LoginResponse(BaseModel):
    accessToken: str
    tokenType: str = "Bearer"
    expiresIn: int
    user: dict


class MeResponse(BaseModel):
    id: str
    schoolId: str
    role: Role
    staffId: Optional[str] = None
    gateIds: Optional[list[str]] = None
    displayName: str
    phone: Optional[str] = None
    email: Optional[str] = None


# --- Directory ---


class GateOut(BaseModel):
    id: str
    schoolId: str
    name: str
    active: bool


class StaffOut(BaseModel):
    id: str
    schoolId: str
    name: str
    roleTitle: str
    mobile: Optional[str] = None
    userId: Optional[str] = None
    active: bool


class StaffCreate(BaseModel):
    name: str
    roleTitle: str
    mobile: Optional[str] = None
    userId: Optional[str] = None
    active: bool = True


class StaffPatch(BaseModel):
    name: Optional[str] = None
    roleTitle: Optional[str] = None
    mobile: Optional[str] = None
    userId: Optional[str] = None
    active: Optional[bool] = None


# --- Media ---


class MediaUploadResponse(BaseModel):
    key: str
    url: str


# --- Visits ---


class VisitCreate(BaseModel):
    visitorName: str
    mobile: str
    visitorType: VisitorType
    purpose: str
    hostId: str
    livePhotoKey: str
    idType: IdType
    idNumber: Optional[str] = None
    idImageKey: Optional[str] = None
    vehicleNumber: Optional[str] = None
    accompanyingCount: Optional[int] = None
    notes: Optional[str] = None
    signatureKey: Optional[str] = None
    gateId: str
    blacklistOverride: bool = False


class RejectBody(BaseModel):
    reason: str = Field(min_length=1)


class CheckInBody(BaseModel):
    gateId: Optional[str] = None


class ForceCheckoutBody(BaseModel):
    reason: str = Field(min_length=1)


class VisitOut(BaseModel):
    id: str
    schoolId: str
    visitorName: str
    mobile: str
    visitorType: str
    purpose: str
    hostId: str
    livePhotoKey: str
    idType: str
    idNumber: Optional[str] = None
    idImageKey: Optional[str] = None
    vehicleNumber: Optional[str] = None
    accompanyingCount: Optional[int] = None
    notes: Optional[str] = None
    signatureKey: Optional[str] = None
    gateId: str
    registeredByUserId: str
    status: str
    rejectReason: Optional[str] = None
    decidedAt: Optional[str] = None
    decidedByUserId: Optional[str] = None
    passId: Optional[str] = None
    qrToken: Optional[str] = None
    timeIn: Optional[str] = None
    timeOut: Optional[str] = None
    gateInId: Optional[str] = None
    gateOutId: Optional[str] = None
    checkoutType: Optional[str] = None
    forceCheckoutReason: Optional[str] = None
    forceCheckoutByUserId: Optional[str] = None
    blacklistHit: bool = False
    blacklistId: Optional[str] = None
    blacklistOverrideByUserId: Optional[str] = None
    meetingDoneAt: Optional[str] = None
    createdAt: str
    updatedAt: str
    meta: Optional[dict] = None


# --- Passes ---


class PassScanBody(BaseModel):
    token: Optional[str] = None
    passId: Optional[str] = None
    action: Literal["check_in", "check_out"]
    gateId: Optional[str] = None


class PassOut(BaseModel):
    passId: str
    visitId: str
    schoolId: str
    visitorName: str
    photoUrl: Optional[str] = None
    hostId: str
    hostName: Optional[str] = None
    gateId: str
    gateName: Optional[str] = None
    status: str
    qrToken: Optional[str] = None
    issuedAt: Optional[str] = None
    expiresAt: Optional[str] = None
    revoked: bool = False
    meta: Optional[dict] = None


# --- Blacklist ---


class BlacklistCreate(BaseModel):
    name: str
    mobile: Optional[str] = None
    idType: Optional[IdType] = None
    idNumber: Optional[str] = None
    reason: str
    severity: Severity
    expiresOn: Optional[str] = None
    notes: Optional[str] = None
    photoUrl: Optional[str] = None


class BlacklistPatch(BaseModel):
    name: Optional[str] = None
    mobile: Optional[str] = None
    idType: Optional[IdType] = None
    idNumber: Optional[str] = None
    reason: Optional[str] = None
    severity: Optional[Severity] = None
    active: Optional[bool] = None
    expiresOn: Optional[str] = None
    notes: Optional[str] = None
    photoUrl: Optional[str] = None


class BlacklistMatchRequest(BaseModel):
    mobile: Optional[str] = None
    idType: Optional[IdType] = None
    idNumber: Optional[str] = None


class BlacklistOut(BaseModel):
    id: str
    schoolId: str
    name: str
    mobile: Optional[str] = None
    idType: Optional[str] = None
    idNumber: Optional[str] = None
    reason: str
    severity: str
    active: bool
    addedBy: str
    addedAt: str
    expiresOn: Optional[str] = None
    notes: Optional[str] = None
    photoUrl: Optional[str] = None


# --- Exports / reports / outbox ---


class ExportRequest(BaseModel):
    scope: Literal["history", "inside", "blacklist", "daily_gate_summary"]
    filters: dict[str, Any] = Field(default_factory=dict)


class MetaWrap(BaseModel):
    data: Any
    meta: dict = Field(default_factory=lambda: {"watermark": "DEMO"})
