# Gratis append-only backup options

Research date: 2026-11-18. This supplements [the architecture research](data-architecture.md). Research only: no backend replacement, service account, bucket, retention lock, billing subscription or deployment has been created. The existing Drive prototype remains unchanged.

## Answer

There are two useful routes:

1. **Free software on storage you already own: restic + rest-server in append-only mode.** A separately controlled server rejects overwrite/deletion by the backup client. This is the clearest option when the goal is a compromised phone being unable to erase previous backups, and a PC/NAS/server is already available. Hardware, disk capacity, power, network and maintenance are not free hosted storage.
2. **Small free hosted allowance: Backblaze B2 + a tightly scoped writer key, with Object Lock if a defined retention window is wanted.** The first 10 GB of storage is advertised as always free, account creation advertises no credit card, and Object Lock has no extra feature fee. This is a candidate for a small experiment, not unlimited free history or a blanket zero-billing/account-takeover guarantee. Its quota behavior and no-payment state must be checked on the actual account before enabling a client.

For an absolute zero-new-service-bill requirement, the first option is the stronger recommendation **if existing separately controlled storage is available**. If not, B2 is worth evaluating within its free allowance, but do not add payment details, raise spending limits or silently turn it into pay-as-you-go storage. Neither option is implemented or runtime-tested in tampung.

## Threat boundary first

Append-only means the **backup-writing credential** cannot erase or replace previous recovery data. It does not mean that the storage administrator, provider, account-closure path, failed disk or physical attacker cannot destroy it. A phone holding both upload and server-admin credentials has defeated the separation.

A malicious writer can also fill the available space, upload useless new snapshots or falsify timestamps. Retained bytes, count, last verified restore, anomaly alerts and a server-enforced quota are part of the design. At the limit, stop new writes visibly instead of automatically deleting the oldest good copy.

For protection against storage-admin compromise as well, use a separately administered/offline copy or a proven provider-enforced retention model with its account/provider limitations understood. No infinite free, unerasable service was established by this research.

## Comparison

| Option | What is gratis | Enforced against compromised writer? | Admin/account limit | Capacity/cost and Android fit |
| --- | --- | --- | --- | --- |
| restic + rest-server `--append-only` | Open-source software | Server rejects modification and deletion of existing repository backup content through that endpoint | Server filesystem/admin can still erase it; cleanup credentials must remain separate | Your disk/server, power and network. Server `--max-size` quota. Needs a tested Android restic integration; not an arbitrary-file upload endpoint |
| Backblaze B2 narrow application key | First 10 GB storage allowance, advertised no-card signup | Omit `deleteFiles`, restrict one private bucket/prefix; old file versions must remain discoverable. Add Object Lock for timed retention | Master/account credentials are a different threat; no-delete key alone is not an account-admin defense | Native/S3 API feasible in Android but needs a separate adapter and key handling, not Google OAuth. More storage/downloads can cost money |
| B2 Object Lock compliance mode | Lock feature no extra charge; storage still counts | Provider rejects changes/deletion of retained versions before expiry; governance mode can be bypassed by privileged keys, compliance cannot ordinarily be removed/shortened | Provider docs also describe account closure as a response to an excessively long lock, so do not claim survival of full account compromise or provider termination | Lock period 1–3,000 days in current docs. Mistaken/garbage locked uploads cannot simply be pruned to reclaim quota |
| Borg 1.x `borg serve --append-only` | Open-source software | Preserves low-level segment history against a restricted Borg client | Delete/prune can still change the visible archive set; admin compaction can make malicious deletions permanent; filesystem `rm` is outside Borg | Own disk/server and SSH/crypto integration; less straightforward for the current small Android snapshot experiment |
| Cloudflare R2 bucket locks | 10 GB-month Standard storage, 1M Class A and 10M Class B operations in monthly allowance | Bucket policy blocks object overwrite/delete while enabled, with writer-only credentials separated from bucket config | An administrator with bucket-config access can remove/change the rules | Requires enabling an R2 subscription via checkout and uses usage-based billing beyond allowances. Not preferred for a no-surprise-bill request |

## Route A: restic + rest-server on an existing machine

The official rest-server README documents `--append-only`: new backups can be created, existing backups cannot be modified/deleted through that mode. It also exposes `--max-size` for the repository and HTTPS/authentication support. This is a server capability boundary, unlike the current Drive app choosing not to call a permitted delete method. [R1]

