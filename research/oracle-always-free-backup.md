# Is an Oracle Always Free server enough?

Research date: 2026-11-18. Documentation-level assessment, not an Oracle deployment, account check or backend replacement. Existing Drive prototype unchanged.

## Verdict

**Yes for a small restic/rest-server experiment in compute/storage terms. Not recommended as the only dependable recovery copy.** The main concern is Oracle's right to reclaim idle free compute and suspend/terminate inactive accounts, not CPU power. A low-traffic phone backup receiver may meet the idle criteria precisely because it is efficient.

A modest Linux VM with a separately controlled rest-server `--append-only` endpoint can protect old repository contents from deletion through the phone's writer credentials. It does not protect them from the VM's root user, OCI administrator, account takeover/closure, provider reclamation or a storage mistake. An independent second copy remains necessary for important data.

No Oracle account, VM, bucket, API key, payment verification, retention lock or subscription has been created or changed. No promise of available capacity or permanent free storage is made.

## Current published allowances, not remembered figures

Oracle's detailed Always Free resource page currently lists:

| Resource | Current documented allowance | Practical meaning |
| --- | --- | --- |
| AMD micro compute | Up to two VM.Standard.E2.1.Micro VMs, each 1 GB RAM and 1/8 OCPU baseline, burstable | A small backup receiver is plausible; concurrent restore/check/prune throughput must be measured |
| Arm A1 compute | First 1,500 OCPU-hours and 9,000 GB-hours/month, described as 2 OCPUs and 12 GB RAM total for Always Free | More than enough headroom for the small server process, if available. Do not repeat older 4-OCPU/24-GB claims as current fact |
| Boot + block volumes | 200 GB combined in the home region, plus five combined boot/block volume backups | OS and repository share the allowance. One default ~50 GB boot + 150 GB data volume leaves less than 150 GB usable repository space after filesystem/headroom |
| Outbound data | 10 TB/month allowance | Usually ample for a small personal restore experiment, but not an unlimited promise |
| Object storage after trial, Always-Free-only account | 20 GB combined Standard/Infrequent Access/Archive and 50,000 API requests/month | Different service from the VM disk. Potentially useful for a future object-storage design, not automatically an independent provider/account copy |

Source: [O1]. The same Oracle page inconsistently mentions 47 GB and 50 GB boot-volume minima/defaults in different sections. Plan conservatively around 50 GB and verify the actual console allocation before any setup. Do not claim an exact 153 GB or 150 GB usable backup capacity. The 200 GB is per tenancy and shared with any existing resources, not a fresh allowance for every VM.

The page also distinguishes object-storage allowances for paid/free-trial accounts (10 GB per listed tier) from the post-trial Always-Free-only 20 GB combined allowance. Do not add these together or assume trial capacity survives the promotion.

## Why this should not be the only recovery copy

### Idle compute can be reclaimed

Oracle says an Always Free instance can be deemed idle when, over seven days, all applicable conditions hold:

- 95th-percentile CPU utilization below 20%.
- Network utilization below 20%.
- Memory utilization below 20% for A1 only.

A backup endpoint receiving a few small snapshots is likely to be low utilization. This is a risk assessment, not a claim about a particular VM. Do not manufacture CPU/network load to evade the policy. A monitoring check or periodic login is not proof it avoids compute-idle reclamation. [O1]

Oracle's general *manual instance termination* documentation says attached VNICs/volumes are detached and the boot volume is preserved by default, with explicit options that can also delete volumes. That does **not** establish what all provider-initiated idle reclamation or account termination does to all data. No guaranteed volume-survival/retention interval was established for those cases. A separate persistent data volume helps operational separation, but must not be sold as immunity to provider/account deletion. [O5, O6]

### Account inactivity rules conflict

The current public Free Tier FAQ says accounts idle for 30 days or more may be treated as abandoned and eligible for suspension or termination. A different official promotion-expiry page says free accounts remain active when used within the past 60 days. These official texts conflict. Treat 30 days as a conservative risk boundary, not a guaranteed safe interval, and resolve current account policy before entrusting important data. Account activity and instance utilization are different checks. [O2, O4]

### Availability is not guaranteed

Always Free compute must be in the tenancy's home region. Oracle documents out-of-host-capacity errors and suggests another availability domain or waiting. No capacity was checked for an account/region here. Do not silently upgrade to paid merely to obtain capacity; do not automate repeated signup or create multiple free accounts. Oracle permits one free account per person. [O1, O2]

## Free tier versus the trial and billing

The **US$300/30-day trial** is not the ongoing free allowance. Paid resources created with trial credits can be reclaimed after the promotion/grace period unless upgraded. Oracle warns excess A1 resources are disabled and deleted if the tenancy does not return within the Always Free allowance. Use only explicitly Always Free-eligible resources and a free Linux image in the home region, not a trial-funded bigger machine. [O1, O3, O4]

