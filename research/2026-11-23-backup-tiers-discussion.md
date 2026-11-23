# Backup tiers for app signing keys - discussion record

Date: 2026-11-23. A faithful record of a conversation between Irvan and
Instinct about where the app family's signing-key backups should live, in the
order it happened. This note records questions, verified answers and
decisions; it contains no secrets, ciphertext or personal data.

## What prompted it

Patuh's registry flagged a repo Irvan did not recognize:
`signing-key-backups`, a private repo created 2026-11-18. He asked what it
was and who uses it.

## Questions and verified answers, in order

**Who or what process uses the backup repo?**
Verified against the API: nothing reads it. Code search across all
repositories returns zero references, and no workflow touches it. The keys CI
actually signs with live in each app repo's own GitHub Actions secrets
(keystore as base64, alias and passwords). The repo's role is a cold,
manual disaster-recovery copy: GPG-encrypted (AES256) ciphertext, restore by
hand, recovery passwords in his password vault. It was written exactly twice
on 2026-11-18 (the irvan and tuju key backups) and never since.

**Why did only irvan and tuju land in the repo?**
Timing, not a decision. Every key created on 2026-11-17 (kemas, sapa, jin,
onda, unggah, bidik, denyut, saku, tumbuh) had its encrypted backup delivered as
a WhatsApp attachment, each restore-checked before sending. The private repo
was created on 2026-11-18 and received only that day's two new keys. The
backup strategy changed mid-stream; nothing about irvan or tuju is special.

**Why don't saku and tampung (themselves secret/backup tools) use a private
GitHub repo as their storage backend?**
The 2026-11-18 architecture split was deliberate: backups to the user's
Google Drive, sync/chat over DeltaChat, public data on Bluesky - GitHub was
not in it. A private repo is not append-only: anyone holding a token can
force-push or delete history, so it fails the ransomware-resistance bar set
for tampung. Saku is local-first by design; putting its vault in a repo would
mean a leaked token hands over the whole ciphertext database. A private repo
is a convenience store for cold ciphertext, not a backend.

**Can't GitHub be configured to deny force pushes?**
Yes - branch protection / rulesets can block force pushes and deletions. But
on a free personal account that is available only for PUBLIC repositories;
for private repos the API requires a paid plan. And even where it works, it
protects branch history, not the repository itself: an account-level
compromise can still delete the repo. [S1]

**Does B2 object lock survive account compromise?**
Two modes. Governance mode: overridable by anyone with a capable application
key. Compliance mode: nobody can delete or shorten a lock during the
retention window - not the owner, not an admin, not Backblaze support. The
escape hatch that survives is closing the entire account, which needs the
account login plus 2FA. Object lock shrinks the attack surface from "any
leaked token" to "login AND 2FA"; it does not eliminate it. Cost side:
during retention even the owner cannot delete, so over-locking a bucket
means paying storage until expiry. [S2][S3]

**Does restic append-only fall to ransomware too?**
It survives the common case: a compromised backup client. rest-server in
`--append-only` mode refuses deletes and overwrites at the protocol level, so
ransomware holding the client credentials cannot touch history. It does not
survive compromise of the server host itself (the flag is enforced by the
rest-server process, not the storage), and `forget`/`prune` need full access,
so the admin client must be a separate, well-secured machine. A subtler
attack: garbage snapshots timed to trick count-based retention into pruning
good ones - mitigated with `forget --keep-within`. [S4][S5]

**So is proper safety only air gap or CD burning?**
Correct in principle: every network-attached copy shares a failure mode with
the thing it protects. The tier list ends at an offline copy, physically
elsewhere, needing a human to touch it.

## Conclusion: tiers, not one winner

1. GitHub Actions secrets - what CI signs with, per app repo.
2. `cadang` (private repo) - cold disaster recovery, GPG ciphertext,
   restore-by-hand, recovery passwords in the vault.
3. Optional provider-enforced immutability - B2 compliance-mode object lock,
   or restic + rest-server `--append-only` for client-ransomware resistance,
   each with the documented limits above.
4. Final tier: air-gapped paper backups - each small ciphertext encoded as
   QR codes, printed, stored physically. Restore: scan with any camera,
   reassemble, decrypt with the vault recovery password. No GitHub, no
   accounts, no internet.

**The QR-sheet idea is Irvan's preferred final tier of the options
discussed. It is PARKED: revisit early 2027, at his decision.**

## Decisions taken 2026-11-23

- The repo `signing-key-backups` was renamed to `cadang` and stays private;
  its README was rewritten and the original details archived in
  `old-details.md` inside that repo.
- All app signing-key backups are consolidated into `cadang` (regenerated
  fresh from each repo's Actions-secret keystore, restore-checked).
- Patuh enforces backup coverage: any enrolled signed app must have its
  keystore backup in `cadang`, and repo discovery flags missing backups so a
  newly keyed app cannot be forgotten.

## Sources

- [S1] GitHub REST API branch protection:
  https://docs.github.com/en/rest/branches/branch-protection
- [S2] Backblaze B2 Object Lock:
  https://www.backblaze.com/docs/en/cloud-storage-object-lock
- [S3] Backblaze: what IT teams get wrong about immutable backups and object
  lock: https://www.backblaze.com/blog/what-it-teams-get-wrong-about-immutable-backups-and-object-lock/
- [S4] restic rest-server README:
  https://github.com/restic/rest-server?tab=readme-ov-file
- [S5] restic forget documentation:
  https://restic.readthedocs.io/en/latest/060_forget.html

Related: [gratis append-only backup options](gratis-append-only-backups.md).
