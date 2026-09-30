import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.satcop.smartvisitor.kiosk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.satcop.smartvisitor.kiosk"
        minSdk = 23
        targetSdk = 35
        versionCode = 1066
        versionName = "1.0.14-1role-PREVIEW-TEAL-2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-DEMO"
        }
        // 1064 QA-ONLY build: fake front camera for emulators. Own applicationId, own source set (src/debugqa),
        // never signed/published as a release. Release/debug source sets carry a no-op QaHooks only.
        create("debugqa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debugqa"
            versionNameSuffix = "-DEBUG-QA-ONLY"
            matchingFallbacks += listOf("debug")
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // minSdk 23 (Android 6): java.time etc. via core library desugaring
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    add("debugqaImplementation", "androidx.compose.ui:ui-tooling")
    add("debugqaImplementation", "androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

// ---------------------------------------------------------------------------------------------------------------
// 1064 release guard: the QA-only fake camera / bypass must never reach a release build.
//  1) verifyNoQaBypassSources: runs BEFORE release compilation; fails if src/main or src/release mention any QA symbol
//     or if src/release QaHooks is not the constant-false no-op.
//  2) verifyNoQaBypassInReleaseApk: runs AFTER packageRelease (finalizer); fails if any release dex / manifest
//     contains a QA class name, the debugqa applicationId or the switch action.
// ---------------------------------------------------------------------------------------------------------------
val qaForbiddenSymbols = listOf(
    "DebugQaTestPattern", "QaSwitchReceiver", "debugqa", "FAKE_CAMERA", "QA TEST IMAGE", "TEST CAMERA (QA build)",
    "QaOtpRepository", "QaOtpState", "QA_OTP", "qa-otp-", "qa-reset-token",
)

// Dev/scaffolding copy and fixture data must never ship in a release APK (see ForbiddenDevStringsTest).
val devForbiddenInApk = listOf(
    "under shell", "tabs stay", "compact home", "no long scroll", "Demo host approve", "Enroll face (stub)",
    "ID captured (demo)", "Sample parent visit loaded", "Block sample", "Alert sample", "Prefill sample",
    "Load P-4F21", "not a real government ID", "demo seed", "FIXTURES \u00b7", "Demo story",
)

val verifyNoQaBypassSources = tasks.register("verifyNoQaBypassSources") {
    group = "verification"
    description = "Fails if QA-only bypass symbols appear in src/main or src/release."
    val roots = listOf(file("src/main"), file("src/release"))
    val releaseHooks = file("src/release/java/com/satcop/smartvisitor/kiosk/qa/QaHooks.kt")
    doLast {
        val bad = mutableListOf<String>()
        roots.filter { it.exists() }.forEach { root ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val text = f.readText()
                qaForbiddenSymbols.filter { text.contains(it) }.forEach { bad += "${f.relativeTo(projectDir)}: $it" }
            }
        }
        if (!releaseHooks.exists() || !releaseHooks.readText().contains("const val fakeCamera: Boolean = false") ||
            !releaseHooks.readText().contains("const val otpEntryPoints: Boolean = false") ||
            !releaseHooks.readText().contains("fun otpRepository(): OtpRepository? = null")) {
            bad += "src/release QaHooks must be the constant-false no-op"
        }
        if (bad.isNotEmpty()) throw GradleException("QA bypass code found in release sources:\n" + bad.joinToString("\n"))
    }
}

val verifyNoQaBypassInReleaseApk = tasks.register("verifyNoQaBypassInReleaseApk") {
    group = "verification"
    description = "Fails if the packaged release APK contains QA-only bypass symbols."
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    doLast {
        val apks = apkDir.get().asFile.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        if (apks.isEmpty()) throw GradleException("No release APK found to verify in ${apkDir.get().asFile}")
        val bad = mutableListOf<String>()
        apks.forEach { apk ->
            ZipFile(apk).use { zip ->
                zip.entries().asSequence().filter { it.name.startsWith("assets/fixtures") }.forEach { bad += "${apk.name}!${it.name}: fixture asset in release" }
                zip.entries().asSequence().filter { it.name.endsWith(".dex") }.forEach { e ->
                    val latin = String(zip.getInputStream(e).readBytes(), Charsets.ISO_8859_1)
                    devForbiddenInApk.forEach { d -> if (latin.contains(d)) bad += "${apk.name}!${e.name}: dev text \"$d\"" }
                }
                zip.entries().asSequence().filter { it.name.endsWith(".dex") || it.name == "AndroidManifest.xml" }.forEach { e ->
                    val raw = zip.getInputStream(e).readBytes()
                    val latin = String(raw, Charsets.ISO_8859_1)
                    // AndroidManifest.xml is UTF-16LE inside binary XML
                    val utf16 = if (e.name.endsWith(".xml")) String(raw, Charsets.UTF_16LE) else ""
                    qaForbiddenSymbols.forEach { s ->
                        if (latin.contains(s) || (utf16.isNotEmpty() && utf16.contains(s))) bad += "${apk.name}!${e.name}: $s"
                    }
                }
            }
        }
        if (bad.isNotEmpty()) throw GradleException("QA bypass symbols found in RELEASE APK:\n" + bad.joinToString("\n"))
        println("verifyNoQaBypassInReleaseApk: OK (${apks.size} apk, 0 QA symbols)")
    }
}

afterEvaluate {
    tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyNoQaBypassSources) }
    tasks.matching { it.name == "packageRelease" }.configureEach { finalizedBy(verifyNoQaBypassInReleaseApk) }
    tasks.matching { it.name == "assembleRelease" }.configureEach { dependsOn(verifyNoQaBypassInReleaseApk) }
}
