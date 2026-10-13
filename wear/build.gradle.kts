import java.util.Properties

// Same local.properties as :app: the watch app has to be signed with the phone app's key, or the
// Data Layer won't pair them.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sukoon.wear"
    compileSdk = 36

    defaultConfig {
        // The Data Layer only connects apps with the same package name (and signing key) on phone and
        // watch, so each flavor matches its phone flavor's applicationId.
        applicationId = "com.sukoon.app"
        minSdk = 30 // Wear OS 3
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "store"
    productFlavors {
        create("github") { dimension = "store" }
        create("play") {
            dimension = "store"
            applicationId = "io.github.khairyky.sukoon"
        }
    }

    signingConfigs {
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
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.complications.data.source.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.concurrent.futures)
    testImplementation(libs.junit)
}
