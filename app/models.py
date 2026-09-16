"""Pydantic v2 models — field names locked to API contract."""
from __future__ import annotations

from datetime import date
from enum import Enum
from typing import Any, Literal, Optional

from pydantic import BaseModel, Field, field_validator, model_validator


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
    collector_live_photo = "collector_live_photo"
    pickup_list_photo = "pickup_list_photo"


class PickupRelation(str, Enum):
    parent = "parent"
    guardian = "guardian"
    sibling = "sibling"
    relative = "relative"
    other = "other"


class PickupReason(str, Enum):
    early = "early"
    sick = "sick"
    appointment = "appointment"
    other = "other"


class PickupStatus(str, Enum):
    Matching = "Matching"
    Released = "Released"
    BlockedNotAuthorized = "BlockedNotAuthorized"
    BlockedCustody = "BlockedCustody"
    ReleasedWithOverride = "ReleasedWithOverride"


class MatchMethod(str, Enum):
    mobile = "mobile"
    id = "id"
    manual_list_select = "manual_list_select"
    none = "none"


class CustodyFlag(str, Enum):
    none = "none"
    restricted = "restricted"
    court_order = "court_order"


class Weekday(str, Enum):
    mon = "mon"
    tue = "tue"
    wed = "wed"
    thu = "thu"
    fri = "fri"
    sat = "sat"
    sun = "sun"


class PolicyTrigger(str, Enum):
    outside_hours = "outside_hours"
    holiday = "holiday"
    both = "both"


# --- Auth ---


class LoginRequest(BaseModel):
    username: str
    password: str


class UserPublic(BaseModel):
    id: str
    schoolId: str
    role: Role
    staffId: Optional[str] = None
    gateIds: Optional[list[str]] = None
    displayName: str
    phone: Optional[str] = None
    email: Optional[str] = None


class LoginResponse(BaseModel):
    accessToken: str
    tokenType: str = "Bearer"
    expiresIn: int
    user: UserPublic
    meta: Optional[dict] = None


class MeResponse(UserPublic):
    meta: Optional[dict] = None


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
    meta: Optional[dict] = None


# --- Visits ---


