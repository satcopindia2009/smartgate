import { BLAST_FIXTURE_STATE_KEY, LAST_BLAST_ID_KEY, SEED_BLAST_ID } from "./constants";
import type {
  BlastPreview,
  BlastRecipient,
  BlastTemplate,
  EmergencyBlast,
  LiveVisitor,
  SchoolBlastConfig,
} from "./types";

export const SEED_TEMPLATES: BlastTemplate[] = [
  {
    id: "tpl_evac_assembly",
    schoolId: "SCH-DEMO-01",
    name: "Evacuation — assembly ground",
    instruction:
      "Demo International School: Please proceed to the assembly ground now. Follow staff instructions. Do not leave campus until cleared.",
    channel: "sms",
    active: true,
    updatedByUserId: "U-SH",
    updatedAt: "2026-09-16T08:00:00+05:30",
  },
  {
    id: "tpl_shelter_in_place",
    schoolId: "SCH-DEMO-01",
    name: "Shelter in place",
    instruction:
      "Demo International School: Shelter in place. Stay where you are and await staff direction. Do not move between buildings.",
    channel: "sms",
    active: true,
    updatedByUserId: "U-SH",
    updatedAt: "2026-09-16T08:00:00+05:30",
  },
];

export function listActiveTemplates(templates: BlastTemplate[] = SEED_TEMPLATES): BlastTemplate[] {
  return templates.filter((t) => t.active);
}

export function seedBlastConfig(): SchoolBlastConfig {
  return {
    schoolId: "SCH-DEMO-01",
    emergencyBlastEnabled: true,
    blastStaffLaneEnabled: false,
    blastChannelsVisitor: ["sms"],
    blastChannelsStaff: ["in_app", "push"],
    updatedByUserId: "U-ADMIN",
    updatedAt: "2026-09-16T09:00:00+05:30",
  };
}

export function blastIdOf(blast?: Pick<EmergencyBlast, "blastId" | "blast_id"> | null): string {
  return blast?.blastId || blast?.blast_id || "";
}

export function rememberLastBlastId(id?: string | null): void {
  const value = String(id || "").trim();
  if (!value) return;
  try {
    sessionStorage.setItem(LAST_BLAST_ID_KEY, value);
  } catch {
    /* ignore */
  }
}

export function rememberedLastBlastId(): string {
  try {
    return sessionStorage.getItem(LAST_BLAST_ID_KEY) || SEED_BLAST_ID;
  } catch {
    return SEED_BLAST_ID;
  }
}

export function maskBlastMobile(mobile?: string | null): string {
  const digits = String(mobile || "").replace(/\D/g, "");
  const last4 = digits.slice(-4) || "0000";
  return `+91-9xxx-xx${last4}`;
}

export function recipientStatusClass(status?: string | null): string {
  if (status === "sent") return "blast-status sent";
  if (status === "failed") return "blast-status failed";
  if (status === "skipped_hold") return "blast-status hold";
  if (status === "skipped_no_mobile") return "blast-status skip";
  if (status === "queued" || status === "sending") return "blast-status queued";
  return "blast-status";
}

export function blastStatusClass(status?: string | null): string {
  if (status === "completed") return "status-pill status-approved";
  if (status === "partial") return "status-pill status-waiting";
  if (status === "failed") return "status-pill status-rejected";
  if (status === "pending_confirm") return "status-pill status-pending";
  return "status-pill";
}

export function countsLabel(blast?: EmergencyBlast | null): string {
  const c = blast?.counts;
  if (!c) return "—";
  const parts = [
    c.sent ? `${c.sent} sent` : null,
    c.failed ? `${c.failed} failed` : null,
    c.skipped_hold ? `${c.skipped_hold} WA hold` : null,
    c.skipped_no_mobile ? `${c.skipped_no_mobile} no mobile` : null,
    c.queued ? `${c.queued} queued` : null,
  ].filter(Boolean);
  return parts.join(" · ") || "—";
}

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T;
}

