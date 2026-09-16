import { PICKUP_CONSENT_VERSION, PICKUP_FIXTURE_STATE_KEY } from "./constants";
import type {
  AuthorizedPickupPerson,
  CustodyFlagRecord,
  PickupEvent,
  Student,
} from "./types";

export interface PickupFixtureSession {
  students: Student[];
  people: Record<string, AuthorizedPickupPerson[]>;
  custody: Record<string, CustodyFlagRecord>;
  events: PickupEvent[];
}

let session: PickupFixtureSession | null = null;

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T;
}

function seedStudents(): Student[] {
  return [
    {
      id: "STU-AARAV",
      schoolId: "SCH-DEMO-01",
      studentId: "5B-17",
      name: "Aarav Mehta",
      class: "5",
      section: "B",
      active: true,
      legalHold: false,
      createdAt: "2026-06-01T09:00:00+05:30",
      updatedAt: "2026-06-01T09:00:00+05:30",
    },
    {
      id: "STU-KABIR",
      schoolId: "SCH-DEMO-01",
      studentId: "4A-09",
      name: "Kabir Singh",
      class: "4",
      section: "A",
      active: true,
      legalHold: true,
      createdAt: "2026-06-01T09:00:00+05:30",
      updatedAt: "2026-09-10T11:00:00+05:30",
    },
  ];
}

function seedPeople(): Record<string, AuthorizedPickupPerson[]> {
  return {
    "STU-AARAV": [
      {
        id: "APP-NEHA",
        schoolId: "SCH-DEMO-01",
        studentId: "STU-AARAV",
        name: "Neha Mehta",
        relation: "parent",
        mobile: "9822041201",
        idType: "DL",
        idLast4: "1201",
        active: true,
        blockedByCustody: false,
        pickupConsentVersion: PICKUP_CONSENT_VERSION,
        pickupConsentAt: "2026-06-15T10:00:00+05:30",
        createdByUserId: "U-ADMIN",
        updatedByUserId: "U-ADMIN",
        createdAt: "2026-06-15T10:00:00+05:30",
        updatedAt: "2026-06-15T10:00:00+05:30",
      },
      {
        id: "APP-ROHAN",
        schoolId: "SCH-DEMO-01",
        studentId: "STU-AARAV",
        name: "Rohan Mehta",
        relation: "relative",
        mobile: "9822077320",
        idType: "Other",
        idLast4: "7320",
        active: true,
        effectiveFrom: "2026-09-01",
        effectiveTo: "2026-12-31",
        blockedByCustody: false,
        pickupConsentVersion: PICKUP_CONSENT_VERSION,
        pickupConsentAt: "2026-06-15T10:00:00+05:30",
        createdByUserId: "U-ADMIN",
        updatedByUserId: "U-ADMIN",
        createdAt: "2026-06-15T10:00:00+05:30",
        updatedAt: "2026-06-15T10:00:00+05:30",
      },
    ],
    "STU-KABIR": [
      {
        id: "APP-KABIR-NEHA",
        schoolId: "SCH-DEMO-01",
        studentId: "STU-KABIR",
        name: "Neha Mehta",
        relation: "parent",
        mobile: "9822041201",
        idType: "DL",
        idLast4: "1201",
        active: true,
        blockedByCustody: false,
        pickupConsentVersion: PICKUP_CONSENT_VERSION,
        pickupConsentAt: "2026-06-15T10:00:00+05:30",
        createdByUserId: "U-SH",
        updatedByUserId: "U-SH",
        createdAt: "2026-06-15T10:00:00+05:30",
        updatedAt: "2026-06-15T10:00:00+05:30",
      },
    ],
  };
}

function seedCustody(): Record<string, CustodyFlagRecord> {
  return {
    "STU-AARAV": {
      studentId: "STU-AARAV",
      schoolId: "SCH-DEMO-01",
      flag: "none",
      gateInstruction: "",
      blockedPersonIds: [],
      allowedPersonIds: null,
      updatedByUserId: "U-ADMIN",
      updatedAt: "2026-06-15T10:05:00+05:30",
    },
    "STU-KABIR": {
      studentId: "STU-KABIR",
      schoolId: "SCH-DEMO-01",
      flag: "court_order",
      gateInstruction: "Release only to Neha Mehta (Mother). Block all others.",
      blockedPersonIds: [],
      allowedPersonIds: ["APP-KABIR-NEHA"],
      updatedByUserId: "U-SH",
      updatedAt: "2026-09-10T11:00:00+05:30",
    },
  };
}

