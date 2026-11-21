import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase (push notifications + Crashlytics) is optional and free. It is enabled
// automatically when app/google-services.json exists (see docs/SETUP_GUIDE.md).
val firebaseEnabled = file("google-services.json").exists()
if (firebaseEnabled) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Configuration comes from (in order): environment variables (GitHub Actions secrets),
// android/local.properties, or gradle properties. Nothing secret is committed.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun config(name: String, default: String = ""): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: default

val supabaseUrl = config("SUPABASE_URL")
val supabaseAnonKey = config("SUPABASE_ANON_KEY")
val versionCodeValue = config("VERSION_CODE", "1").toInt()
val versionNameValue = config("VERSION_NAME", "1.0.0")

android {
    namespace = "com.swadsangam.restaurant"
    compileSdk = 36

    defaultConfig {
        // PRD §16.1 — package name must be confirmed available before first release.
        applicationId = "in.swadsangam.restaurant"
        minSdk = 26          // Android 8.0 (PRD §13)
        targetSdk = 36       // Google Play requirement from 31 Aug 2026
        versionCode = versionCodeValue
        versionName = versionNameValue

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("boolean", "FIREBASE_ENABLED", firebaseEnabled.toString())
        buildConfigField("String", "FALLBACK_PHONE", "\"+919675750485\"")
        resourceConfigurations += listOf("en", "hi")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = config("ANDROID_KEYSTORE_PATH")
            if (storePath.isNotBlank() && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = config("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = config("ANDROID_KEY_ALIAS", "swadsangam")
                keyPassword = config("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else null
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions.unitTests.isReturnDefaultValues = true
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.browser)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging) // only used when BuildConfig.DEBUG; removed by R8 in release
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
