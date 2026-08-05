# AdMob monetisation

Future Clock ships anchored adaptive banner ads. They are shown above the bottom
navigation after the Google User Messaging Platform (UMP) has collected the
current session's consent state.

The first public release deliberately has no interstitials. Dismissing an alarm
and changing tabs are not meaningful breaks in a timekeeping task, so full-screen
ads there would be disruptive. Banner inventory provides a policy-friendly,
respectful starting point for revenue.

## Production setup

1. Create the Android app in [AdMob](https://admob.google.com/) with the final
   package name: `com.futureclock.app`.
2. Create one **anchored adaptive banner** ad unit and copy the App ID and banner
   unit ID.
3. Copy [keystore.properties.example](./keystore.properties.example) to
   `keystore.properties` at the repository root. This file is ignored by Git.
4. Set the AdMob entries using your values:

   ```properties
   admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
   admobBannerAdUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
   ```

   You may instead supply `ADMOB_APP_ID` and `ADMOB_BANNER_AD_UNIT_ID` as
   environment variables in CI.
5. In AdMob, create and publish the UMP privacy message for the app. UMP uses
   the App ID to load that message.

The debug variant always uses Google's official test App ID and banner unit ID.
It cannot request paid ads. The release build injects your IDs from local
configuration and refuses to build if either one is missing.

## Consent and privacy

- UMP updates consent information on every launch before Mobile Ads initializes
  or an ad request is made.
- If consent cannot be collected on a first install, the app does not request
  ads. A previously valid consent state can continue to be used when a refresh
  temporarily fails, as allowed by UMP.
- **Settings → Privacy choices → Manage** lets people reopen the UMP privacy
  options form whenever it is available.
- You still need a public privacy-policy page and must add its URL in Google
  Play Console and in the AdMob privacy/disclosures flow.

## Testing safely

Use the debug build or mark your own physical device as a test device in AdMob.
Android emulators are automatically recognized as test devices. Never click
production ads while testing; it can be treated as invalid traffic. See Google's
[test-ad guidance](https://developers.google.com/admob/android/test-ads).

## app-ads.txt

After the app is listed on Google Play, add the developer website to the store
listing and host an `app-ads.txt` file on that verified domain. AdMob uses it to
verify authorised sellers and improve monetisation eligibility.
