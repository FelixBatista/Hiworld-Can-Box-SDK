# Automated releases

`.github/workflows/release.yml` runs on every push to `main` and on manual dispatch. It tests the portable core, creates a versioned SDK JAR, builds a signed Probe APK, attaches both files and `SHA256SUMS` to a GitHub Release, and tags the exact commit as `build-<GitHub run number>`.

The release artifact versions include the GitHub run number, for example `0.5.1-build.42` for the SDK and `0.5.2-build.42` for the Probe. The Probe uses Android version code `100000 + run number`, so every published APK is eligible to update an earlier release APK.

## One-time GitHub setup

Create these repository Actions secrets before the first release:

- `HIWORLD_PROBE_SIGNING_P12_BASE64`: Base64 representation of the existing `debug.p12` used to sign the installed Probe.
- `HIWORLD_PROBE_SIGNING_PASSWORD`: password for that PKCS#12 file and the `probe` alias.

The existing key must be used. A new key would produce an APK that Android cannot install as an update over the Probe already on the head unit.

On the trusted build server, encode the existing key without printing the value into a shell history or committing it:

```text
base64 -w 0 "$HOME/.local/share/teyes-probe-signing/debug.p12"
```

Copy that output directly into the first GitHub Actions secret. Copy the contents of the corresponding `password` file into the second secret. Do not add either value to Git, issues, pull requests, or release notes.

After the secrets are configured, push any commit to `main` or start **Release SDK and Probe** manually from the Actions tab. The workflow fails before publishing when a secret is missing.
