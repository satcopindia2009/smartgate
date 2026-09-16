package com.satcop.smartvisitor.kiosk.data.registration

import com.satcop.smartvisitor.kiosk.data.fixture.DemoFixtures
import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.VisitorType

/**
 * In-progress gate registration. Field names match POST /v1/visits where they exist.
 * Wave 1 does not POST — camera / media / blacklist are Wave 2.
 */
data class RegistrationDraft(
    val visitorType: String = VisitorType.Parent.apiValue,
    val visitorName: String = "",
    val mobile: String = "",
    val purpose: String = "",
    val hostId: String? = DemoFixtures.HOST_ANITA_ID,
    val gateId: String = DemoFixtures.GATE_MAIN_ID,
    val vehicleNumber: String = "",
    val accompanyingCount: String = "",
    val notes: String = "",
) {
    companion object {
        fun fromStory(story: DemoStory): RegistrationDraft = RegistrationDraft(
            visitorType = story.visitorType,
            visitorName = story.visitorName,
            mobile = story.mobile,
            purpose = story.purpose,
            hostId = story.hostId,
            gateId = story.gateId,
        )
    }
}
