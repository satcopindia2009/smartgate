package com.satcop.smartvisitor.kiosk.data.api

/**
 * Process-wide JWT session shared by Gate/Host (LiveVisitorApi) and Guard Patrol
 * (LiveGuardPatrolApi) so one login routes all three roles in the 1-role APK.
 */
object AppAuth {
    val session: AuthSession = AuthSession()
}
