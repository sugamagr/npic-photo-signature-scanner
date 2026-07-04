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
            "-Xcontext-receivers",
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

    // NOTE: OpenCV artifact is intentionally omitted from this bootstrap commit.
    // The Maven-central 'org.opencv:opencv:4.10.0' distribution requires the native
    // .so libraries via a JNI loader that we wire up in a later camera-layer commit.
    // Adding it here without the native init would ship a broken dependency.
    // See PRD §7 for the pipeline; the camera layer will add the artifact + init.

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
