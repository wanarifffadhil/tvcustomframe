# CleanFrame for Philips Android TV

A port of [CleanFrame](https://github.com/nthnclrk/CleanFrame) (MIT) for Android TV, set up for a
Philips 55PUT7906/68. Home shows **Netflix, YouTube and PS5 (HDMI 1)**.

## How it works

- `android/app` is a small Android TV app. It's a full-screen WebView that shows the CleanFrame UI
  from inside the APK. A Java bridge (`MainActivity.java`) lets it:
  - list and open installed apps
  - open the TV settings or the original Home screen
  - switch HDMI inputs
- `android/web/device.js` replaces the Samsung-only `app/device.js` in the Android build. It also
  sets the Home apps (`primaryApps`) and hides Samsung's Art Mode.
- `app/` is shared with the Samsung version. `app/main.js` reads `window.CleanFramePlatform` for
  the brand name, apps and features. Without it, `app/main.js` behaves exactly like upstream.
- Nothing goes to the network, and no account data is read.

## Get the APK

Every push to `app/`, `android/` or `scripts/` builds the APK with GitHub Actions
(`.github/workflows/android.yml`). The build publishes it to the `android-latest` release:

https://github.com/wanarifffadhil/tvcustomframe/releases/download/android-latest/CleanFrame-Philips.apk

## Install on the TV (about 10 minutes)

1. On the TV, install **Downloader** (by AFTVnews) from Google Play.
2. Allow Downloader to install apps. Go to **Settings → All settings → Android settings →
   Device Preferences → Security & restrictions → Unknown sources**, then turn **Downloader** on.
   Menu names may differ slightly.
3. Open Downloader, enter the APK link above and choose **Install**.
4. Open **CleanFrame** from the Apps row. Check that Netflix, YouTube and PS5 each open.
5. Optional: make it the Home screen. Press Home. If Android asks which Home app to use, choose
   CleanFrame → *Always*. If it doesn't ask, keep opening CleanFrame from the Apps row.

## Updates and the signing key

Without signing secrets, CI signs each build with a new throwaway key. So to update, **uninstall
CleanFrame first**, then install the new APK. That resets its Home settings, which are only three
apps.

To let updates install over the old version, add your own key once:

1. On any computer with Java, create a key:

   ```sh
   keytool -genkeypair -storetype PKCS12 -keystore sideload.p12 -alias sideload -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=CleanFrame"
   ```

2. In GitHub, go to **Settings → Secrets and variables → Actions** and add two secrets:
   - `ANDROID_KEYSTORE_B64`: the output of `base64 -w0 sideload.p12`
   - `ANDROID_KEYSTORE_PASSWORD`: the password you chose
3. Keep `sideload.p12` private and backed up. Never commit it.

## Known limits

- **HDMI switching** uses Android's standard input link (`TvContract` passthrough). Some Philips
  firmware ignores it. If PS5 does nothing, use the remote's Sources button.
- **Back** is handled inside CleanFrame. It never closes the app.
- **Art Mode** is Samsung-only, so it's hidden.
- Not yet tested on a real Philips TV.

## Build locally

```sh
npm ci && npm run export:public
gradle -p android assembleRelease   # Gradle 8.11, JDK 17, Android SDK 34
```
