package com.satcop.smartvisitor.kiosk.data.model

/** Live Pranay seed school — never show Priya / P-4F21 / demo chips here. */
object SchoolIds {
    const val PRANAY = "SCH-PRANAY-01"
    const val DEMO = "SCH-DEMO-01"

    fun hidesDemoStory(schoolId: String?): Boolean =
        schoolId?.trim().equals(PRANAY, ignoreCase = true)
}

object AfterHoursCopy {
    const val HOST_NO_OP =
        "After hours · Host Approve is a no-op. Admin or Security Head must approve."
    const val CODE = "AFTER_HOURS_SH_REQUIRED"
}

object CampusHours {
    fun isAfterHours(
        rows: List<CampusHoursRow>,
        now: java.time.ZonedDateTime,
    ): Boolean {
        if (rows.isEmpty()) return false
        val weekday = now.dayOfWeek.name.take(3).lowercase()
        val row = rows.firstOrNull { it.weekday.equals(weekday, ignoreCase = true) } ?: return false
        if (row.closed) return true
        val open = row.openTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
        val close = row.closeTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
        if (open == null || close == null) return row.closed
        val t = now.toLocalTime()
        return t.isBefore(open) || !t.isBefore(close)
    }
}


object GateConsent {
    const val VERSION = "visitor_notice_en_hi_v1"
    const val TITLE_EN = "Visitor notice"
    const val TITLE_HI = "आगंतुक सूचना"
    const val BODY_EN =
        "This school uses Satcop Smart Visitor to verify visitors and keep the campus safe. We will collect your name, mobile, visit purpose, host, a live photo, and government ID details or ID image. Data is used for entry approval, checkout, and security audit, and is retained only as long as the school’s retention policy allows. You may ask the school office about access, correction, or deletion.\n\nBy tapping I agree & continue, you consent to this processing for today’s visit."
    const val BODY_HI =
        "यह स्कूल परिसर की सुरक्षा के लिए Satcop Smart Visitor का उपयोग करता है। हम आपका नाम, मोबाइल, आने का उद्देश्य, होस्ट, लाइव फोटो और सरकारी पहचान विवरण/फोटो लेंगे। डेटा प्रवेश अनुमति, चेकआउट और सुरक्षा रिकॉर्ड के लिए उपयोग होगा और स्कूल की नीति के अनुसार ही रखा जाएगा। पहुँच, सुधार या हटाने के लिए स्कूल कार्यालय से संपर्क करें।\n\nसहमत हूँ और आगे बढ़ें पर टैप करके आप आज की विज़िट के लिए सहमति देते हैं।"
    const val AGREE_EN = "I agree & continue"
    const val AGREE_HI = "सहमत हूँ और आगे बढ़ें"
    const val DECLINE = "Decline / Back · अस्वीकार / वापस"
}

/** Staff face login consent — Gate|Host|Guard (visitor OUT). Compliance pack staff_face_notice_en_hi_v1. */
object StaffFaceConsent {
    const val VERSION = "staff_face_notice_en_hi_v1"
    const val TITLE_EN = "Staff face login notice"
    const val TITLE_HI = "स्टाफ फेस लॉगिन सूचना"
    const val BODY_EN =
        "This school uses Satcop Smart Visitor face login so you can sign in with your face on the school device. We will capture a live face image to create a login template stored on the school’s Satcop servers (not as a long-lived face gallery on this phone). The template is used only to verify it is you at login. You can still use your username and password anytime. The school admin can reset your face login; you would enroll again. You may ask the school office about access, correction, or deletion of this biometric login data.\n\nBy tapping I agree & continue, you consent to creating and using your staff face-login template for school sign-in."
    const val BODY_HI =
        "यह स्कूल Satcop Smart Visitor फेस लॉगिन का उपयोग करता है ताकि आप स्कूल डिवाइस पर अपने चेहरे से साइन इन कर सकें। हम एक लाइव चेहरे की छवि लेकर लॉगिन टेम्पलेट बनाएंगे, जो स्कूल के Satcop सर्वर पर रखा जाएगा (इस फ़ोन पर लंबे समय तक फेस गैलरी के रूप में नहीं)। टेम्पलेट केवल यह जाँचने के लिए है कि लॉगिन पर आप ही हैं। आप कभी भी उपयोगकर्ता नाम और पासवर्ड से साइन इन कर सकते हैं। स्कूल एडमिन आपका फेस लॉगिन रीसेट कर सकता है; तब दोबारा नामांकन करना होगा। इस बायोमेट्रिक लॉगिन डेटा की पहुँच, सुधार या हटाने के लिए स्कूल कार्यालय से संपर्क करें।\n\nसहमत हूँ और आगे बढ़ें पर टैप करके आप स्कूल साइन-इन हेतु स्टाफ फेस-लॉगिन टेम्पलेट बनाने और उपयोग करने की सहमति देते हैं।"
    const val AGREE_EN = "I agree & continue"
    const val AGREE_HI = "सहमत हूँ और आगे बढ़ें"
    const val DECLINE = "Decline / Back · अस्वीकार / वापस"
}
