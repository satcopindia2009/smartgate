package com.satcop.smartvisitor.kiosk.ui.otp

/** EN + HI strings from Product otp-spec section 6 and the OTP build scope (HI pending Compliance review). */
data class OtpStrings(
    val title: String, val sentTo: (String) -> String, val enterCode: String, val verify: String,
    val resendIn: (Int) -> String, val resend: String, val sendWhatsApp: String, val sendSms: String,
    val wrong: (Int?) -> String, val expired: String, val locked: String, val resendLimit: String,
    val dailyLimit: String, val sendFailed: String, val sendAgain: String, val tryAgain: String, val verified: String,
    val consentStaff: String, val consentVisitor: String,
    val forgotTitle: String, val forgotGeneric: String, val sendCode: String, val passwordChanged: String,
    val verifyNow: String, val notVerified: String, val verifiedChip: String, val changeNumber: String,
    val skip: String, val mobileVerifiedChip: String, val visitorCardTitle: String, val visitorAsk: (String) -> String,
)

object OtpCopy {
    val EN = OtpStrings(
        title = "Verify mobile number",
        sentTo = { "We sent a 6-digit code to $it." },
        enterCode = "Enter the code",
        verify = "Verify",
        resendIn = { "Resend code in $it s" },
        resend = "Resend code",
        sendWhatsApp = "Send by WhatsApp",
        sendSms = "Send by SMS",
        wrong = { n -> if (n == null) "That code is not right." else "That code is not right. $n ${if (n == 1) "try" else "tries"} left." },
        expired = "This code has expired. Request a new one.",
        locked = "Too many attempts. Try again in 30 minutes.",
        resendLimit = "You have used all resends. Try again later.",
        dailyLimit = "Daily limit reached. Try again tomorrow.",
        sendFailed = "Could not send the code. Try again or use another option.",
        sendAgain = "Send again",
        tryAgain = "Try again",
        verified = "Mobile number verified.",
        consentStaff = "By continuing you allow the school and Satcop Smart Visitor to send a one-time code to this number by SMS or WhatsApp, to verify it and to help you reset your password. We use this message only to verify your number.",
        consentVisitor = "To confirm this number, the school will send a one-time code to it by SMS. We use the number only for this visit and campus safety. By continuing you allow this.",
        forgotTitle = "Forgot password",
        forgotGeneric = "If this number is registered and verified, a code has been sent.",
        sendCode = "Send code",
        passwordChanged = "Password changed. Please sign in.",
        verifyNow = "Verify now",
        notVerified = "Not verified",
        verifiedChip = "Verified ✓",
        changeNumber = "Change number",
        skip = "Skip",
        mobileVerifiedChip = "Mobile verified",
        visitorCardTitle = "Verify visitor's mobile",
        visitorAsk = { "Ask the visitor for the code sent to $it." },
    )

    val HI = EN.copy(
        title = "मोबाइल नंबर सत्यापित करें",
        sentTo = { "हमने $it पर 6 अंकों का कोड भेजा है।" },
        enterCode = "कोड दर्ज करें",
        resendIn = { "$it सेकंड में कोड दोबारा भेजें" },
        resend = "कोड दोबारा भेजें",
        sendWhatsApp = "व्हाट्सऐप से भेजें",
        sendSms = "एसएमएस से भेजें",
        wrong = { n -> if (n == null) "यह कोड सही नहीं है।" else "यह कोड सही नहीं है। $n प्रयास बाकी हैं।" },
        expired = "यह कोड समाप्त हो गया है। नया कोड मांगें।",
        locked = "बहुत अधिक प्रयास। 30 मिनट बाद पुनः प्रयास करें।",
        resendLimit = "आप सभी बार कोड दोबारा भेज चुके हैं। बाद में प्रयास करें।",
        dailyLimit = "आज की सीमा पूरी हो गई। कल प्रयास करें।",
        sendFailed = "कोड नहीं भेजा जा सका। फिर प्रयास करें या दूसरा विकल्प चुनें।",
        verified = "मोबाइल नंबर सत्यापित हो गया।",
        consentStaff = "आगे बढ़ने पर आप स्कूल और Satcop Smart Visitor को इस नंबर पर SMS या व्हाट्सऐप से एक बार का कोड भेजने की अनुमति देते हैं, ताकि नंबर सत्यापित हो और पासवर्ड रीसेट में मदद मिल सके। यह संदेश केवल सत्यापन के लिए है।",
        consentVisitor = "इस नंबर की पुष्टि के लिए स्कूल इस पर SMS से एक बार का कोड भेजेगा। हम नंबर का उपयोग केवल इस विज़िट और परिसर की सुरक्षा के लिए करते हैं। आगे बढ़ने पर आप इसकी अनुमति देते हैं।",
        forgotGeneric = "यदि यह नंबर पंजीकृत और सत्यापित है, तो कोड भेज दिया गया है।",
        passwordChanged = "पासवर्ड बदल गया। कृपया साइन इन करें।",
    )

    fun forLanguage(lang: String?): OtpStrings = if (lang.equals("hi", true)) HI else EN
}