Oracle's free-tier signup uses card/contact information for identity verification and can place temporary authorization holds. The FAQ excludes virtual/single-use/prepaid cards and PIN-only debit cards. These holds are not ordinary usage charges, but they are still relevant to setup and require explicit approval. Do not ask for a card in chat or treat signup as already authorized. Oracle says the account is not charged unless upgraded, but signup acceptance, actual free limits and billing state must be checked in the real flow before proceeding. [O2, O7]

No upgrade to Pay As You Go is recommended by this research. After an upgrade, over-limit usage can be billed; a free-eligible VM does not make every extra disk, image, region, backup, load balancer or other resource free. Budget alerts are not assumed to be enforceable spending limits. Use the actual tenancy's Limits/Quotas/Usage and no-paid-resource constraints if a later setup is approved. [O1, O2]

## Rest-server fit and safe design

The official rest-server project provides Linux amd64 and arm64 release builds, matching the AMD and Arm families. Architecture compatibility is established at documentation/release-asset level only, not tested on OCI. The server is designed to run on low-end hardware, but an actual workload/memory/restore test is still needed. [R1, R2]

If evaluated later:

1. One Always Free Linux VM, one explicit persistent repository directory on the data volume, and a repository quota below real free capacity. **Never use rest-server's default temporary directory**, which the README warns loses backups.
2. Run the service unprivileged, authenticate it and use HTTPS. Phone gets only the backup endpoint credential; no OCI keys, root SSH credential or cleanup permission. Keep software patched and firewall only needed ports.
3. Use restic's established encrypted repository/client. Tampung's current custom dummy codec is not a restic repository and cannot simply be POSTed to rest-server. Native Android integration and recovery-key handling require a separate design/test.
4. Keep restore/recovery keys independently. A server can preserve bytes that become useless when all decryption material is lost.
5. No automatic keep-last-N pruning. A malicious writer can poison count/timestamp-based policies; separate trusted maintenance needs inspection and conservative retention. See [the existing append-only comparison](gratis-append-only-backups.md).
6. Test synthetic upload, denied overwrite/delete via writer, restore of both old/new snapshots, quota exhaustion and recovery after stopping/rebuilding the VM without destroying the data volume. A manual successful rebuild does not prove survival of Oracle account termination.
7. Keep a second recovery copy outside this Oracle account/provider for important data. Drive can remain an additional copy with its known deletion limits; it is not automatically synchronized/pruned to match the Oracle history.

## A separate Oracle option: Object Storage retention

Oracle Object Storage also documents time-bound retention rules that block object modification/deletion. A **locked** rule is irreversible after a mandatory 14-day lock delay; Oracle says not even tenancy admin or support can delete it, and duration can only increase. Indefinite legal-hold rules cannot be locked. Versioning and active retention have constraints. [O8]

This might avoid depending on an idle VM to receive/recover small backups, within the separate object-storage allowance. It is a future candidate, not an established free-tier runtime qualification or a recommendation to lock anything now. It needs a compatible Android adapter, actual IAM/scope and quota checks, and a careful synthetic test before any irreversible lock. Account suspension/closure/provider retention remain separate threats. A locked object is not proof that an account will live forever, and locked junk can consume the limited free quota.

## Recommendation for tampung

Oracle is good enough to **try** a server-enforced append-only backend without buying hardware, if signup and Always Free capacity work. Choose it as an experiment/secondary recovery store, not an unattended sole backup guarantee. Do not remove Drive or turn on OCI resources based solely on this research. The next useful question, only if setup is desired, is whether there is an existing Oracle account and what its actual free-region/capacity limits are, not a request for passwords or a paid upgrade.

## Sources

All official sources below were read 2026-11-18. No Oracle account or service was operated.

- O1. Oracle, Always Free Resources, exact shapes/storage/idle/network policy: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
- O2. Oracle public Free Tier FAQ, signup/payment/inactivity/account eligibility: https://www.oracle.com/cloud/free/
- O3. Oracle Cloud Infrastructure Free Tier, trial and excess A1 behavior: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm
- O4. Oracle promotion expiry, 60-day account text and trial reclamation: https://docs.oracle.com/en-us/iaas/Content/GSG/Tasks/signingup_topic-What_Happens_When_the_Promotion_Expires.htm
- O5. Oracle manual instance termination: https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/terminatinginstance.htm
- O6. Oracle boot-volume lifecycle: https://docs.oracle.com/en-us/iaas/Content/Block/Concepts/bootvolumes.htm
- O7. Oracle signup tutorial, no charges unless upgrading: https://docs.oracle.com/en/learn/cloud_free_tier/
- O8. Oracle Object Storage retention rules and irreversible locking: https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/usingretentionrules.htm
- R1. rest-server project, flags/persistence/security: https://github.com/restic/rest-server
- R2. rest-server official release assets: https://github.com/restic/rest-server/releases
