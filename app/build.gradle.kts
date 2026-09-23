import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: reads a properties file kept OUTSIDE the repo
// (storeFile / storePassword / keyAlias / keyPassword). JEV_KEYSTORE_PROPS wins.
// The legacy H: fallback is Windows-only; asking Gradle's Linux file resolver to
// parse "H:/..." fails during configuration before a debug build can even start.
val releasePropsPath = System.getenv("JEV_KEYSTORE_PROPS")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        "H:/android/keys/jev-release.properties"
    } else {
        null
    }

val releaseProps = Properties().apply {
    releasePropsPath?.let { path ->
        val f = File(path)
        if (f.exists()) FileInputStream(f).use { load(it) }
    }
}

android {
    namespace = "com.jev.probe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jev.probe"
        minSdk = 30
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"

        // ML Kit's bundled Chinese recognizer ships native libs for every ABI.
        // The target phone (and every phone this can run on: minSdk 30) is
        // arm64, so keep only that one — the other three are dead weight.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (releaseProps.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Acceptance builds install beside the upstream release instead of
            // requiring an uninstall that would wipe API keys and local history.
            applicationIdSuffix = ".guofeng"
            versionNameSuffix = "-wechat-exact-probe"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Uncompressed, page-aligned .so files: required for the 16 KB page-size
    // devices Android 15+ ships, and it lets the loader mmap the ML Kit natives
    // instead of unpacking them at install time.
    packaging {
        jniLibs {
            useLegacyPackaging = false
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // On-device OCR. The *bundled* Chinese model (not the play-services variant):
    // it works on phones with no Google Play services and needs no model download.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    testImplementation("junit:junit:4.13.2")
    // Real JVM JSONObject for local unit tests; android.jar provides only throwing stubs.
    testImplementation("org.json:json:20240303")
}
