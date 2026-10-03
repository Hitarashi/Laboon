# Release signing

The tag release workflow signs the APK with a Java keystore stored in GitHub Actions secrets. Add
these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret                      | Value                                |
|-----------------------------|--------------------------------------|
| `ANDROID_KEYSTORE_BASE64`   | Base64-encoded release keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password                    |
| `ANDROID_KEY_ALIAS`         | Signing key alias                    |
| `ANDROID_KEY_PASSWORD`      | Signing key password                 |

Use the same keystore for every release so existing installs can be upgraded. The workflow decodes
it into the runner's temporary directory, signs the release APK, verifies its signature, and
attaches it to the GitHub Release for the pushed tag.
