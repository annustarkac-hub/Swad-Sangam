import java.util.Properties

// Swad Sangam Admin + Staff apps: a secure full-screen shell around the admin web app (admin.html),
// with native printing, file upload, downloads and phone/WhatsApp/UPI links. Two flavours, one codebase.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun config(name: String, default: String = ""): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: default

val siteBase = config("SITE_BASE_URL", "https://annustarkac-hub.github.io/Swad-Sangam/")

android {
    namespace = "in.swadsangam.staff"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = config("VERSION_CODE", "1").toInt()
        versionName = config("VERSION_NAME", "1.0.0")
        buildConfigField("String", "SITE_BASE", "\"$siteBase\"")
    }

    flavorDimensions += "role"
    productFlavors {
        create("admin") {
            dimension = "role"
            applicationId = "in.swadsangam.admin"
            resValue("string", "app_name", "Swad Sangam Admin")
            buildConfigField("String", "START_PATH", "\"admin.html#/\"")
            buildConfigField("boolean", "KEEP_SCREEN_ON", "false")
        }
        create("staff") {
            dimension = "role"
            applicationId = "in.swadsangam.staff"
            resValue("string", "app_name", "Swad Sangam Staff")
            // Opens straight on the billing screen; kitchen staff are sent to the kitchen display by their role.
            buildConfigField("String", "START_PATH", "\"admin.html#/pos\"")
            buildConfigField("boolean", "KEEP_SCREEN_ON", "true")
        }
    }

    signingConfigs {
        create("release") {
            val storePath = config("ANDROID_KEYSTORE_PATH")
            if (storePath.isNotBlank() && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = config("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = config("ANDROID_KEY_ALIAS", "swadsangam")
                keyPassword = config("ANDROID_KEY_PASSWORD", config("ANDROID_KEYSTORE_PASSWORD"))
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
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
