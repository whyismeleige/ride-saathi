# Third-Party Notices

Ride Saathi uses open-source software and public map data. This file summarizes
the primary third-party notices that apply to source distributions and app
builds. Upstream packages retain their own copyright notices and license files.

## Bundled Assets

### Leaflet

- Location: `android/app/src/main/assets/map/leaflet.js`,
  `android/app/src/main/assets/map/leaflet.css`
- License: BSD 2-Clause
- Copyright: Volodymyr Agafonkin, CloudMade, and Leaflet contributors
- Full license text: `android/app/src/main/assets/map/LICENSE`

### OpenStreetMap

- Location: map tiles shown by the local Leaflet preview
- Attribution: OpenStreetMap contributors
- License / terms: OpenStreetMap data is available under the Open Database
  License, and map tile usage is subject to OpenStreetMap Foundation tile usage
  policy.
- In-app attribution: the map preview includes the OpenStreetMap attribution
  link in `android/app/src/main/assets/map/index.html`.

## Android And JVM Dependencies

The Android app uses Android Gradle Plugin, Gradle Wrapper, Kotlin, Jetpack
Compose, AndroidX, Google Play services, Firebase, JUnit, and JSON.org
libraries. These components are distributed under their respective upstream
licenses, primarily Apache License 2.0, Eclipse Public License 1.0 for JUnit 4,
and the JSON License for JSON.org.

The Gradle wrapper scripts include their own Apache License 2.0 notices in
`android/gradlew` and `android/gradlew.bat`.

## Backend Dependencies

The backend uses FastAPI, Uvicorn, HTTPX, Pydantic, and pytest. These packages
are distributed under their respective upstream licenses, primarily MIT and BSD
family licenses.

## Service Providers

Ride Saathi integrates with third-party services and apps, including Uber and
Ola Maps. Their trademarks, APIs, SDKs, map data, and service terms remain owned
and controlled by their respective providers.
