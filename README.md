# tampung

Synthetic Android Google Drive backup-and-restore experiment, with public research on a local-first app data architecture.

## Research

Read [the sourced research](research/data-architecture.md):

- Google Drive for independent recovery snapshots, append-only by default in the app.
- Delta Chat for private sync and chat.
- AT Protocol/PDS for explicitly published posts and structured public records.
- Retention, storage budgets and cost visibility.
- Threat limits: app-private Drive is not immutable storage. Authorized credentials can permanently delete history. A checksum is not encryption or authentication.

## Current state

Package-neutral Java snapshot codec and backup engine, with 16 passing host tests using synthetic data and an in-memory fake remote. Android shell is the next step. **No Android APK, Google OAuth configuration, genuine cloud round-trip or device restore has been verified.** No accounts, paid services or billing have been connected.

Run the host checks with JDK 11 or later:

```sh
java RunTests.java "$PWD"
```

The core targets Java 8 compatibility. [Implementation plan](research/implementation-plan.md) and [qualification boundaries](research/data-architecture.md#4-qualification-and-next-gates).

## Backup defaults

Create a new snapshot, verify downloaded bytes, preserve older snapshots. No automatic overwrite, pruning or deletion. Stop visibly at a storage budget instead of erasing recovery history. The first format is strictly for dummy notes, not real secrets. It enforces a 64 KiB snapshot limit and 100-note limit.

Publishing to AT Protocol and private Delta Chat sync are research only, not implemented features. Generic code and research belong in this repository; credentials, OAuth tokens, private payloads and account-specific test evidence do not.

## License

AGPL-3.0-or-later for this project's original code. See [LICENSE](LICENSE). External libraries retain their own licenses.