function countsFor(recipients: BlastRecipient[]) {
  const counts = {
    queued: 0,
    sent: 0,
    failed: 0,
    skipped_no_mobile: 0,
    skipped_hold: 0,
  };
  for (const row of recipients) {
    const status = row.status as keyof typeof counts;
    if (status in counts) counts[status] += 1;
  }
  return counts;
}

function deriveStatus(recipients: BlastRecipient[]): EmergencyBlast["status"] {
  if (!recipients.length) return "completed";
  const statuses = recipients.map((r) => r.status);
  const ok = new Set(["sent", "skipped_no_mobile", "skipped_hold"]);
  if (statuses.every((s) => ok.has(String(s)))) return "completed";
  if (statuses.every((s) => s === "failed")) return "failed";
  if (statuses.some((s) => s === "failed")) return "partial";
  return "sending";
}

export function seedEmergencyBlast(): EmergencyBlast {
  const blastId = SEED_BLAST_ID;
  const recipients: BlastRecipient[] = [
    {
      blastId,
      blast_id: blastId,
      visitId: "vis_p7k88",
      mobileMasked: "+91-9xxx-xx4442",
      channel: "sms",
      status: "sent",
      providerMessageId: "mock_sms_001",
      attemptedAt: "2026-09-16T11:04:20+05:30",
      errorCode: null,
    },
    {
      blastId,
      blast_id: blastId,
      visitId: "vis_p7k88",
      mobileMasked: "+91-9xxx-xx4442",
      channel: "whatsapp",
      status: "skipped_hold",
      providerMessageId: null,
      attemptedAt: "2026-09-16T11:04:20+05:30",
      errorCode: "WA_HOLD",
    },
    {
      blastId,
      blast_id: blastId,
      visitId: "vis_p7m12",
      mobileMasked: "+91-9xxx-xx2018",
      channel: "sms",
      status: "sent",
      providerMessageId: "mock_sms_002",
      attemptedAt: "2026-09-16T11:04:21+05:30",
      errorCode: null,
    },
    {
      blastId,
      blast_id: blastId,
      visitId: "vis_p7m19",
      mobileMasked: "+91-9xxx-xx1187",
      channel: "sms",
      status: "sent",
      providerMessageId: "mock_sms_003",
      attemptedAt: "2026-09-16T11:04:21+05:30",
      errorCode: null,
    },
    {
      blastId,
      blast_id: blastId,
      visitId: "vis_p7m22",
      mobileMasked: "+91-9xxx-xx3340",
      channel: "sms",
      status: "sent",
      providerMessageId: "mock_sms_004",
      attemptedAt: "2026-09-16T11:04:22+05:30",
      errorCode: null,
    },
    {
      blastId,
      blast_id: blastId,
      visitId: "vis_p7m28",
      mobileMasked: "+91-9xxx-xx5501",
      channel: "sms",
      status: "sent",
      providerMessageId: "mock_sms_005",
      attemptedAt: "2026-09-16T11:04:22+05:30",
      errorCode: null,
    },
    {
      blastId,
      blast_id: blastId,
      visitId: "vis_p7m31",
      mobileMasked: "+91-9xxx-xx7799",
      channel: "sms",
      status: "failed",
      providerMessageId: null,
      attemptedAt: "2026-09-16T11:04:23+05:30",
      errorCode: "MOCK_PROVIDER_UNREACHABLE",
    },
  ];
  return {
    blastId,
    blast_id: blastId,
    schoolId: "SCH-DEMO-01",
    triggeredByUserId: "U-SH",
    triggeredAt: "2026-09-16T11:04:12+05:30",
    templateId: "tpl_evac_assembly",
    instruction:
      "Demo International School: Please proceed to the assembly ground now. Follow staff instructions. Do not leave campus until cleared.",
    insideCount: 6,
    recipientCount: 6,
    status: "partial",
    confirmAt: "2026-09-16T11:04:18+05:30",
    recipients,
    counts: countsFor(recipients),
  };
}

