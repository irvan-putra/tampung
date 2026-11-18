# tampung data architecture research

Research date: 2026-11-18. This is a design and threat-model proposal, not evidence of a working cloud backup. Prototype execution is limited to synthetic backup/restore. No Google, Delta Chat or AT Protocol account has been connected.

## Recommendation

Separate three jobs: Google Drive holds recovery snapshots, Delta Chat carries private chat and app sync operations, and AT Protocol repositories on a PDS hold explicitly published public posts and structured records. These are separate stores with separate permissions and deletion semantics. Sync must never be the only backup, and publishing must never happen as a side effect of sync.

Default backups to **append-only application behavior**: create a new independent snapshot, verify a download, retain earlier snapshots, and never automatically overwrite, prune or propagate a synced deletion into historical snapshots. This helps against accidental overwrites, bugs and ransomware that only changes the live dataset. It is **not immutable storage**. Google's `drive.appdata` scope permits permanent file deletion. A compromised authorized app or account can erase the same history. Do not market this design as ransomware-proof, WORM, a retention lock or end-to-end encrypted backup.

## Comparison

| Job | Proposed system | What it supplies | What the app still must do |
| --- | --- | --- | --- |
| Recovery | Drive `appDataFolder` | App-private cloud files and scoped Google authorization | Snapshot format, restore validation, retention, size/cost accounting, tested recovery, optional encryption/key recovery |
| Private sync and chat | Delta Chat | Established encrypted messaging and multi-device primitives | Operation IDs, idempotence, conflict policy, schema upgrades, offline catch-up and local persistence |
| Public publication | AT Protocol/PDS | Public account repositories, signed records, Lexicon schema ecosystem | Explicit publish review, collection/schema design, OAuth, moderation/indexing/AppView and deletion caveats |

## 1. Google Drive: own-app recovery

### Current facts

Google describes `appDataFolder` as a hidden app-specific space. Access is limited to the app, not other Drive apps or the normal file UI. It uses the non-sensitive `https://www.googleapis.com/auth/drive.appdata` scope. It is different from the reserved device-backup folder. Files cannot be shared, moved between spaces or trashed. Users can delete the app's data through Drive app management. [G1]

Critically, `files.delete` permanently deletes owned files without trash and accepts the `drive.appdata` scope. Hiding a delete button or omitting a delete endpoint in the current client does not remove the token's capability. App-private means access-isolated, not deletion-resistant or cryptographically opaque to the provider. [G1, G3]

Google's Android guidance recommends `AuthorizationClient` for access to Google data, requested at the moment a user needs that feature. The developer configures branding/consent and an Android OAuth client matching package and signing-certificate SHA-1. This is developer setup; an end user chooses/approves an account through Google's UI. No account password form, service account, backend refresh token or assistant-side Drive connection is needed for the planned manual phone-side test. [G2, G8]

Android OS Auto Backup is a separate system-managed feature: up to 25 MB per app, system-triggered backup/restore with its own rules. The test app must exclude its local and token state from OS backup so an OS restore cannot be mistaken for successful manual Drive recovery. [G6]

WhatsApp's documented Google Account backup is tied to its account/phone-number requirements, and each new backup replaces the previous one. Its implementation is not a public API for this project to reuse; tampung intentionally proposes independent retained snapshots instead of replacement. [W1]

### Proposed backup contract

- Manual, fixed synthetic notes only in the first app. No file picker, external storage permission, existing vaults or other-app backups.
- Create a uniquely identified snapshot file in `appDataFolder`, never update an existing content file. Record format version, snapshot ID, byte length, timestamp and content digest. A timestamp is not a trust boundary or secure ordering proof.
- Download and compare bytes and schema before marking the new snapshot verified. A lost response may create an orphan or duplicate; retry must reconcile by operation ID, not guess success.
- Keep a visible list of verified snapshots. Never rely on one mutable cloud index as the only discovery path; rebuild history from paginated file listing and validate content. Server ordering or a hash is not evidence that an authorized attacker did not delete older snapshots.
- Restore validates bounds, format, hash and all fields into a temporary structure, then replaces only this app's synthetic local data in a transaction. Failure preserves the original local data. User chooses a snapshot, sees a preview, and confirms the local replacement. No silent restore on login.
- Account switching cancels in-flight work and discards cached account-bound history. Show the chosen account using Google's account UX. Never log tokens or account identifiers in public test evidence.
- No automatic deletion, pruning or retention countdown in the initial prototype. Default: keep all created snapshots until an explicit, separate future deletion flow is designed and authorized. At a storage budget, stop new backups visibly instead of quietly deleting the oldest.
- Retention policy changes cannot claim to make previous snapshots immutable. Any future delete action needs a preview of exact snapshots/bytes and explicit user confirmation, not a synced tombstone.

