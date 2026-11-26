# tansu

A minimal Android app that turns business-card photos in a folder into vCard/CSV, using ChatGPT plan sign-in or an OpenAI-compatible API.

This repository currently contains the project scaffold only. The app feature is
not implemented yet; the agreed domain terms and decisions live in
[GLOSSARY.md](GLOSSARY.md) and [docs/adr](docs/adr).

## Requirements

- Linux x86_64 with Nix (flakes enabled)
- Optional: `/dev/kvm` for the emulator

The Android SDK, JDK 21, Gradle-facing tools, ADB, and Maestro come from the
Nix dev shell. Do not install SDK components with `sdkmanager`; change
`flake.nix` and re-enter the shell instead.

## Getting started

```bash
nix develop
android-dev doctor
```

Point Gradle at the SDK from the dev shell. `local.properties` is ignored by
Git, so create it locally with the value of `$ANDROID_HOME`:

```bash
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
```

Build and check:

```bash
android-dev gradle assembleDebug testDebugUnitTest lintDebug
```

The pinned SDK is API 36 with Build Tools 36.0.0. The app uses
`compileSdk = 36`, `minSdk = 26`, and `jvmToolchain(21)`.

## Emulator

`emulator = false` is the default in `flake.nix`, so image downloads and AVD
setup are skipped for build-only work and CI. Enable it only when running
Maestro flows:

1. Set `emulator = true` in `perSystem.ashiba.android` in `flake.nix`.
2. Re-enter the shell and create/start the AVD:

   ```bash
   nix develop
   android-dev avd-create
   android-dev avd-start
   ```

3. Install the APK and run the flow from another shell:

   ```bash
   adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
   android-dev test -e APP_ID=dev.yutakobayashi.tansu -e READY_TEXT=tansu \
     .maestro/smoke.yaml
   ```

Return `emulator` to `false` before committing unless a change genuinely needs
an emulator in CI.

## Layout

```
app/                  Android application module
  src/main/           production sources and resources
  src/test/           JVM unit tests
  src/androidTest/    instrumented tests
docs/adr/             architecture decision records
.maestro/             Maestro UI flows
flake.nix             development shells and pinned Android SDK
GLOSSARY.md           domain terms used by the app and docs
```

## Agent skills

Entering the dev shell deploys the Android skills into `.agents/`, `.codex/`,
`.claude/`, and `.opencode/`. These directories are generated and ignored by
Git; keep repository-owned skills elsewhere.

## License

Apache-2.0. See [LICENSE](LICENSE).
