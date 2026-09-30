package com.satcop.smartvisitor.kiosk.ui.addvisitor

import android.graphics.Bitmap
import com.satcop.smartvisitor.kiosk.data.addvisitor.ActiveVisit
import com.satcop.smartvisitor.kiosk.data.addvisitor.KnownProfile
import com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind

enum class AvStage { NUMBER, FORM }

sealed interface AvNotice {
    data object Blocked : AvNotice
    data class Inside(val active: ActiveVisit) : AvNotice
    data class Failed(val message: String) : AvNotice
}

/**
 * Add Visitor flow state. Everything about the person comes from the server lookup; nothing is cached
 * beyond this in-memory flow (cleared on cancel / new visitor / logout).
 */
data class AddVisitorState(
    val stage: AvStage = AvStage.NUMBER,
    val mobileInput: String = "",
    val mobileError: String? = null,
    val busy: Boolean = false,
    val notice: AvNotice? = null,
    /** Chosen/selected profile kind. Visitor is the default for a new number. */
    val kind: ProfileKind = ProfileKind.VISITOR,
    /** Non-null when the server already knows this number (form A or D). */
    val known: KnownProfile? = null,
    val referencePhoto: Bitmap? = null,
    val checkoutBusy: Boolean = false,
) {
    val returning: Boolean get() = known != null
}