export function previewFromInside(
  inside: LiveVisitor[],
  template?: BlastTemplate | null,
): BlastPreview {
  return {
    insideCount: inside.length,
    templateId: template?.id ?? null,
    instruction: template?.instruction ?? null,
    instructionPreview: template?.instruction ?? null,
    emergencyBlastEnabled: true,
    channelsSummary: {
      sms: { planned: inside.length, skipped_no_mobile: 0 },
      whatsapp: { planned: 0, hold: true, skipped_hold: 0 },
      visitor: ["sms"],
      staffLaneEnabled: false,
      whatsappHold: true,
      staff: { enabled: false },
    },
  };
}

interface BlastFixtureSession {
  blasts: EmergencyBlast[];
  seq: number;
}

let session: BlastFixtureSession | null = null;

export function getBlastFixtureSession(): BlastFixtureSession {
  if (session) return session;
  try {
    const raw = sessionStorage.getItem(BLAST_FIXTURE_STATE_KEY);
    if (raw) {
      session = JSON.parse(raw) as BlastFixtureSession;
      return session;
    }
  } catch {
    /* ignore */
  }
  session = { blasts: [seedEmergencyBlast()], seq: 4 };
  return session;
}

function persistBlastFixtures(): void {
  if (!session) return;
  try {
    sessionStorage.setItem(BLAST_FIXTURE_STATE_KEY, JSON.stringify(session));
  } catch {
    /* ignore */
  }
}

export function getBlastLocal(blastId: string): EmergencyBlast | null {
  const sess = getBlastFixtureSession();
  const found = sess.blasts.find((b) => blastIdOf(b) === blastId);
  return found ? clone(found) : blastId === SEED_BLAST_ID ? seedEmergencyBlast() : null;
}

export function latestBlastLocal(): EmergencyBlast | null {
  const sess = getBlastFixtureSession();
  return sess.blasts[0] ? clone(sess.blasts[0]) : seedEmergencyBlast();
}

export function confirmBlastLocal(
  inside: LiveVisitor[],
  template: BlastTemplate,
  instruction: string,
  triggeredByUserId: string,
): EmergencyBlast {
  const sess = getBlastFixtureSession();
  sess.seq += 1;
  const blastId = `B-20260916-${String(sess.seq).padStart(2, "0")}`;
  const now = new Date().toISOString();
  const recipients: BlastRecipient[] = inside.map((v, i) => ({
    blastId,
    blast_id: blastId,
    visitId: v.visitId,
    mobileMasked: maskBlastMobile(v.mobile),
    channel: template.channel === "whatsapp" ? "whatsapp" : "sms",
    status: template.channel === "whatsapp" ? "skipped_hold" : "sent",
    providerMessageId:
      template.channel === "whatsapp" ? null : `mock-sms-${String(i + 1).padStart(3, "0")}`,
    attemptedAt: now,
    errorCode: template.channel === "whatsapp" ? "WA_HOLD" : null,
  }));
  const blast: EmergencyBlast = {
    blastId,
    blast_id: blastId,
    schoolId: "SCH-DEMO-01",
    triggeredByUserId,
    triggeredAt: now,
    templateId: template.id,
    instruction,
    insideCount: inside.length,
    recipientCount: recipients.length,
    status: deriveStatus(recipients),
    confirmAt: now,
    recipients,
    counts: countsFor(recipients),
  };
  sess.blasts = [blast, ...sess.blasts];
  persistBlastFixtures();
  rememberLastBlastId(blastId);
  return clone(blast);
}

export function retryFailedLocal(blastId: string): EmergencyBlast | null {
  const sess = getBlastFixtureSession();
  const blast = sess.blasts.find((b) => blastIdOf(b) === blastId);
  if (!blast) return null;
  const now = new Date().toISOString();
  blast.recipients = (blast.recipients || []).map((row) => {
    if (row.status !== "failed") return row;
    return {
      ...row,
      status: "sent",
      providerMessageId: `mock-sms-retry-${Date.now().toString(16).slice(-4)}`,
      attemptedAt: now,
      errorCode: null,
    };
  });
  blast.counts = countsFor(blast.recipients);
  blast.status = deriveStatus(blast.recipients);
  persistBlastFixtures();
  rememberLastBlastId(blastId);
  return clone(blast);
}
