import java.util.Properties

// local.properties (gitignored): the Supabase keys, and the release signing key's location and passwords.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sukoon.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sukoon.app"
        // 26+: notification channels are required for the alarm-severity channels (PLAN.md §6)
        // and foreground-service behavior we need for continuous BLE collection anyway.
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.6.1"

        // Followers backend (Supabase): URL + publishable key from the gitignored local.properties.
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProps.getProperty("supabase.publishableKey", "")}\"")
    }

    signingConfigs {
        // Sukoon's own key, on the machine that has it (keystore/ + local.properties, never in git).
        localProps.getProperty("release.storeFile")?.let { path ->
            create("release") {
                storeFile = rootProject.file(path)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Non-debuggable runs Compose at full speed (ART optimisation + baseline profiles). Signed with
            // Sukoon's key where this machine has it, else the debug key; -PdebugSigned forces the debug key
            // (the one build that updates a debug-signed install in place, to back up before moving keys).
            signingConfig = if (project.hasProperty("debugSigned")) signingConfigs.getByName("debug")
            else signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // False positive with Kotlin 2 (K2 UAST): it flags produceState lambdas that do assign `value`.
        disable += "ProduceStateDoesNotAssignValue"
    }

    buildFeatures {
        compose = true
        buildConfig = true // About screen shows the version
    }
}

ksp {
    // Tracks schema history for future Room migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    // Home-screen widgets (ui/widget/): Compose-style RemoteViews that know their exact resized size.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.health.connect) // MyFitnessPal meals in, glucose out (health/)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // android.jar's org.json is a stub on the JVM; the real one lets parser tests run off-device.
    testImplementation(libs.json)
}
