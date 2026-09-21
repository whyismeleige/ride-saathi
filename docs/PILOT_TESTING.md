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
- Try an unsaved place in English and Hindi, including “take me to Apollo Hospital” and “मुझे अपोलो अस्पताल जाना है”. Check the query, the actual first three Ola results, addresses, and selected map pin. Include another city explicitly and verify results are not restricted to Home.
- Verify options finish speaking before the microphone starts automatically. Say “second one” / “दूसरा”, select by touch during playback, remain silent, and give an ambiguous hospital name. Silence and unclear replies must leave the list usable without selecting a place.
- Try More results, Repeat options, and Search again by voice and touch. Replace the query by speech and typed text. Select a singleton result, then use No/Back to return to the choices. Cancel should return Home; searched destinations must not appear in saved places.
- With location permission unavailable or location disabled, check the Home-preference message and usable search. Leave during a search, then return and retry; no late response should change the screen. Check no results, internet loss, and recovery.
- Try two saved destinations in one sentence. The app must still ask which saved place you mean without searching online.

## Observe each participant

Ask them to go to Home, then a family member's address, first by touch and then by voice. Let them try before helping. Record language, phone/font size, phrase used, whether the right place appeared, whether they understood that booking finishes in Uber, and where help was needed. Avoid recording exact personal addresses in feedback notes.

Use the first 3–5 sessions to find remaining difficulties; repeat any failed task after a fix before widening the pilot.
