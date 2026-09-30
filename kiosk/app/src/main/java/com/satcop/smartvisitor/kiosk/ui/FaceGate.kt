package com.satcop.smartvisitor.kiosk.ui

/**
 * Face-login hard gate (1059). Pure state machine — no Android, unit tested.
 *
 *  SIGNED_OUT --password ok--> FACE_PENDING --face ok (server face_verified token)--> VERIFIED
 *  SIGNED_OUT --face-only login ok (server face_verified token)------------------> VERIFIED
 *  back / logout / cold start / process restore ---------------------------------> SIGNED_OUT
 *  server 403 FACE_REQUIRED while VERIFIED --------------------------------------> FACE_PENDING
 *
 * Only VERIFIED may compose any role screen (Gate/Host/Guard) or fetch data.
 */
enum class GateStage { SIGNED_OUT, FACE_PENDING, VERIFIED }

object FaceGateMachine {
    val initial: GateStage = GateStage.SIGNED_OUT

    /** Password accepted: NEVER grants access by itself. */
    fun onPasswordLogin(s: GateStage): GateStage = GateStage.FACE_PENDING

    /** [serverVerified] must be the `faceVerified` flag of the token returned by the face-verify step. */
    fun onFaceResult(s: GateStage, serverVerified: Boolean): GateStage =
        if (serverVerified) GateStage.VERIFIED else s

    /** Back from the face screen never reveals the app: it drops the half-open session. */
    fun onBack(s: GateStage): GateStage = when (s) {
        GateStage.FACE_PENDING -> GateStage.SIGNED_OUT
        else -> s
    }

    fun onLogout(@Suppress("UNUSED_PARAMETER") s: GateStage): GateStage = GateStage.SIGNED_OUT

    fun onColdStart(): GateStage = GateStage.SIGNED_OUT

    fun onServerFaceRequired(s: GateStage): GateStage =
        if (s == GateStage.VERIFIED) GateStage.FACE_PENDING else s

    fun canShowData(s: GateStage): Boolean = s == GateStage.VERIFIED
}