This server speaks the **restic repository protocol**. The existing tampung snapshot bytes cannot simply be POSTed to it as arbitrary backup files. A future integration would need a maintained restic client/library/process bridge and its encryption/password-recovery model, or a different well-reviewed server protocol. It must respect that project's license and be tested on Android. No home-grown cryptography or hand-written imitation of the restic format is proposed.

Suggested evaluation design, not deployment:

- Storage/admin identity separate from the phone. Only the authenticated append-only backup endpoint is reachable by the phone, not shell/filesystem/admin APIs.
- Repository quota at the server; keep plenty of headroom for recovery operations and stop-on-full status in the app.
- Client-side backup encryption using established restic functionality, with recovery material stored independently. Append-only is not confidentiality.
- Maintenance from a separate trusted machine, not a second unrestricted credential on the phone. No automated keep-last-N pruning.
- Synthetic acceptance tests: upload two snapshots; attempt deletion and overwrite using the writer credentials and require refusal; restore both snapshots; try replay/garbage and quota exhaustion; verify old data survives. Test actual restore after the phone's local state is gone.

### Cleanup can undo the protection

Restic documents a subtle attack: a compromised client can add garbage snapshots and manipulate timestamps so a later administrator's count-based retention policy removes good snapshots. Its guidance prefers `--keep-within` for append-only cleanup, but even that only protects within the chosen time window. A clean administrator needs independent detection and review, not blind trust in the client's newest snapshot. An offline copy is still useful. [R2]

## Route B: B2 small free hosted storage

### Verified current published terms

- The current main price page says **first 10 GB always free**. Above that it lists **US$6.95/TB per 30 days**, billed on byte-hours, with no minimum file-size/storage-duration fee. This is not a flat subscription. [B1]
- Egress is free up to **3× monthly average stored data**, then **US$0.01/GB**, except qualifying partner routes. Restoring all data soon after a large initial upload can differ from a steady-state month because the allowance uses an average. [B1]
- Current pay-as-you-go pricing says Class A/B/C API operations free; Class D is US$0.004/10,000 after 2,500/day. Do not enable event-notification features unnecessarily. Older Backblaze help/blog pages and third-party tutorials show different historical prices/allowances; use the current main price page, not those figures. [B1]
- Backblaze's main service page advertises **no credit card required** to start. This research did not create an account or verify the current account's exact free upload/download cutoffs. Do not interpret no card at signup as unlimited free operations or an audited guarantee against every liability. [B2]
- Caps/alerts documentation says unlimited charges can accrue if no caps are set. Some caps can be configured, but Class D cannot be capped by that feature and changes may take up to ten minutes. An email alert is not the same as an immediate hard stop. No payment method, no cap increase, no unapproved paid usage. [B3, B4]
- The upload API documents `403 cap_exceeded`, but the documentation read does not establish every no-card account limit or guarantee how the 10 GB allowance is enforced for this user. Verify read-only account limits and refusal behavior in a bounded dummy test before production. [B8]

### The writer must not hold deletion or administration powers

Standard B2 application keys can be bucket/prefix restricted with chosen capabilities. The docs list `writeFiles`, `readFiles`, `listFiles`, `deleteFiles`, bucket/retention/legal-hold management and governance-bypass separately. A backup writer should not receive a master key, key-management permissions, `deleteFiles`, retention administration or governance bypass. A UI preset called read/write must not be assumed safe without inspecting the actual capabilities. [B5, B9]

Write-only restrictions do not solve everything: B2 version/hide behavior can change what appears current without physically erasing an earlier version. Recovery must list and validate **all retained versions**, not only a mutable newest name or index. The hide-file API itself uses `writeFiles`, illustrating why no-delete is not a guarantee of a pristine visible history. [B10]

### Object Lock strengthens a defined window, not all possible threats

Object Lock must actually be configured on the stored versions; a bucket merely having the feature enabled is insufficient. Default retention protects new writes from the time it is configured, not retroactively every old file. Verify the returned retained-until/mode for each snapshot. Current docs distinguish governance mode, bypassable with appropriate capabilities, from compliance mode, whose retained versions cannot ordinarily have retention removed or shortened by users. After expiry, files can be deleted. [B6]

Backblaze's same Object Lock FAQ says an excessively long mistaken lock may require **closing the account**, while its cancellation guide describes deleting data before disabling B2. These are not a clean guarantee about account-closure behavior. Treat this as a material unresolved account-level boundary, not evidence that Object Lock survives full account takeover or provider termination. Resolve the provider's exact current account-deletion/recovery policy before making that promise. [B6, B7]

