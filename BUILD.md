# Build & deploy

This document covers building, signing, and shipping Future Clock to the Play
Store or any other Android distribution channel.

## Build environment

| Tool         | Version       |
| ------------ | ------------- |
| JDK          | 17            |
| Gradle       | 8.4           |
| AGP          | 8.2.2         |
| Kotlin       | 1.9.22        |
| Android SDK  | platform-34, build-tools 34.0.0 |

Set `ANDROID_HOME` to a directory containing `platforms/android-34` and
`build-tools/34.0.0`, or set `sdk.dir` in `local.properties` to the same path.

## Debug build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Install on a connected device:

```bash
./gradlew installDebug
# or
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed with the default Android debug keystore so it can be
side-loaded on any device.

## Release build

### 1. Generate a signing key

```bash
keytool -genkey -v -keystore futureclock-release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias futureclock
```

Store this file **outside** the repository and **never commit it**. Add
`*.jks` and `*.keystore` to `.gitignore` (already done).

### 2. Create `keystore.properties`

Create `keystore.properties` in the project root (it is gitignored):

```properties
storeFile=/absolute/path/to/futureclock-release.jks
storePassword=your-store-password
keyAlias=futureclock
keyPassword=your-key-password
```

### 3. Wire signing in `app/build.gradle.kts`

Add the following to the `android` block, before `buildTypes`:

```kotlin
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

signingConfigs {
    create("release") {
        keyAlias = keystoreProperties["keyAlias"] as String?
        keyPassword = keystoreProperties["keyPassword"] as String?
        storeFile = (keystoreProperties["storeFile"] as String?)?.let { file(it) }
        storePassword = keystoreProperties["storePassword"] as String?
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // ... existing release config
    }
}
```

### 4. Build a signed AAB

```bash
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

This is the artifact you upload to the Play Console.

## Play Store checklist

- [ ] Replace test AdMob unit IDs with your real ones (see `ADS.md`).
- [ ] Add the Google Play services UMP consent dialog before the first ad request.
- [ ] Add a privacy policy URL to the Play Store listing.
- [ ] Set the correct content rating and target audience.
- [ ] Add screenshots (16:9 and 9:16) showing the 5 tabs and each of the 4 widgets.
- [ ] Add a feature graphic (1024×500).
- [ ] Choose category: Tools / Productivity.
- [ ] Confirm that all four widget previews render correctly (the system uses
      `previewLayout` from `widget_info_*.xml`).
- [ ] Confirm that the app requests `SCHEDULE_EXACT_ALARM` properly on API 31+ —
      the app shows a rationale and deep-links to the system settings screen.

## App Bundle vs APK

- AAB (.aab) — required for Play Store. Google generates per-device APKs.
- APK (.apk) — useful for direct distribution, sideloading, internal testing,
  and CI.

## Continuous integration

A minimal GitHub Actions workflow is in `.github/workflows/android.yml`. It
runs on every push and PR, builds the debug APK, and uploads it as an
artifact named `app-debug`.

## Offline place catalog

The packaged catalog is a separate, disposable SQLite database; it never shares
storage with Room user data. `CityCatalog` expands
`app/src/main/assets/places-v2.sqlite.dbz` into `noBackupFilesDir`, validates a
new copy, and atomically replaces only the catalog when its version changes.

Regenerate it from the official GeoNames exports:

```bash
python tools/build_place_catalog.py \
  --cities /path/to/cities500.zip \
  --countries /path/to/countryInfo.txt \
  --admin1 /path/to/admin1CodesASCII.txt \
  --output app/src/main/assets/places-v2.sqlite.dbz
```

Verify the exact packaged asset before release:

```bash
python tools/verify_place_catalog.py \
  app/src/main/assets/places-v2.sqlite.dbz \
  --java "$JAVA_HOME/bin/java"
```

The verifier checks SQLite integrity, schema/version metadata, indexes, required
fields, coordinates, duplicate identifiers, representative searches, global
coverage, and every timezone ID against Java.

## Troubleshooting

| Problem                                    | Fix                                                                                          |
| ------------------------------------------ | -------------------------------------------------------------------------------------------- |
| `Could not find :play-services-ads:`       | Run `./gradlew --refresh-dependencies` to clear the cache                                     |
| `SDK location not found`                   | Add `sdk.dir=...` to `local.properties` or set `ANDROID_HOME`                                 |
| Alarms not firing in Doze                  | Confirm `SCHEDULE_EXACT_ALARM` is granted on API 31+ devices (the app prompts the user)     |
| Widget stuck on the wrong time             | Open the widget from the launcher and remove + re-pin it; this re-triggers a tick            |
| Banner ad shows "Missing ad unit ID"       | You are running a release build that still has the test ID — replace with your own           |
