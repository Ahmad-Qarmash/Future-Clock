# Ads (AdMob)

Future Clock uses Google AdMob for monetisation. Banner ads sit above the
bottom navigation on every screen. Interstitials appear at well-defined
moments that match user intent (dismissing an alarm, adding a city) plus
every fifth tab change, with a hard minimum interval of one minute so
they never feel spammy.

## Ad units

| Type          | Variable                      | Default (test ID)                                          |
| ------------- | ----------------------------- | ---------------------------------------------------------- |
| App ID        | `strings.xml` → `admob_app_id`| `ca-app-pub-3940256099942544~3347511713`                   |
| Banner        | `BuildConfig.BANNER_AD_UNIT_ID`     | `ca-app-pub-3940256099942544/6300978111`             |
| Interstitial  | `BuildConfig.INTERSTITIAL_AD_UNIT_ID`| `ca-app-pub-3940256099942544/1033173712`            |

The defaults above are Google-provided test IDs and are safe to ship in a
debug APK. **They will not earn any revenue** — Google only pays on real
production unit IDs.

## Swapping in your real unit IDs

1. Create an AdMob account at https://admob.google.com.
2. Create a new app and three ad units: one banner, one interstitial.
3. Copy your App ID (looks like `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`)
   and replace the value of `admob_app_id` in `res/values/strings.xml`.
4. Open `app/build.gradle.kts` and replace the two `buildConfigField` lines
   inside `buildTypes.release` with your real banner and interstitial unit IDs:

   ```kotlin
   buildTypes {
       release {
           buildConfigField("String", "BANNER_AD_UNIT_ID", "\"ca-app-pub-XXXX/YYY\"")
           buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-XXXX/ZZZ\"")
           // ... rest
       }
   }
   ```

5. Re-build a release APK and the IDs will be baked into `BuildConfig`.

The `debug` build type intentionally keeps the test IDs so your local
testing never serves a real ad or risks your account.

## Where the ads appear

- **Banner** — every tab, above the bottom nav. Sits in `MainActivity.ad_container`
  which is added by the layout once at startup.
- **Interstitial** — at the following triggers, only when both the minimum
  interval (60 s) and the cadence rule are satisfied:

  | Trigger               | Cadence                          |
  | --------------------- | -------------------------------- |
  | Alarm dismiss         | Every time                       |
  | Add city              | Every time                       |
  | Tab change            | Every 5th                        |

  Rate-limiting is enforced in `AdManager.maybeShowInterstitial`. If the
  minimum interval has not elapsed, the call returns `false` and the user
  continues without interruption.

## Removing ads

Two options:

1. **Strip the AdMob call from the layout** — remove the
   `<FrameLayout android:id="@+id/ad_container" .../>` block from
   `res/layout/activity_main.xml` and delete the `setupBannerAd()` call in
   `MainActivity.onCreate`. Drop the `play-services-ads` dependency from
   `app/build.gradle.kts` and remove `AdManager.kt` plus the
   `meta-data com.google.android.gms.ads.APPLICATION_ID` line in the manifest.
2. **Premium unlock** — a future option. The architecture already isolates
   ad calls in `AdManager`, so you can add a `BillingClient` integration
   that flips a `showAds` flag in `SettingsRepository`.

## COPPA / GDPR

If you target EEA or California users, you must:

- Show a consent dialog before requesting ads. Use Google's
  [UMP SDK](https://developers.google.com/admob/ump/android/quick-start) by
  adding `play-services-ump` and calling `ConsentInformation.requestConsentInfoUpdate`
  before `MobileAds.initialize`.
- Mark the AdMob account as "tagged for child-directed treatment" if the app
  is aimed at children under 13.

The current build does not yet ship a consent dialog. Add the UMP integration
before publishing to the Play Store.

## Policy reminders

- The **banner ad is always above content**, not on top of it. The ad container
  is sibling-positioned to the bottom nav and never overlays the fragment.
- The **interstitial never fires within 60 s** of the previous one. This is
  enforced centrally in `AdManager` and not bypassable from feature code.
- The **app makes no ad-related network calls before consent** in production
  builds once you add UMP.
