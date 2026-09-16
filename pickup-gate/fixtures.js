/**
 * Locked P6 + F2 fixtures (used when the mock tunnel is down).
 * Aarav Mehta 5-B · Neha (Mother) + Rohan (Uncle).
 * Kabir Singh 4-A · court_order · Neha Mehta (Mother) allow · claimed father block.
 * Mother is Neha Mehta — not Priya Singh / not visitor Priya.
 */
window.VMS_PICKUP_FIXTURES = {
  students: [
    {
      id: "STU-AARAV",
      schoolId: "SCH-DEMO-01",
      studentId: "5B-17",
      name: "Aarav Mehta",
      class: "5",
      section: "B",
      active: true,
      legalHold: false,
    },
    {
      id: "STU-AARAV-PATEL",
      schoolId: "SCH-DEMO-01",
      studentId: "3A-22",
      name: "Aarav Patel",
      class: "3",
      section: "A",
      active: true,
      legalHold: false,
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
    },
  ],
  authorized: {
    "STU-AARAV": [
      {
        id: "APP-NEHA",
        studentId: "STU-AARAV",
        name: "Neha Mehta",
        relation: "parent",
        relationLabel: "Mother",
        mobile: "9822011001",
        idLast4: "8821",
        active: true,
        blockedByCustody: false,
      },
      {
        id: "APP-ROHAN",
        studentId: "STU-AARAV",
        name: "Rohan Mehta",
        relation: "relative",
        relationLabel: "Uncle",
        mobile: "98220177320",
        idLast4: "4409",
        active: true,
        effectiveFrom: "2026-09-01",
        effectiveTo: "2026-12-31",
        blockedByCustody: false,
      },
    ],
    "STU-AARAV-PATEL": [
      {
        id: "APP-SNEHA",
        studentId: "STU-AARAV-PATEL",
        name: "Sneha Patel",
        relation: "parent",
        relationLabel: "Mother",
        mobile: "9822055660",
        idLast4: "3340",
        active: true,
        blockedByCustody: false,
      },
    ],
    "STU-KABIR": [
      {
        id: "APP-NEHA-K",
        studentId: "STU-KABIR",
        name: "Neha Mehta",
        relation: "parent",
        relationLabel: "Mother",
        mobile: "9822011001",
        idLast4: "8821",
        active: true,
        blockedByCustody: false,
      },
      {
        id: "APP-CLAIM-FATHER",
        studentId: "STU-KABIR",
        name: "Claimed father (demo)",
        relation: "other",
        relationLabel: "Not allowed",
        mobile: "",
        idLast4: "",
        active: true,
        blockedByCustody: true,
        demoBlock: true,
      },
    ],
  },
  custody: {
    "STU-AARAV": {
      studentId: "STU-AARAV",
      flag: "none",
      gateInstruction: "",
      allowedPersonIds: null,
      blockedPersonIds: null,
    },
    "STU-AARAV-PATEL": {
      studentId: "STU-AARAV-PATEL",
      flag: "none",
      gateInstruction: "",
      allowedPersonIds: null,
      blockedPersonIds: null,
    },
    "STU-KABIR": {
      studentId: "STU-KABIR",
      flag: "court_order",
      gateInstruction: "Release only to Neha Mehta (Mother). Block all others.",
      allowedPersonIds: ["APP-NEHA-K"],
      blockedPersonIds: ["APP-CLAIM-FATHER"],
    },
  },
};
