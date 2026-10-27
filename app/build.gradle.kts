plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.phonediagnostic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phonediagnostic"
        minSdk = 26
        targetSdk = 35
        versionCode = 34
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("ciDebug") {
            val keystoreFile = rootProject.file("keystore/debug.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        // Release signing is driven by environment variables (set in CI from
        // GitHub Actions secrets). Never commit the real keystore.
        val releaseStorePath = System.getenv("RELEASE_STORE_FILE")
        if (!releaseStorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            val ciKeystore = rootProject.file("keystore/debug.keystore")
            if (ciKeystore.exists()) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Prefer the private release key when CI provides it. Fall back to
            // the committed CI keystore only for local unsigned experiments;
            // the Release workflow refuses to publish under the public key
            // unless allow_public_key_signing is explicitly ticked.
            signingConfig = when {
                signingConfigs.findByName("release") != null ->
                    signingConfigs.getByName("release")
                rootProject.file("keystore/debug.keystore").exists() ->
                    signingConfigs.getByName("ciDebug")
                else -> null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // The elevated-access helper Shizuku binds is defined by an AIDL contract.
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // lifecycle-lint's NonNullableMutableLiveDataDetector crashes under Kotlin
    // 2.1's analysis API (IncompatibleClassChangeError on KaCallableMemberCall).
    // Safe to disable: this app uses StateFlow, not MutableLiveData.
    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    // Compose 1.12 / BOM 2026.09 requires compileSdk 37 + AGP 9.2 — stay on a
    // BOM that still targets SDK 35 while AGP is on 8.7.2.
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Optional, opt-in elevated access. The app runs fine with neither the
    // Shizuku manager app installed nor root; these only add the client API and
    // the provider the manager binds to.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
    // The android.jar stub throws on every org.json call, so unit tests need the
    // real implementation to exercise the JSON export.
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