### Threat boundary

| Failure or attacker | Append-only client helps? | Remaining risk |
| --- | --- | --- |
| Accidental live edit/deletion replicated to every device | Yes, earlier independent snapshots remain | Only if a usable earlier snapshot exists and restore works |
| Ransomware encrypts local app data, but does not obtain Google app credentials | Potentially | It can upload encrypted state as a new snapshot; show changed-data anomalies and do not prune earlier ones |
| Attacker runs code in authorized app or steals usable Drive token | Not an enforced defense | Token can update/delete app data, even if this version's UI cannot |
| Google account takeover, app connection removed with data deletion, provider failure | No guarantee | Need an independent recovery copy/security boundary |
| Storage exhaustion from rapid new snapshots | No | Manual start, per-snapshot size/count/rate caps and visible storage budget; stop rather than prune |
| Secret backup payload exposed | No | This synthetic format is plaintext with a checksum; real-data encryption/key recovery is future work |

For stronger deletion resistance, evaluate a separate independently controlled copy or a provider-enforced retention/object-lock service with credentials separated from the backup writer. That is a future option, not a property of personal Drive `appDataFolder`, not a purchase recommendation and not authorized deployment. Test provider semantics before any immutable-storage claim. More copies using the same compromised account/token are not independent defenses.

### Why not overwrite one file and keep revisions?

Drive binary revisions can be purged; `keepForever` pins at most 200 revisions per file, and the API supports permanent revision deletion. Pinned revisions also consume storage. This is neither an indefinite-history guarantee nor a retention lock. Separate snapshot files make retention and recovery accounting explicit, though they remain deletable by authorized credentials. [G4, G5]

### Costs and history visibility

Make the following part of the user-facing storage screen before enabling recurring backups:

1. Snapshot count, verified/unverified counts, actual bytes retained by this app, oldest and newest verified dates, last restore-test result, and a plain deletion-resistance warning.
2. Selected retention policy, currently keep-all with no automatic deletion, and a storage budget independent of the provider's overall account quota.
3. Estimated additional storage for 7/30/365 days based on observed snapshot sizes and frequency. Show the assumptions: full snapshot size, backups/day, compression, duplication and any proposed thinning. Label estimates, never call them provider bills.
4. Where available under existing scope, use `about.storageQuota` for account limit/usage. Values may describe pooled organizational storage; absent limit does not mean zero. Verify the endpoint works with the final scope during real testing. If it does not, keep app-only accounting and link to provider storage management rather than expand scope silently. [G7]
5. Show the provider plan and currency price only if obtained from the user's current plan or a current regional official offer, and label its verification date. If unknown, say unknown. Marginal cash cost within an existing plan is not accurately calculated by multiplying app bytes by a made-up per-GB rate. No upgrade, payment, billing-account attachment or auto-top-up.
6. Separate storage-plan cost, data/mobile-network cost and API/project cost. The current Drive API usage page describes updated 2026 quotas and planned charging changes; do not promise permanently unlimited free API use. Recheck before production. This prototype will not enable billing. [G9]

Illustrative math only: one full 1 MiB snapshot per day retained 365 days uses roughly 365 MiB before metadata or compression. At 10 MiB/day the same policy is roughly 3,650 MiB. Deduplication can reduce bytes but adds restore-chain, privacy and corruption risks, so it is not assumed in the first test.

## 2. Delta Chat: sync and chat, not the only recovery store

Delta Chat's documented webxdc design has durable-style updates sent through the messenger (`sendUpdate`) and separate ephemeral realtime P2P channels. Realtime state is not a backup. Its example LiveChat loses all history after everyone closes it. Peer IP exposure is also possible with P2P. [D1]

Webxdc's shared-state guide explicitly warns that peers can see updates in different orders and requires apps to choose conflict handling, such as suitable CRDTs. `sendUpdate` has payload/rate constraints and can fail to send when the user cannot send to the chat. Its return is not a remote durable-commit receipt. Listener serial numbers are local replay positions, not globally unique operation IDs or proof every peer acknowledged a change. [D2, D3, D4]

Design implications:

- Separate transport success, peer acknowledgement and durable local application state. Use operation IDs, schema versions, replay protection, idempotent application and account/device identity boundaries.
- Decide per data type whether concurrent edits merge, make versions, or need a human choice. Do not claim Delta Chat magically provides database transactions or all conflict rules.
- Queue offline updates, process out of order and test reconnect/duplicate/replayed updates. Snapshots remain independent from synced deletion events.
- Native Android integration is not automatically the same API as an embedded webxdc app. A future adapter must choose and test the correct Delta Chat interface and preserve its license/security model. No Delta Chat integration is implemented in this backup prototype.

