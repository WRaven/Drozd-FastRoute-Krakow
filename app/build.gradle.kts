import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val gitDescription = providers.exec {
    commandLine("git", "describe", "--tags", "--always")
}.standardOutput.asText.get().trim()

val appVersionName = providers.environmentVariable("APP_VERSION_NAME").orNull
    ?: if (gitDescription.matches(Regex("""v\d+\.\d+\.\d+(?:[-+].*)?"""))) {
        gitDescription.removePrefix("v")
    } else {
        "0.1.0-$gitDescription"
    }

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1

val appVersionCode = providers.environmentVariable("APP_VERSION_CODE").orNull?.toIntOrNull()
    ?: providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()
    ?: gitCommitCount

if (appVersionCode < 1) {
    throw GradleException("APP_VERSION_CODE must be a positive integer.")
}

val signingValues = listOf(
    providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull?.takeIf(String::isNotBlank),
    providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull?.takeIf(String::isNotBlank),
    providers.environmentVariable("ANDROID_KEY_ALIAS").orNull?.takeIf(String::isNotBlank),
    providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull?.takeIf(String::isNotBlank)
)
val hasReleaseSigning = signingValues.all { it != null }
val releaseBuildRequested = gradle.startParameter.taskNames.any {
    it.contains("assembleRelease", ignoreCase = true) ||
        it.contains("bundleRelease", ignoreCase = true) ||
        it.contains("packageRelease", ignoreCase = true)
}

if (signingValues.any { it != null } && !hasReleaseSigning) {
    throw GradleException(
        "Release signing requires ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, " +
            "ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD."
    )
}
if (releaseBuildRequested && !hasReleaseSigning) {
    throw GradleException(
        "Release APKs must use the stable signing key. Configure all four ANDROID_KEYSTORE_* " +
            "environment variables before building a release."
    )
}
if (hasReleaseSigning && !file(signingValues[0]!!).isFile) {
    throw GradleException("The configured Android release keystore does not exist.")
}

android {
    namespace = "pl.wraven.mpkfastroute"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "pl.wraven.mpkfastroute"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("stableRelease") {
                storeFile = file(signingValues[0]!!)
                storePassword = signingValues[1]
                keyAlias = signingValues[2]
                keyPassword = signingValues[3]
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("stableRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.play.services.location)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