class VisitCreate(BaseModel):
    visitorName: str = Field(min_length=1)
    mobile: str = Field(min_length=1)
    visitorType: VisitorType
    purpose: str = Field(min_length=1)
    hostId: str = Field(min_length=1)
    livePhotoKey: str = Field(min_length=1)
    idType: IdType
    idNumber: Optional[str] = None
    idImageKey: Optional[str] = None
    vehicleNumber: Optional[str] = None
    accompanyingCount: Optional[int] = Field(default=None, ge=0)
    notes: Optional[str] = None
    signatureKey: Optional[str] = None
    gateId: str = Field(min_length=1)
    blacklistOverride: bool = False

    @field_validator("visitorName", "purpose", "hostId", "livePhotoKey", "gateId")
    @classmethod
    def strip_required(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("must not be empty")
        return v


class ReasonBody(BaseModel):
    reason: str = Field(min_length=1)

    @field_validator("reason")
    @classmethod
    def reason_required(cls, v: str) -> str:
        v = (v or "").strip()
        if not v:
            raise ValueError("reason is required")
        return v


class ApproveBody(BaseModel):
    """Optional for in-hours Host Approve; required non-blank reason when afterHours (A6)."""

    reason: Optional[str] = None

    @field_validator("reason")
    @classmethod
    def strip_reason(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        v = v.strip()
        return v or None


class RejectBody(ReasonBody):
    pass


class CheckInBody(BaseModel):
    gateId: Optional[str] = None


class ForceCheckoutBody(ReasonBody):
    pass


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
    afterHours: bool = False
    policyTrigger: Optional[str] = None
    afterHoursEvaluatedAt: Optional[str] = None
    afterHoursApproveReason: Optional[str] = None
    createdAt: str
    updatedAt: str
    meta: Optional[dict] = None


# --- Passes ---


class PassScanBody(BaseModel):
    token: Optional[str] = None
    passId: Optional[str] = None
    action: Literal["check_in", "check_out"]
    gateId: Optional[str] = None

    @model_validator(mode="after")
    def token_or_pass_id(self) -> "PassScanBody":
        token = (self.token or "").strip() or None
        pass_id = (self.passId or "").strip() or None
        self.token = token
        self.passId = pass_id
        if not token and not pass_id:
            raise ValueError("token or passId required")
        return self


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
    scope: Literal[
        "history",
        "inside",
        "blacklist",
        "daily_gate_summary",
        "pickup_events",
        "pickup_lists",
    ]
    filters: dict[str, Any] = Field(default_factory=dict)
    purpose: Optional[str] = None


# --- Pickup & Custody (P2 §1) ---


class StudentCreate(BaseModel):
    studentId: Optional[str] = None
    name: str = Field(min_length=1)
    klass: str = Field(alias="class", min_length=1)
    section: str = Field(min_length=1)
    active: bool = True
    enrollmentEndedAt: Optional[str] = None
    legalHold: bool = False

    model_config = {"populate_by_name": True}

    @field_validator("name", "klass", "section")
    @classmethod
    def strip_required(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("must not be empty")
        return v


class StudentPatch(BaseModel):
    studentId: Optional[str] = None
    name: Optional[str] = None
    klass: Optional[str] = Field(default=None, alias="class")
    section: Optional[str] = None
    active: Optional[bool] = None
    enrollmentEndedAt: Optional[str] = None
    legalHold: Optional[bool] = None

    model_config = {"populate_by_name": True}


class StudentOut(BaseModel):
    id: str
    schoolId: str
    studentId: Optional[str] = None
    name: str
    klass: str = Field(alias="class")
    section: str
    active: bool
    enrollmentEndedAt: Optional[str] = None
    legalHold: bool = False
    createdAt: str
    updatedAt: str
    meta: Optional[dict] = None

    model_config = {"populate_by_name": True}


class AuthorizedPickupCreate(BaseModel):
    name: str = Field(min_length=1)
    relation: PickupRelation
    mobile: str = Field(min_length=1)
    idType: Optional[IdType] = None
    idNumber: Optional[str] = None
    idLast4: Optional[str] = None
    photoRef: Optional[str] = None
    active: bool = True
    effectiveFrom: Optional[str] = None
    effectiveTo: Optional[str] = None
    pickupConsentVersion: str = Field(min_length=1)
    pickupConsentAt: str = Field(min_length=1)

    @field_validator("name", "mobile", "pickupConsentVersion", "pickupConsentAt")
    @classmethod
    def strip_required(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("must not be empty")
        return v


class AuthorizedPickupPatch(BaseModel):
    name: Optional[str] = None
    relation: Optional[PickupRelation] = None
    mobile: Optional[str] = None
    idType: Optional[IdType] = None
    idNumber: Optional[str] = None
    idLast4: Optional[str] = None
    photoRef: Optional[str] = None
    active: Optional[bool] = None
    effectiveFrom: Optional[str] = None
    effectiveTo: Optional[str] = None
    pickupConsentVersion: Optional[str] = None
    pickupConsentAt: Optional[str] = None


class AuthorizedPickupOut(BaseModel):
    id: str
    schoolId: str
    studentId: str
    name: str
    relation: str
    mobile: str
    idType: Optional[str] = None
    idNumber: Optional[str] = None
    idLast4: Optional[str] = None
    photoRef: Optional[str] = None
    active: bool
    effectiveFrom: Optional[str] = None
    effectiveTo: Optional[str] = None
    blockedByCustody: bool = False
    pickupConsentVersion: str
    pickupConsentAt: str
    createdByUserId: str
    updatedByUserId: str
    createdAt: str
    updatedAt: str
    meta: Optional[dict] = None


class CustodyFlagPut(BaseModel):
    flag: CustodyFlag
    gateInstruction: str = Field(default="", max_length=280)
    blockedPersonIds: list[str] = Field(default_factory=list)
    allowedPersonIds: Optional[list[str]] = None

    @field_validator("gateInstruction")
    @classmethod
    def clip_instruction(cls, v: str) -> str:
        return (v or "").strip()


class CustodyFlagOut(BaseModel):
    studentId: str
    schoolId: str
    flag: str
    gateInstruction: str
    blockedPersonIds: Optional[list[str]] = None
    allowedPersonIds: Optional[list[str]] = None
    updatedByUserId: Optional[str] = None
    updatedAt: Optional[str] = None
    meta: Optional[dict] = None


class PickupCreate(BaseModel):
    studentId: str = Field(min_length=1)
    gateId: str = Field(min_length=1)
    pickupReason: PickupReason
    reasonOther: Optional[str] = None
    collectorPickupPersonId: Optional[str] = None
    collectorName: Optional[str] = None
    collectorMobile: Optional[str] = None
    idType: Optional[IdType] = None
    idNumber: Optional[str] = None
    idLast4: Optional[str] = None
    linkVisit: bool = False

    @model_validator(mode="after")
    def claim_or_match_key(self) -> "PickupCreate":
        person = (self.collectorPickupPersonId or "").strip() or None
        name = (self.collectorName or "").strip() or None
        mobile = (self.collectorMobile or "").strip() or None
        id_number = (self.idNumber or "").strip() or None
        last4 = (self.idLast4 or "").strip() or None
        self.collectorPickupPersonId = person
        self.collectorName = name
        self.collectorMobile = mobile
        self.idNumber = id_number
        self.idLast4 = last4
        if self.pickupReason == PickupReason.other and not (self.reasonOther or "").strip():
            raise ValueError("reasonOther is required when pickupReason is other")
        if not person and not name and not mobile and not id_number and not last4:
            raise ValueError(
                "claimed collector required: collectorPickupPersonId or collectorName or mobile/id"
            )
        return self


class PickupConsentBody(BaseModel):
    pickupConsentVersion: str = Field(min_length=1)
    pickupConsentAt: Optional[str] = None

    @field_validator("pickupConsentVersion")
    @classmethod
    def consent_version_required(cls, v: str) -> str:
        v = (v or "").strip()
        if not v:
            raise ValueError("pickupConsentVersion is required")
        return v


class PickupReleaseBody(BaseModel):
    collectorLivePhotoRef: str = Field(min_length=1)
    linkVisit: Optional[bool] = None

    @field_validator("collectorLivePhotoRef")
    @classmethod
    def photo_required(cls, v: str) -> str:
        v = (v or "").strip()
        if not v:
            raise ValueError("collectorLivePhotoRef is required")
        return v


class PickupOut(BaseModel):
    id: str
    schoolId: str
    gateId: str
    studentId: str
    collectorPickupPersonId: Optional[str] = None
    collectorName: str
    collectorMobile: str
    collectorRelation: Optional[str] = None
    matchMethod: Optional[str] = None
    pickupReason: str
    reasonOther: Optional[str] = None
    collectorLivePhotoRef: Optional[str] = None
    status: str
    custodyFlagSnapshot: str
    override: bool = False
    overrideByUserId: Optional[str] = None
    overrideReason: Optional[str] = None
    linkedVisitId: Optional[str] = None
    visitLinkFailed: Optional[bool] = None
    releasedAt: Optional[str] = None
    attemptedAt: str
    gateUserId: str
    pickupConsentAt: Optional[str] = None
    pickupConsentVersion: Optional[str] = None
    createdAt: str
    updatedAt: str
    meta: Optional[dict] = None


# --- Access Rules: hours + holidays (P2 after-hours A1–A2) ---


class CampusHoursRow(BaseModel):
    schoolId: Optional[str] = None
    timezone: str = "Asia/Kolkata"
    weekday: Weekday
    openTime: Optional[str] = None
    closeTime: Optional[str] = None
    closed: bool = False
    overnight: Optional[bool] = False
    updatedByUserId: Optional[str] = None
    updatedAt: Optional[str] = None


class HolidayCreate(BaseModel):
    date: str = Field(min_length=1)
    label: Optional[str] = None

    @field_validator("date")
    @classmethod
    def date_iso(cls, v: str) -> str:
        v = (v or "").strip()
        try:
            date.fromisoformat(v)
        except ValueError as e:
            raise ValueError("date must be YYYY-MM-DD") from e
        return v

    @field_validator("label")
    @classmethod
    def strip_label(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        v = v.strip()
        return v or None


class HolidayOut(BaseModel):
    id: str
    schoolId: str
    date: str
    label: Optional[str] = None
    createdByUserId: Optional[str] = None
    updatedByUserId: Optional[str] = None
    createdAt: Optional[str] = None
    updatedAt: Optional[str] = None
    meta: Optional[dict] = None


class MetaWrap(BaseModel):
    data: Any
    meta: dict = Field(default_factory=lambda: {"watermark": "DEMO"})