function seedEvents(): PickupEvent[] {
  return [
    {
      id: "PK-FX-001",
      schoolId: "SCH-DEMO-01",
      gateId: "G-MAIN",
      studentId: "STU-AARAV",
      collectorPickupPersonId: "APP-ROHAN",
      collectorName: "Rohan Mehta",
      collectorMobile: "9822077320",
      collectorRelation: "relative",
      matchMethod: "manual_list_select",
      pickupReason: "appointment",
      collectorLivePhotoRef: "media/live_photo/rohan-mehta",
      status: "Released",
      custodyFlagSnapshot: "none",
      override: false,
      releasedAt: "2026-09-16T14:14:00+05:30",
      attemptedAt: "2026-09-16T14:14:00+05:30",
      gateUserId: "U-GATE",
    },
    {
      id: "PK-FX-002",
      schoolId: "SCH-DEMO-01",
      gateId: "G-MAIN",
      studentId: "STU-KABIR",
      collectorName: "Claimed father",
      collectorMobile: "9999990001",
      collectorRelation: "other",
      matchMethod: "none",
      pickupReason: "early",
      status: "BlockedCustody",
      custodyFlagSnapshot: "court_order",
      override: false,
      attemptedAt: "2026-09-16T13:52:00+05:30",
      gateUserId: "U-GATE",
    },
    {
      id: "PK-FX-003",
      schoolId: "SCH-DEMO-01",
      gateId: "G-PED",
      studentId: "STU-AARAV",
      collectorName: "Unknown person",
      collectorMobile: "9999990002",
      collectorRelation: null,
      matchMethod: "none",
      pickupReason: "early",
      status: "BlockedNotAuthorized",
      custodyFlagSnapshot: "none",
      override: false,
      attemptedAt: "2026-09-15T11:08:00+05:30",
      gateUserId: "U-GATE",
    },
    {
      id: "PK-FX-004",
      schoolId: "SCH-DEMO-01",
      gateId: "G-MAIN",
      studentId: "STU-AARAV",
      collectorPickupPersonId: "APP-NEHA",
      collectorName: "Neha Mehta",
      collectorMobile: "9822041201",
      collectorRelation: "parent",
      matchMethod: "mobile",
      pickupReason: "sick",
      collectorLivePhotoRef: "media/live_photo/neha-mehta",
      status: "Released",
      custodyFlagSnapshot: "none",
      override: false,
      releasedAt: "2026-09-14T15:40:00+05:30",
      attemptedAt: "2026-09-14T15:40:00+05:30",
      gateUserId: "U-GATE",
    },
    {
      id: "PK-FX-005",
      schoolId: "SCH-DEMO-01",
      gateId: "G-MAIN",
      studentId: "STU-KABIR",
      collectorName: "Emergency contact (override)",
      collectorMobile: "9822099000",
      collectorRelation: "guardian",
      matchMethod: "none",
      pickupReason: "other",
      reasonOther: "Medical emergency",
      collectorLivePhotoRef: "media/live_photo/override",
      status: "ReleasedWithOverride",
      custodyFlagSnapshot: "court_order",
      override: true,
      overrideByUserId: "U-SH",
      overrideReason: "Security Head approved emergency release",
      releasedAt: "2026-09-12T10:22:00+05:30",
      attemptedAt: "2026-09-12T10:22:00+05:30",
      gateUserId: "U-GATE",
    },
  ];
}

function persist() {
  if (!session) return;
  try {
    sessionStorage.setItem(PICKUP_FIXTURE_STATE_KEY, JSON.stringify(session));
  } catch {
    /* ignore quota */
  }
}

export function getPickupFixtureSession(): PickupFixtureSession {
  if (session) return session;
  try {
    const raw = sessionStorage.getItem(PICKUP_FIXTURE_STATE_KEY);
    if (raw) {
      session = JSON.parse(raw) as PickupFixtureSession;
      return session;
    }
  } catch {
    /* ignore */
  }
  session = {
    students: clone(seedStudents()),
    people: clone(seedPeople()),
    custody: clone(seedCustody()),
    events: clone(seedEvents()),
  };
  return session;
}

export function emptyCustody(studentId: string): CustodyFlagRecord {
  return {
    studentId,
    flag: "none",
    gateInstruction: "",
    blockedPersonIds: [],
    allowedPersonIds: null,
  };
}

export function upsertPickupPersonLocal(person: AuthorizedPickupPerson, isNew: boolean) {
  const sess = getPickupFixtureSession();
  const rows = sess.people[person.studentId] || [];
  if (isNew) {
    sess.people[person.studentId] = [...rows, person];
  } else {
    sess.people[person.studentId] = rows.map((row) => (row.id === person.id ? person : row));
  }
  persist();
}

export function saveCustodyLocal(record: CustodyFlagRecord) {
  const sess = getPickupFixtureSession();
  sess.custody[record.studentId] = record;
  persist();
}
