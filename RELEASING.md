# Releasing Juzzics (APK on GitHub)

Every release is built by GitHub Actions (`.github/workflows/release.yml`), signed with the
release key, and attached to a GitHub release. Testers download the APK from
**https://github.com/ggabidauri-tbc/Juzzics/releases/latest**.

## Signing: testing key now, release key later

All releases must be signed with the **same key**, or phones refuse to install an update over
the previous version.

**For now (testing):** nothing to set up. Without the secrets below, builds are signed with the
shared testing key in the repository (`app/testing.keystore`, password `juzzics-testing`). It's
public on purpose: fine for test builds, but anyone could sign an app that looks like an update.

**Later (real releases):** make your own key once and add it as secrets. Switching keys means
testers uninstall the test version once (Android won't update across keys), then updates work
as before. Keep the key safe (a password manager), never commit it.

1. Make the key (Android Studio's Java has `keytool`):

   ```sh
   "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool" -genkeypair -v \
     -keystore ~/juzzics-release.jks -alias juzzics \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

   It asks for a password and a few details (name is enough).

2. Turn it into text for GitHub:

   ```sh
   base64 -i ~/juzzics-release.jks | pbcopy
   ```

3. On GitHub: **Settings > Secrets and variables > Actions > New repository secret**, add:

   | Secret             | Value                                  |
   |--------------------|----------------------------------------|
   | `KEYSTORE_BASE64`  | what step 2 copied                     |
   | `KEYSTORE_PASSWORD`| the password from step 1               |
   | `KEY_ALIAS`        | `juzzics`                              |
   | `KEY_PASSWORD`     | the password from step 1 (same one)    |

## Making a release

On GitHub: **Releases > Draft a new release**, choose a tag like `v1.1` (create it), give it a
title and notes, **Publish**. GitHub Actions then builds the APK from that tag and adds
`Juzzics-1.1.apk` to the release (a few minutes; watch it in the **Actions** tab).

The "Source code (zip / tar.gz)" files GitHub adds to every release are just the code; testers
want the `.apk`.

For a quick test build without a version: **Actions > Release APK > Run workflow**. It shows up
as a pre-release `build-<number>`.

## Installing (for testers)

Open the APK on the phone. Android asks once to allow installing apps from the browser / files
app. Updates install over the old version; songs, lyrics and remembered friends stay.

Note: an APK started from Android Studio's Run button is a test build that can't be shared
(Android refuses to install it elsewhere). Share the one from the releases page.
