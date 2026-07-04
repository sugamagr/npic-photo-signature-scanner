import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.npic.photoandsignscanner"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.npic.photoandsignscanner"
        minSdk        = libs.versions.minSdk.get().toInt()
        targetSdk     = libs.versions.targetSdk.get().toInt()

        versionCode = 1
        versionName = "0.1.0"

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

    buildTypes {
        release {
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
    // NpicApplication.initOpenCVOnce() on first Camera-screen entry. See PRD §7 for the
    // pipeline that consumes these primitives (edge detection + ink isolation).
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
