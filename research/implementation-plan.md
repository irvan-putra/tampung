# Drive backup probe: package-neutral preparation

Status: local Java core only. Android shell source is being qualified. No release APK, OAuth registration or real Google round-trip exists yet.

## Checks completed

Run `java RunTests.java /path/to/drive-backup-probe` with JDK 11+. Sources are compiled for Java 8 compatibility. The 16 local tests use an in-memory fake remote and fake atomic local store. They do not prove Android storage atomicity or Google Drive authorization.

- Small versioned synthetic-only snapshots, maximum 64 KiB and 100 notes.
- SHA-256 integrity check, not encryption or authentication. Never use this format for real sensitive data without a separate reviewed security design.
- Upload creates a new snapshot and verifies downloaded bytes before reporting success. Old remote snapshots are not deleted.
- Restore validates the complete snapshot before asking the local storage adapter for one atomic replacement.
- Tests cover Unicode, corrupt and missing data, unsupported schema, offline transport, revoked consent simulation and local commit failure simulation.

## Android implementation plan after name choice

1. Create separate `com.vanipy.<chosen-name>` app, public generic source, copyleft license, manual synthetic test screen. No real file picker, contacts, vault or external storage permission. Disable OS cloud backup for the experiment and explicitly exclude dummy/local/token state in backup rules so OS restore cannot be mistaken for a Drive round-trip.
2. Start official Google Identity Services AuthorizationClient authorization only on a user-triggered Backup or Restore action. Request `https://www.googleapis.com/auth/drive.appdata`, not Drive-wide access. Keep access tokens in memory and out of logs, source, exports, screenshots and app backups.
3. Google Cloud setup needed: enable Drive API; configure branding, consent audience/test users and scope; register the Android package with the actual install signing certificate SHA-1. No service account, embedded client secret or assistant account connection is required for this phone-side flow. Do not enable billing or paid services.
4. Drive adapter creates snapshots with parent appDataFolder, lists only appDataFolder, downloads a selected snapshot and verifies bytes. Enforce response size while streaming, not just after download. A unique app-specific marker prevents accidental snapshot selection. Reauthorization should be handled through the official UI on consent revocation, with cancellation returning to idle.
5. Show selected Google account through the official authorization/account UX. Bind a backup/restore operation to that account; cancel in-flight work and clear account-scoped state on switching. Never automatically restore into local state on login.
6. Android local adapter must commit via AtomicFile or a database transaction and preserve local state on failures. Serialize concurrent actions. Disable duplicate taps during an operation. User explicitly chooses whether to clear only this app's dummy data for the restore test.
7. Qualification stages: Java core tests, Android build/lint/unit tests, emulator UI/fake adapter tests, then genuine Google-authorized upload/download/clear-local/restore test. Record digest, byte count, timestamp and success states without publishing account identifiers or payloads. Reinstall test is separate and requires matching package/signing registration and same Google account.
8. No scheduled background backup for the first test. No APK release/signing/publication without the scoped authority for that stage. Retention management and encryption are separate future work. Manual small-payload testing must stay within a no-billing-enabled configuration.

## Current sources checked November 18, 2026

- Google Drive app-specific storage: https://developers.google.com/workspace/drive/api/guides/appdata . App-private hidden appDataFolder with non-sensitive drive.appdata scope; distinct from Drive device backup folders. Users may delete app data from their Drive app connection.
- Android authorization: https://developer.android.com/identity/authorization . AuthorizationClient, action-time permission and Android OAuth package/signing SHA-1 registration.
- Scopes: https://developers.google.com/workspace/drive/api/guides/api-specific-auth . Choose minimal scope, avoid restricted Drive-wide access.
- Consent setup: https://developers.google.com/workspace/guides/configure-oauth-consent . Audience, branding and external test users as applicable.
- OS Auto Backup: https://developer.android.com/identity/data/autobackup . Separate system-managed 25 MB mechanism, not this experiment.
- WhatsApp behavior: https://faq.whatsapp.com/407643231403807/?cms_platform=android . Google Account/phone-number-linked backups, optional end-to-end encryption, each new backup replaces previous one. This project does not claim WhatsApp's internal implementation or its proprietary backup view.
- Usage and costs: https://developers.google.com/workspace/drive/api/guides/limits . Current documentation discusses 2026 changes. Do not claim unlimited permanently free API usage or attach a billing account.

## Awaited decisions

Name selected: `tampung`. Public repository and sourced research now exist. Developer Google OAuth registration and real-device qualification remain pending.
