package com.satcop.smartvisitor.kiosk.data.fixture

import com.satcop.smartvisitor.kiosk.data.model.AuthorizedPickup
import com.satcop.smartvisitor.kiosk.data.model.PickupOut
import com.satcop.smartvisitor.kiosk.data.model.PickupReasons
import com.satcop.smartvisitor.kiosk.data.model.StudentOut
import java.util.UUID

/**
 * Non-Pranay fixture students only. SCH-PRANAY-01 must use live /v1/students.
 */
object PickupFixtures {
    val students: List<StudentOut> = listOf(
        StudentOut(
            id = "STU-DEMO-S-001",
            schoolId = DemoFixtures.SCHOOL_ID,
            studentId = "D-S-001",
            name = "Aarav Mehta",
            className = "2",
            section = "A",
            active = true,
        ),
        StudentOut(
            id = "STU-DEMO-S-002",
            schoolId = DemoFixtures.SCHOOL_ID,
            studentId = "D-S-002",
            name = "Diya Nair",
            className = "4",
            section = "C",
            active = true,
        ),
    )

    private val authorized: Map<String, List<AuthorizedPickup>> = mapOf(
        "STU-DEMO-S-001" to listOf(
            AuthorizedPickup(
                id = "AP-DEMO-001-1",
                studentId = "STU-DEMO-S-001",
                schoolId = DemoFixtures.SCHOOL_ID,
                name = "Rohit Mehta",
                relation = "parent",
                mobile = "9876501101",
                active = true,
            ),
        ),
        "STU-DEMO-S-002" to listOf(
            AuthorizedPickup(
                id = "AP-DEMO-002-1",
                studentId = "STU-DEMO-S-002",
                schoolId = DemoFixtures.SCHOOL_ID,
                name = "Anjali Nair",
                relation = "parent",
                mobile = "9876502201",
                active = true,
            ),
        ),
    )

    fun search(q: String?): List<StudentOut> {
        val needle = q?.trim()?.lowercase().orEmpty()
        if (needle.isEmpty()) return students
        return students.filter { row ->
            row.name.lowercase().contains(needle) ||
                row.id.lowercase().contains(needle) ||
                row.studentId.orEmpty().lowercase().contains(needle) ||
                row.classSection().lowercase().contains(needle)
        }
    }

    fun authorizedFor(studentId: String): List<AuthorizedPickup> =
        authorized[studentId].orEmpty()

    fun start(
        studentId: String,
        gateId: String,
        pickupReason: String,
        collectorId: String?,
        collectorName: String?,
        collectorMobile: String?,
        reasonOther: String?,
    ): PickupOut {
        val person = authorizedFor(studentId).firstOrNull { it.id == collectorId }
        return PickupOut(
            id = "PK-LOCAL-${UUID.randomUUID().toString().take(8)}",
            schoolId = DemoFixtures.SCHOOL_ID,
            gateId = gateId,
            studentId = studentId,
            collectorPickupPersonId = person?.id ?: collectorId,
            collectorName = person?.name ?: collectorName,
            collectorMobile = person?.mobile ?: collectorMobile,
            collectorRelation = person?.relation,
            matchMethod = if (person != null) "manual_list_select" else "typed",
            pickupReason = pickupReason,
            reasonOther = reasonOther,
            status = "Matching",
            custodyFlagSnapshot = "none",
            meta = DemoFixtures.meta,
        )
    }

    fun consent(current: PickupOut): PickupOut = current.copy(
        pickupConsentVersion = PickupReasons.CONSENT_VERSION,
        pickupConsentAt = "fixture",
    )

    fun release(current: PickupOut, photoRef: String): PickupOut = current.copy(
        status = "Released",
        collectorLivePhotoRef = photoRef,
        releasedAt = "fixture",
    )
}
