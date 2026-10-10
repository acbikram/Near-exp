# Near Expiry v3.31 — Technical Audit

## Changes in this patch

- Bumped Android versionName to 3.31 and versionCode to 142.
- Fixed CSV expiry-date parsing for month names in uppercase, lowercase, or title case while preserving separators.
- Added regression tests for `28-SEP-26`, `28-sep-26`, and `28-Sep-2026`.
- Hardened update APK downloads: reject empty files, verify the final cached APK exists and is non-empty, and fall back to copying if rename fails.
- Hardened GitHub Actions release signing: require the release keystore and all signing secrets, decode the keystore, and validate the configured alias before building. The workflow no longer intentionally publishes a debug-signed release when signing secrets are absent.

## Build validation status

A successful GitHub Actions run is required before treating the release as validated. Release publication also requires the repository's existing permanent signing key to be configured in Actions secrets. Do not replace that key, because doing so can prevent in-place updates for existing installations.

## Next steps

1. Review and merge the pull request.
2. Confirm the Android CI quality job passes.
3. Confirm the release signing secrets are present and correct.
4. Download the generated APK from the successful Actions run or GitHub Release.
