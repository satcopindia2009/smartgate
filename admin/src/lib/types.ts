export type Role = "gate" | "host" | "admin" | "security_head";
export type DataSource = "api" | "fixtures";
export type VisitorType = "Parent" | "Vendor" | "Guest" | "Official" | "Alumni";
export type IdType = "Aadhaar" | "DL" | "Voter" | "Passport" | "Other";
export type Severity = "Block" | "Alert";
export type Weekday = "mon" | "tue" | "wed" | "thu" | "fri" | "sat" | "sun";
export type PolicyTrigger = "outside_hours" | "holiday" | "both";

export interface AuthUser {
  id: string;
  schoolId: string;
  role: Role;
  staffId?: string | null;
  gateIds?: string[] | null;
  displayName: string;
  phone?: string | null;
  email?: string | null;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
  meta?: { watermark?: string };
}

export interface Gate {
  id: string;
  schoolId?: string;
  name: string;
  active?: boolean;
}

export interface Staff {
  id: string;
  schoolId?: string;
  name: string;
  roleTitle?: string;
  role?: string;
  mobile?: string | null;
  userId?: string | null;
  active?: boolean;
}

export interface ApiVisit {
  id: string;
  schoolId?: string;
  visitorName: string;
  mobile: string;
  visitorType: VisitorType | string;
  purpose: string;
  hostId: string;
  livePhotoKey?: string;
  gateId?: string;
  gateInId?: string | null;
  gateOutId?: string | null;
  timeIn?: string | null;
  timeOut?: string | null;
  passId?: string | null;
  status: string;
  rejectReason?: string | null;
  decidedAt?: string | null;
  checkoutType?: string | null;
  forceCheckoutReason?: string | null;
  blacklistHit?: boolean;
  blacklistId?: string | null;
  notes?: string | null;
  registeredByUserId?: string | null;
  overdue?: boolean;
  createdAt?: string;
  updatedAt?: string;
  afterHours?: boolean;
  policyTrigger?: PolicyTrigger | string | null;
  afterHoursEvaluatedAt?: string | null;
  afterHoursApproveReason?: string | null;
  escortRequired?: boolean;
  allowedZones?: string[];
  escortStaffId?: string | null;
  escortName?: string | null;
  escortSuggestedByHost?: string | null;
  escortWaived?: boolean;
  escortWaiveReason?: string | null;
  escortClearedAt?: string | null;
}

export interface LiveVisitor {
  visitId: string;
  name: string;
  photoUrl?: string | null;
  type: string;
  mobile: string;
  hostId: string;
  host: string;
  purpose: string;
  gate: string;
  gateId?: string;
  timeIn: string;
  passId: string;
  status: string;
  flags: string[];
  blacklistHit: boolean;
  blacklistId?: string | null;
  afterHours?: boolean;
  policyTrigger?: PolicyTrigger | string | null;
  afterHoursEvaluatedAt?: string | null;
  escortRequired?: boolean;
  allowedZones?: string[];
  escortStaffId?: string | null;
  escortName?: string | null;
  escortWaived?: boolean;
}

export interface HistoryVisit {
  visitId: string;
  name: string;
  photoUrl?: string | null;
  mobile: string;
  type: string;
  purpose: string;
  host: string;
  decision: string;
  decisionReason?: string | null;
  decisionAt?: string | null;
  gateIn?: string | null;
  timeIn?: string | null;
  gateOut?: string | null;
  timeOut?: string | null;
  checkoutType: string;
  durationMin?: number | null;
  blacklistHit: boolean;
  registeredBy?: string;
  notes?: string;
  passId?: string | null;
  createdAt?: string | null;
  afterHours?: boolean;
  policyTrigger?: PolicyTrigger | string | null;
  afterHoursEvaluatedAt?: string | null;
  afterHoursApproveReason?: string | null;
  hostId?: string;
  escortRequired?: boolean;
  allowedZones?: string[];
  escortStaffId?: string | null;
  escortName?: string | null;
  escortWaived?: boolean;
}

export interface BlacklistEntry {
  id: string;
  name: string;
  mobile?: string | null;
  idType?: string | null;
  idNumber?: string | null;
  reason: string;
  severity: Severity | string;
  active: boolean;
  addedBy: string;
  addedAt: string;
  expiresOn?: string | null;
  notes?: string | null;
  photoUrl?: string | null;
}

