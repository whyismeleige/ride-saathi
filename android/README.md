# Ride Saathi Android

Open this directory in Android Studio. The single `:app` module uses Kotlin and
Jetpack Compose. See [ARCHITECTURE.md](ARCHITECTURE.md) for feature ownership,
state, navigation and platform boundaries.

## Build and test

Use the Gradle wrapper from this directory with Android SDK 36 installed.
Java compilation targets 17; `gradle.properties` currently selects the local
JDK 21 installation. Override `org.gradle.java.home` for a different machine.

```sh
./gradlew test
./gradlew assembleDebug
./gradlew assembleQa
./gradlew :app:assembleRelease :app:lintDebug :app:lintQa
./gradlew :app:assembleQaAndroidTest
node --test app/src/test/js/map-preview.test.cjs
# Requires a connected device/emulator:
./gradlew :app:connectedQaAndroidTest
```

JVM tests cover matching, queries/choices, boundaries, shared-link parsing and
resolution, persistence, localization and navigation. Instrumentation tests cover
address/destination/shared-location flows, providers and the family pilot flow.
They inject controlled providers/location callbacks through `activity.session`
and its feature controllers, rather than reflect on private Activity fields.

`qa` uses `com.ridesaathi.app.qa`, protecting normal installed app data. The test
runner remains `com.ridesaathi.app.PilotTestRunner`; its optional `fontScale`
argument changes only the QA process. Real-device checks remain necessary for
speech/TTS timing, permissions, location quality, map tiles and Uber handoff.

## Configuration

Firebase client configuration is environment-specific and ignored by Git. Download
`google-services.json` from Firebase and place it in `app/` (or the matching
`app/src/<buildType>/` directory). Register `com.ridesaathi.app.qa` for QA and
`com.ridesaathi.app` for debug/release; the configuration must include the package
being built. Supply the same configuration in CI. These client identifiers are
not service-account credentials; never add service-account private keys here.

`API_BASE_URL` resolves from the environment, then `local.properties`, then the
build-type default. Debug and QA default to `http://10.0.2.2:8000`; release defaults
to blank/unconfigured and stays HTTPS-only. Debug/QA network-security overrides
are unchanged. The upstream provider key stays on the backend, never in the APK.

Minimum SDK is 26, target SDK is 35 and compile SDK is 36. Application ID,
SharedPreferences file/keys/JSON and language behavior are unchanged by the
package refactor. The architectural refactor itself adds no runtime or build
dependencies.
