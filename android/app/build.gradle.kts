import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Local build configuration is intentionally excluded from version control.
val localConfig = Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}

// API_BASE_URL is NOT a secret: it can safely live inside the APK. The Ola Maps
// API key never enters this Gradle file or any APK; it exists only on the backend.
// Resolution order: API_BASE_URL environment variable, then local.properties,
// then the per-build-type fallback below.
fun apiBaseUrl(fallback: String): String =
    providers.environmentVariable("API_BASE_URL")
        .orElse(providers.provider { localConfig.getProperty("API_BASE_URL", fallback) })
        .get().trim()

fun escaped(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

android {
    namespace = "com.ridesaathi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ridesaathi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"${escaped(apiBaseUrl(""))}\"")
        testInstrumentationRunner = "com.ridesaathi.app.PilotTestRunner"
    }
    buildTypes {
        getByName("debug") {
            // Android emulators reach the host machine's localhost at 10.0.2.2.
            buildConfigField("String", "API_BASE_URL", "\"${escaped(apiBaseUrl("http://10.0.2.2:8000"))}\"")
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
            // Point this at the staging backend (or a local dev backend) via
            // API_BASE_URL before building a QA/dev APK.
            buildConfigField("String", "API_BASE_URL", "\"${escaped(apiBaseUrl("http://10.0.2.2:8000"))}\"")
        }
        getByName("release") {
            // Release expects a production HTTPS API_BASE_URL to be supplied at
            // build time. A blank value keeps the APK unconfigured (NOT_CONFIGURED)
            // rather than shipping a placeholder endpoint.
            buildConfigField("String", "API_BASE_URL", "\"${escaped(apiBaseUrl(""))}\"")
        }
    }
    testBuildType = "qa"
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Play services otherwise brings Fragment 1.1, incompatible with Activity Result APIs.
    implementation("androidx.fragment:fragment:1.8.9")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