export interface GateReport {
  gate: string;
  gateId?: string;
  checkIns: number;
  checkOuts: number;
  stillInside: number;
  rejects: number;
  blacklistHits: number;
  forceCheckouts: number;
  uniqueMobiles: number;
  medianApprovalSec?: number | null;
  peakInside?: number | null;
}

export interface FixturesFile {
  meta: {
    school: string;
    timezone: string;
    generated: string;
    watermark: string;
    overdueHoursDefault: number;
    gates: string[];
    visitorTypes: string[];
    idTypes: string[];
    roles: { admin: string; securityHead: string };
  };
  staff: { id: string; name: string; role: string }[];
  blacklist: BlacklistEntry[];
  inside: LiveVisitor[];
  history: HistoryVisit[];
  reportsTodayByGate: GateReport[];
  csvScopes?: string[];
  auditLog?: unknown[];
  campusHours?: CampusHoursRow[];
  holidays?: HolidayEntry[];
  zones?: ZoneLabel[];
  escortRules?: EscortZoneRule[];
}

export interface CampusHoursRow {
  schoolId?: string | null;
  timezone?: string;
  weekday: Weekday;
  openTime?: string | null;
  closeTime?: string | null;
  closed: boolean;
  overnight?: boolean | null;
  updatedByUserId?: string | null;
  updatedAt?: string | null;
}

export interface HolidayEntry {
  id: string;
  schoolId?: string;
  date: string;
  label?: string | null;
  createdByUserId?: string | null;
  updatedByUserId?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type ZoneKey =
  | "reception"
  | "admin"
  | "classroom"
  | "sports"
  | "lab"
  | "restricted"
  | "parking";

export interface ZoneLabel {
  key: ZoneKey | string;
  label: string;
  schoolId?: string;
  updatedByUserId?: string | null;
  updatedAt?: string | null;
}

export interface EscortZoneRule {
  schoolId?: string;
  visitorType: string;
  escortRequired: boolean;
  allowedZones: string[];
  updatedByUserId?: string | null;
  updatedAt?: string | null;
}

export interface ApiList<T> {
  data: T[];
  meta?: { watermark?: string };
}

export type PickupRelation = "parent" | "guardian" | "sibling" | "relative" | "other";
export type CustodyFlagValue = "none" | "restricted" | "court_order";
export type PickupEventStatus =
  | "Draft"
  | "Matching"
  | "Released"
  | "BlockedNotAuthorized"
  | "BlockedCustody"
  | "ReleasedWithOverride";

export interface Student {
  id: string;
  schoolId?: string;
  studentId?: string | null;
  name: string;
  class: string;
  section: string;
  active: boolean;
  enrollmentEndedAt?: string | null;
  legalHold?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface AuthorizedPickupPerson {
  id: string;
  schoolId?: string;
  studentId: string;
  name: string;
  relation: PickupRelation | string;
  mobile: string;
  idType?: string | null;
  idNumber?: string | null;
  idLast4?: string | null;
  photoRef?: string | null;
  active: boolean;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  blockedByCustody?: boolean;
  pickupConsentVersion?: string;
  pickupConsentAt?: string;
  createdByUserId?: string | null;
  updatedByUserId?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface CustodyFlagRecord {
  studentId: string;
  schoolId?: string;
  flag: CustodyFlagValue | string;
  gateInstruction: string;
  blockedPersonIds?: string[];
  allowedPersonIds?: string[] | null;
  updatedByUserId?: string | null;
  updatedAt?: string;
  meta?: { watermark?: string };
}

export interface PickupEvent {
  id: string;
  schoolId?: string;
  gateId: string;
  studentId: string;
  collectorPickupPersonId?: string | null;
  collectorName: string;
  collectorMobile: string;
  collectorRelation?: string | null;
  matchMethod?: string | null;
  pickupReason?: string;
  reasonOther?: string | null;
  collectorLivePhotoRef?: string | null;
  status: PickupEventStatus | string;
  custodyFlagSnapshot?: string;
  override?: boolean;
  overrideByUserId?: string | null;
  overrideReason?: string | null;
  linkedVisitId?: string | null;
  visitLinkFailed?: boolean | null;
  releasedAt?: string | null;
  attemptedAt: string;
  gateUserId?: string | null;
  pickupConsentAt?: string | null;
  pickupConsentVersion?: string | null;
  createdAt?: string;
  updatedAt?: string;
}
