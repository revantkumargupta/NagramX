# Nagram X

Nagram X is a maintained fork of Nagram based on Telegram Android. Current maintained source and releases are published from `revantkumargupta/NagramX`.

## Downloads

- [GitHub Releases](https://github.com/revantkumargupta/NagramX/releases)
- [Telegram Channel](https://t.me/NagramX)

The first maintained release is [`v12.10.5-1262`](https://github.com/revantkumargupta/NagramX/releases/tag/v12.10.5-1262), based on Telegram Android `12.10.5 (7105)`.

## Verify APK

Current maintained APKs use this Android signing certificate:

- Package name: `nu.gpu.nagram`
- Version name: `12.10.5`
- APK version code: `1262`
- SHA-256: `00:11:40:4B:3D:F9:D1:A0:87:68:CD:FD:42:AE:76:73:5F:30:B2:6F:E3:2C:37:05:67:56:8C:76:BD:A7:36:87`

Older archived APKs from the previous maintainer used a different signing certificate. Android will not install an update across different certificates, so users moving from an older/debug/test build may need to uninstall once before installing the current maintained release.

## Updates

The in-app updater checks GitHub Releases at `https://api.github.com/repos/revantkumargupta/NagramX/releases` and opens the matching APK download URL. Android still requires the user to confirm APK installation; silent OTA updates are not possible for sideloaded builds.

For future releases:

- Sign every APK with the same maintained release key.
- Increment the APK `versionCode` in `TMessagesProj/build.gradle` (`verCode`).
- Keep `APP_VERSION_NAME` and `APP_VERSION_CODE` in `gradle.properties` aligned with the upstream Telegram version.
- Create a GitHub Release with APK assets containing ABI names such as `arm64-v8a`, `x86_64`, or `universal`.
- Include a parseable APK version code in the release title or body, for example `NagramX v12.10.6 (1263)` or `Version code: 1263`.

## Compilation Guide

1. Clone the repository with its submodules:

   ```bash
   git clone --recursive --shallow-submodules https://github.com/revantkumargupta/NagramX.git NagramX
   ```

   You will need Android Studio, Android NDK `27.2.12479018`, Android SDK platform `37`, and JDK `21`.

2. If you already cloned the repository without submodules, run:

   ```bash
   git submodule update --init --recursive --depth=1
   ```

3. Obtain API credentials (`TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH`) from the [Telegram Developer Portal](https://my.telegram.org/auth). Create `local.properties` in the project root with:

   ```properties
   TELEGRAM_APP_ID=<your_telegram_app_id>
   TELEGRAM_APP_HASH=<your_telegram_app_hash>
   ```

4. For signed APKs, place your release keystore at `TMessagesProj/release.keystore` and add signing configuration to `local.properties`:

   ```properties
   KEYSTORE_PASS=<your_keystore_password>
   ALIAS_NAME=<your_alias_name>
   ALIAS_PASS=<your_alias_password>
   ```

5. For FCM support, replace `TMessagesProj/google-services.json` with your own configuration file.

6. Replace project-specific metadata if you fork this project:

   - Set your Google Maps API key in the `com.google.android.maps.v2.API_KEY` meta-data entry in `TMessagesProj/src/main/AndroidManifest.xml`.
   - Set `BaseRemoteHelper.CHANNEL_METADATA_ID` in `TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/remote/BaseRemoteHelper.java` to your metadata channel's numeric ID, without the `-100` prefix.
   - Update `GITHUB_RELEASES_API` in `TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/remote/UpdateHelper.java` to your release repository.

7. Build locally:

   ```bash
   ./gradlew :TMessagesProj:assembleRelease
   ```

## GitHub Actions Build

The workflows can build from this repository or a fork. For official signed builds, configure these GitHub Actions secrets:

- `LOCAL_PROPERTIES`: Base64-encoded `local.properties` containing `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS`, `TELEGRAM_APP_ID`, and `TELEGRAM_APP_HASH`.
- `RELEASE_KEYSTORE_BASE64`: Base64-encoded release keystore file.
- `HELPER_BOT_TOKEN`: Telegram bot token from [@Botfather](https://t.me/BotFather).
- `HELPER_BOT_TARGET`: Primary Telegram chat ID.
- `HELPER_BOT_CANARY_TARGET`: Chat ID for test builds and metadata.

The maintained release key must never be committed to the repository.

## Acknowledgments

- [AyuGram](https://github.com/AyuGram/AyuGram4A)
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram)
- [Dr4iv3rNope](https://github.com/Dr4iv3rNope/NotSoAndroidAyuGram)
- [exteraGram](https://github.com/exteraSquad/exteraGram)
- [Nagram](https://github.com/NextAlone/Nagram)
- [Nekogram](https://github.com/Nekogram/Nekogram)
- [OctoGram](https://github.com/OctoGramApp/OctoGram)
