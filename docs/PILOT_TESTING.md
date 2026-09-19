# V0 pilot checks

Use the debug APK at `app/build/outputs/apk/debug/app-debug.apk` for a supervised pilot. Installing with `adb install -r` preserves saved places. The release build is unsigned and is not the installable pilot artifact.

## Automated checks

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
node --test app/src/test/js/map-preview.test.cjs
./gradlew :app:connectedQaAndroidTest
```

Device tests use the separate `com.ridesaathi.app.qa` application so test setup cannot erase addresses in `com.ridesaathi.app`. They inject final speech transcripts to exercise the application flow; microphone recognition and spoken output still need a person speaking on each target phone. Device screenshots are saved in the QA app's external files directory. Gradle normally uninstalls the QA app afterward; retain it while reviewing screenshots with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. To check 200% text inside the QA app without changing the phone's settings, also pass `-Pandroid.testInstrumentationRunnerArguments.fontScale=2.0`. Remove the QA packages after review with `adb uninstall com.ridesaathi.app.qa.test` and `adb uninstall com.ridesaathi.app.qa`.

## Voice behavior to check with the family

| Saved places | Spoken phrase | Expected result |
| --- | --- | --- |
| ghar + beta ka ghar | beta ka ghar / बेटे के घर जाना है | Confirm beta ka ghar |
| ghar + beta ka ghar | ghar / घर जाना है | Confirm Home |
| ghar + beta ka ghar | घर या बेटे के घर | Ask which place |
| Home + Doctor | डॉक्टर के पास जाना है | Confirm Doctor |
| Home + Ramesh House | रमेश के घर | Confirm Ramesh House |
| Any confirmation | हाँ नहीं / yes, no | Return to choosing a place |
| Any confirmation | यहाँ / yesterday | Ask again; do not open Uber |
| Home | homework | Do not select Home |

Matching uses whole words, a small bilingual place vocabulary, and conservative Hindi/Roman spelling comparison. It prefers a longer saved name over a shorter name contained in the same spoken phrase. Separate mentions and equal matches still ask for clarification. Spelling comparison is not general translation: add familiar nicknames and unusual spellings as comma-separated voice names during setup, then try those exact names with the person. Existing saved places need no migration.

## Before inviting participants

- Set up the actual saved addresses with a family member. Search explicitly, select the result, and verify the full address and map. Check Home and each additional place after reopening the app.
- In English and Hindi, speak each saved place naturally. Verify the recognized destination, spoken prompt, and both Yes and No. Repeat with background noise and with the phone held at the distance the person normally uses.
- Check the phone's largest comfortable font/display size. Scroll through Home, setup, and confirmation. Try TalkBack: each action should be named, reachable, and understandable.
- Deny microphone permission and verify destination buttons remain usable. Deny location permission and verify the app offers its permission settings. Return, grant permission, and retry.
- Turn location off, try again, and use the location-settings recovery. Turn it back on afterward. Check airplane mode/no internet: saved-place selection should remain available and search/maps should explain the failure.
- Confirm a destination and check the actual Uber pickup/drop-off screen. Uber must still ask the rider to choose and book the ride. Do not count opening Uber as a booked ride.
- During listening or location lookup, press Back or leave the app. Return and check that no late callback opens Uber or changes the destination. Rapidly tap confirmation and verify only one handoff.
- Try an unsaved place, two destinations in one sentence, silence, and an unclear reply. The user must be able to recover using the large place buttons.

## Observe each participant

Ask them to go to Home, then a family member's address, first by touch and then by voice. Let them try before helping. Record language, phone/font size, phrase used, whether the right place appeared, whether they understood that booking finishes in Uber, and where help was needed. Avoid recording exact personal addresses in feedback notes.

Use the first 3–5 sessions to find remaining difficulties; repeat any failed task after a fix before widening the pilot.