## 3. AT Protocol: public posts AND structured data

AT Protocol stores public per-account records in repositories hosted by a PDS. Repositories are signed and content-addressed. Lexicon defines record collections and schemas, so structured public app data can use custom collections instead of encoding everything into Bluesky posts. Bluesky is one app/AppView on the protocol, not a synonym for every custom collection. Custom records do not automatically appear in the Bluesky feed; a reader/indexer/AppView must understand them. [A1, A2, A3]

A signed repository proves integrity/authorship within the protocol model; it does not make data confidential or immutable. Repository mutations and deletion are supported. Network consumers can already have copied public data, so deletion from the source is not a promise of global erasure. Do not put encrypted private sync, backup manifests, account identifiers, secrets or sensitive object names in the public repository simply because encryption exists elsewhere. [A1]

Proposed public publication boundary:

- An explicit review screen separates private local/synced state from the exact post or structured record being published.
- Use a documented namespace/Lexicon for custom public data, stable IDs, schema versions, clear provenance and only deliberately public fields. Preserve unknown fields when round-tripping records so older clients do not silently clobber newer data. [A3]
- Research OAuth, account portability/export, PDS provider policies/limits/costs and moderation/discovery before implementation. Running a personal PDS is optional, not assumed or free by default.
- No PDS deployment, Bluesky posting, public account connection or structured record creation is part of the current prototype execution.

## 4. Qualification and next gates

Completed: 16 host-Java synthetic codec/engine tests using a fake remote and fake atomic local store. These establish only that tested local logic works under the simulated conditions. They do not establish Android storage atomicity, OAuth, cloud upload/download, device reinstall recovery or security against an active attacker.

Android qualification update: `com.vanipy.tampung` debug compilation, lint and API 35 Pixel 2 emulator local generate/snapshot/clear/restore smoke passed at source 070b57b4232828006a366e6a08de6c54fb873d33. Both dummy fields were checked after restore and Google buttons verified disabled. The initial/restored/cloud-disabled screenshots were inspected. Workflow: https://github.com/irvan-putra/tampung/actions/runs/44307668287 . There are 16 host core tests; the Gradle unit-test task had no separate Android unit-test sources. This is not a real Google or physical-phone result.

Next: developer-side Google setup and real on-device approval. A live test needs a stable install certificate registered with OAuth, Drive API enabled and a test account/audience configuration. No signing/release authority is inferred from choosing the name or publishing research.

Real cloud test acceptance: new snapshot created, downloaded bytes match, local dummy data is cleared only after user action, selected snapshot restores every field, existing snapshots remain, no other Drive files are accessible, and cancellation/offline/revoked consent/corruption preserve local state. Record exact source/build and evidence without credentials or personal data. A reinstall/second-device recovery test is a separate qualification stage.

## Sources

All links below were read during this research. Official documentation is primary evidence for API behavior, not a claim that this prototype has executed those APIs. Missing or changing provider policy requires a fresh check before production.

- G1. Google, Store application-specific data: https://developers.google.com/workspace/drive/api/guides/appdata
- G2. Android, Authorize access to Google user data: https://developer.android.com/identity/authorization
- G3. Google Drive files.delete API: https://developers.google.com/workspace/drive/api/reference/rest/v3/files/delete
- G4. Google Drive revisions resource: https://developers.google.com/workspace/drive/api/reference/rest/v3/revisions
- G5. Google Drive manage revisions: https://developers.google.com/workspace/drive/api/guides/manage-revisions
- G6. Android Auto Backup: https://developer.android.com/identity/data/autobackup
- G7. Google Drive about resource and storageQuota: https://developers.google.com/workspace/drive/api/reference/rest/v3/about
- G8. Google, Configure OAuth consent: https://developers.google.com/workspace/guides/configure-oauth-consent
- G9. Google Drive usage limits: https://developers.google.com/workspace/drive/api/guides/limits
- W1. WhatsApp, About Google Account backups: https://faq.whatsapp.com/407643231403807/?cms_platform=android
- D1. Delta Chat, P2P networking introduction (2024-11-20): https://delta.chat/en/2024-11-20-webxdc-realtime
- D2. Webxdc, sendUpdate specification: https://webxdc.org/docs/spec/sendUpdate.html
- D3. Webxdc, setUpdateListener specification: https://webxdc.org/docs/spec/setUpdateListener.html
- D4. Webxdc, Shared Web Application state: https://webxdc.org/docs/shared_state/
- A1. AT Protocol, Repository specification: https://atproto.com/specs/repository
- A2. AT Protocol, Understanding Atproto: https://atproto.com/guides/understanding-atproto
- A3. AT Protocol, Lexicon specification: https://atproto.com/specs/lexicon
