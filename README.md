# tampung

Synthetic Android Google Drive backup-and-restore experiment, with public research on a local-first app data architecture.

## Research

Read [the sourced research](research/data-architecture.md) and the follow-up [gratis append-only backup options](research/gratis-append-only-backups.md), plus [Oracle Always Free suitability](research/oracle-always-free-backup.md):

- Google Drive for independent recovery snapshots, append-only by default in the app.
- Delta Chat for private sync and chat.
- AT Protocol/PDS for explicitly published posts and structured public records.
- Retention, storage budgets and cost visibility.
- Threat limits: app-private Drive is not immutable storage. Authorized credentials can permanently delete history. A checksum is not encryption or authentication.

## Current state

- Java snapshot codec and backup engine: **16 passing host tests** with synthetic data and an in-memory fake remote.
- Separate Android source: `com.vanipy.tampung`, version 0.1.0, lowercase launcher, purpose icon and numeric badge. Local AtomicFile store, explicit snapshot/clear/restore steps, and an official AuthorizationClient/Drive REST adapter.
- Google operations are **disabled at build time** (`DRIVE_CONFIGURED=false`). They must remain disabled until developer OAuth setup and install-certificate registration are complete. Tokens stay in memory and there is no delete/update endpoint in the adapter. This does not restrict what a compromised authorized credential could do through Google's API.
- Android debug build, lint and API 35 Pixel 2 emulator local smoke passed for [070b57b](https://github.com/irvan-putra/tampung/commit/070b57b4232828006a366e6a08de6c54fb873d33): [qualification run](https://github.com/irvan-putra/tampung/actions/runs/44307668287). The UI test generated dummy notes, created a local snapshot, cleared the local notes, restored both exact fields and verified both Google controls disabled. Emulator screenshots were inspected for readable initial, restored and disabled-Google states. This is not a phone or Google backup claim. There is no published APK release.

**No OAuth project, genuine Google upload/restore, account selection, reinstall recovery, Android-device test or production security review has been verified. No accounts, paid services or billing have been connected.**

Run the host checks with JDK 11 or later:

```sh
java RunTests.java "$PWD"
```

The core targets Java 8 compatibility. Android builds require JDK 17, Gradle 8.9 and Android SDK 35:

```sh
gradle -p android testDebugUnitTest lintDebug assembleDebug
```

The GitHub Actions workflow builds a temporary debug APK only to run synthetic emulator tests. It does not release an APK or establish a stable release-signing identity. OAuth will need the certificate of the actual install build, not a guessed or changing CI debug certificate.

## Backup defaults

Create a new snapshot, verify downloaded bytes, preserve older snapshots. No automatic overwrite, pruning or deletion. Stop visibly at a storage budget instead of erasing recovery history. The first format is strictly for dummy notes, not real secrets. It enforces a 64 KiB snapshot limit and 100-note limit. The future live test adapter uses a 1 MiB experiment budget based on the current marked history listing, not a provider-enforced or cross-device atomic quota.

The Android screen distinguishes in-memory local simulation, persistent local dummy notes, and disabled Google operations. Local simulation snapshots disappear when the process exits. OS cloud backup and device-transfer data are excluded so a system restore is not mistaken for the Drive test.

Publishing to AT Protocol and private Delta Chat sync are research only, not implemented features. Generic code and research belong in this repository; credentials, OAuth tokens, private payloads and account-specific test evidence do not.

## License

AGPL-3.0-or-later for this project's original code. See [LICENSE](LICENSE). External libraries retain their own licenses.

---

Built with AI assistance (Instinct and others). Not everything has been reviewed by Irvan.