Long retention and free capacity conflict: retained old/garbage versions occupy storage that cannot simply be deleted early. Defaults should start with a deliberately chosen short test window only after approval, bounded tiny payloads and enough free headroom. Neither enabling a lock nor choosing its duration is authorized by this research request.

## Other candidates

Borg is a real alternative for existing Linux/SSH storage, but its 1.x append-only mode is lower-level: visible delete/prune remains possible and old segment recovery may need manual rollback. The docs warn that later non-append-only writes/compaction can permanently apply deletion markers, and non-Borg filesystem deletion remains possible. This makes rest-server's documented refusal model simpler to explain for the proposed Android experiment. [G1, G2]

R2 offers real server-side bucket lock enforcement and a monthly free allowance, but bucket rules are administratively editable/removable and R2 activation uses a subscription checkout with usage-based monthly charges. It is not the recommendation for a user explicitly seeking gratis with no sudden billing. [C1, C2, C3]

An unplugged extra disk is not append-only software, but a valid independent/offline copy when it is truly disconnected from the compromised device. It costs the disk and manual handling, can fail, and is not a replacement for regular restore checks. This is an architectural option, not a claim about any owned hardware.

## Tampung implications

Keep the Drive experiment and its honest warning. Do not silently replace it. Add a future capability label per backend: application-only append-new, writer-enforced no-delete, timed provider retention, or independent offline copy. A checkbox labelled append-only must not make these sound equivalent.

Whichever backend is later chosen, show:

- Who can delete: phone writer, server admin, storage account owner and provider.
- Snapshot/version count, retained bytes, verified/unverified history, oldest protected snapshot and last restore test.
- Retention expiration, how much capacity is locked until when, and what happens at quota.
- Current free allowance, remaining headroom, observed growth and an estimate clearly separated from a bill.
- No auto-upgrade, paid account activation, silent prune or retention reduction.

An account, key or server decision is **not needed to read this research**. Actual setup would require its own authorization and a bounded synthetic test. No Google, B2, R2, restic, Borg or PDS accounts/resources were created or modified.

## Sources

Primary/vendor/project documentation, read 2026-11-18. This is documentation-level research; no storage service or backup backend was runtime-tested.

- R1. restic/rest-server official repository and README: https://github.com/restic/rest-server
- R2. restic, Removing backup snapshots, security considerations in append-only mode: https://restic.readthedocs.io/en/stable/060_forget.html
- B1. Backblaze current pricing: https://www.backblaze.com/cloud-storage/pricing
- B2. Backblaze service, no-card signup claim: https://www.backblaze.com/cloud-storage
- B3. Backblaze data caps and alerts: https://www.backblaze.com/docs/cloud-storage-data-caps-and-alerts
- B4. Backblaze caps configuration and delay: https://www.backblaze.com/docs/cloud-storage-create-and-manage-caps-and-alerts
- B5. Backblaze application keys: https://www.backblaze.com/docs/cloud-storage-application-keys
- B6. Backblaze Object Lock, including feature cost, modes, limits and closure caveat: https://www.backblaze.com/docs/en/cloud-storage-object-lock
- B7. Backblaze B2 cancellation guide: https://www.backblaze.com/docs/cloud-storage-delete-or-cancel-a-backblaze-b2-account
- B8. Backblaze upload API and cap refusal: https://www.backblaze.com/apidocs/b2-upload-file
- B9. Backblaze create-key capabilities API: https://www.backblaze.com/apidocs/b2-create-key
- B10. Backblaze hide-file API: https://www.backblaze.com/apidocs/b2-hide-file
- G1. Borg serve flags and storage quota: https://borgbackup.readthedocs.io/en/stable/usage/serve.html
- G2. Borg append-only notes, rollback/compaction/admin limits: https://borgbackup.readthedocs.io/en/stable/usage/notes.html
- C1. Cloudflare R2 bucket locks: https://developers.cloudflare.com/r2/buckets/bucket-locks/
- C2. Cloudflare R2 pricing: https://developers.cloudflare.com/r2/pricing/
- C3. Cloudflare R2 get started, checkout/subscription: https://developers.cloudflare.com/r2/get-started/

## Oracle follow-up

For a no-existing-server option, see [Oracle Always Free suitability](oracle-always-free-backup.md). It has enough hardware for a small rest-server experiment, but idle reclamation and account inactivity make it unsuitable as the only dependable recovery copy. This is research, not a deployment.
