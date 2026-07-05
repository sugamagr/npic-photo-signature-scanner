import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// m2508: release keystore lives outside git in `keystore.properties` at the repo root.
// The self-hosted updater (features/update/*) requires the release APK signature to
// match the installed APK; if this file is missing the release build silently falls
// back to the debug signing key, which will make every subsequent update install
// FAIL with INSTALL_FAILED_UPDATE_INCOMPATIBLE. See docs/RELEASE.md for the
// keystore-generation recipe. Debug builds are unaffected.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.npic.photoandsignscanner"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.npic.photoandsignscanner"
        minSdk        = libs.versions.minSdk.get().toInt()
        targetSdk     = libs.versions.targetSdk.get().toInt()

        versionCode = 3
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export for migration testing.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.generateKotlin", "true")
        }

        vectorDrawables { useSupportLibrary = true }

        // OpenCV 4.10 Maven artifact ships arm64-v8a + armeabi-v7a `.so`s only.
        // Filtering to the same ABI set keeps the APK small and avoids UnsatisfiedLinkError
        // when the build system would otherwise expect x86/x86_64 stubs it can't find.
        // Apple-Silicon emulator uses the arm64 image; Intel emulators are unsupported for now.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile") ?: ""
            if (storeFilePath.isNotBlank()) {
                storeFile = file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword") ?: ""
                keyAlias = keystoreProperties.getProperty("keyAlias") ?: ""
                keyPassword = keystoreProperties.getProperty("keyPassword") ?: ""
            }
        }
    }

    buildTypes {
        release {
            // m2508 + m2509 H3: sign with the release key when keystore.properties
            // resolved to a real file. If a release task is on the command line but
            // no valid keystore exists, FAIL the config phase — a debug-signed or
            // unsigned release APK would fail INSTALL_FAILED_UPDATE_INCOMPATIBLE on
            // every user's device the first time they try to auto-update from a
            // previously-release-signed install. Silent unsigned-release ships were
            // the m2509 H3 audit finding.
            val releaseSigning = signingConfigs.getByName("release")
            val hasValidKeystore = releaseSigning.storeFile?.exists() == true
            if (hasValidKeystore) {
                signingConfig = releaseSigning
            } else {
                val runningReleaseTask = gradle.startParameter.taskNames.any {
                    it.contains("Release", ignoreCase = true) ||
                            it.contains("release", ignoreCase = false).not() && it.endsWith("Release")
                }
                if (runningReleaseTask) {
                    throw GradleException(
                        "Release build requires a valid keystore. " +
                                "Populate `keystore.properties` at the repo root with a valid " +
                                "storeFile path — see docs/RELEASE.md."
                    )
                }
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Enables the generated `BuildConfig` class so we can read `VERSION_NAME` from
        // Settings drawer footer (user m1551 S3). AGP 8+ opts this out by default.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/DEPENDENCIES",
                "/META-INF/versions/**/OSGI-INF/**",
            )
        }
        // Android 15+ (SDK 35+) requires arm64 native libraries to be 16 KB page-aligned.
        // Google Play enforces this for new/updated apps targeting SDK 35+ from Nov 2025;
        // Samsung One UI 7 (Android 15) already shows a launch-time warning dialog listing
        // any 4 KB-aligned .so files. Two culprits in this project: libopencv_java4.so
        // (OpenCV 4.10 Maven artifact) and libc++_shared.so (bundled by OpenCV via NDK STL).
        // Setting useLegacyPackaging = false makes AGP store .so files uncompressed and
        // instructs zipalign to use `-P 16` (16 KB page alignment) at packaging time,
        // which realigns even prebuilt libraries. See:
        //   https://developer.android.com/guide/practices/page-sizes
        //   https://developer.android.com/build/releases/gradle-plugin#8-5-0 (jniLibs alignment)
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        animationsDisabled = true
    }

    androidResources {
        // Enable when we ship translated string resources beyond default (en).
        // Requires a resources.properties file declaring unqualifiedResLocale.
        generateLocaleConfig = false
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Strict null-safety + explicit API discipline
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

dependencies {
    // AndroidX core / lifecycle / activity
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.exifinterface)
    implementation(libs.bundles.lifecycle)

    // Compose (BOM aligns transitive versions)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)

    // CameraX
    implementation(libs.bundles.camera)

    // Room (KSP-based codegen)
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // Coroutines, serialization, datetime
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Image loading
    implementation(libs.coil.compose)

    // Preferences DataStore
    implementation(libs.datastore.preferences)

    // OpenCV Android bindings + bundled .so (arm64-v8a, armeabi-v7a). Loaded lazily via
    // NpicApplication.initOpenCVOnce() on first Camera-screen entry. Consumers: EditRenderer
    // (warpPerspective + getPerspectiveTransform for crop commit, warpAffine + getRotationMatrix2D
    // for straighten), BitmapAdjustments (GaussianBlur + addWeighted for Sharpness slider),
    // BitmapFilters (adaptiveThreshold for Document B&W). See PRD §7.3 (perspective correction).
    // §7.1 / §7.2 auto edge / ink detection was removed per m2154.
    implementation(libs.opencv)
    // NOTE: compose-navigation is already pulled in via the compose bundle above.

    // Debug tooling
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Unit tests
    testImplementation(libs.bundles.test.unit)

    // Instrumented tests
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.bundles.test.android)
    androidTestImplementation(libs.room.testing)
}
