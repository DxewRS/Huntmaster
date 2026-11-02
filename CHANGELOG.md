# Huntmaster release notes

## Unreleased

- A kill report the bot keeps refusing no longer stalls tracking. After an
  immediate rejection or three failed attempts it is held: saved, retried hourly,
  excluded from outage recovery and backpressure, and announced once in chat.
  Outages and rate limits never hold a report.
- A held report expires only after 30 days and 24 failed attempts. At most 2,000
  reports or 8 MiB are held, oldest removed first. Removed reports are copied to
  a backup setting, three copies kept, and the player is told once.
- One report that fails alone with temporary errors stops counting as an outage
  after three tries, stays saved and retries every few minutes, so kills behind
  it still deliver. More than three failing together remain an outage.
- HTTP 500, Cloudflare 52x and 530 now count as temporary, not as a refusal.
- Reports the bot rejects are no longer deleted, except when it answers
  `report_expired`, which is final. Diagnostic-only reports keep their rules.
- Startup keeps held state and kill reports dated ahead of the PC clock. A saved
  queue that cannot be fully read is copied to a backup setting before rewriting.
  Retry timing survives a restart, and a save before the restore has run can no
  longer replace the saved queue with an empty one.
- Health checks back off with jitter during an outage and honour `Retry-After`
  up to one minute. Refused registration checks back off up to five minutes and
  a saved kill the bot keeps failing backs off and stops blocking recovery.
- Clearer messages when registration cannot be confirmed and when an outage means
  no kills are being recorded for this login.
- PRIVACY.md now states RuneLite profile sync, backups, the lack of an age limit on
  undelivered kills, and that report evidence comes from the client.
- README reorganized by topic; version history lives in these notes.

## 1.3 — published October 21, 2026 at `5798fe2361bbe639f36912ce7768e91915caa8fe`

- Current Hunt sidebar with bundled RuneScape boss renders, confirmed-KC progress,
  remaining kills, reward and completion state. No runtime Wiki image requests.
- Your Rank card with bundled portraits, bot-owned earned points/thresholds,
  next-rank progress and a labelled account-matched cache for temporary outages.
- Settings reorganized into Display, Discord LFG / Queue alerts, activity
  categories and About Huntmaster. All four raids include The Fractured Archive.
- Recruitment master switch preserves activity selections. Checkbox edits no
  longer repeatedly ask for confirmation or reset a nonempty live alert feed.
- Overlay visibility choice: after credited kill (existing default) or while a
  Hunt is active. Both display only server-confirmed progress.
- Brief outages stay silent; warn once at five minutes, preserve the existing
  ten-minute collection pause, and announce recovery only after that deadline.
- Unknown future optional capture-window entries leave valid packaged-boss
  settings/capabilities intact; existing bounds and protocol gates remain.
- Unchanged sidebar values skip redundant repaint work. Artwork caches remain
  bounded and include failed lookups. The accepted sidebar layout is unchanged.
- About Huntmaster displays version 1.3 and build `collector-v2-release-1`.
- Saved reports cache account/routing/capability hints in memory; recruitment
  selections refresh on settings/profile changes instead of every poll.
- Tracking Status and Copy diagnostics controls removed; internal evidence,
  duplicate protection, saved report IDs and bot credit decisions are preserved.
- Retired the unused local status/clipboard helper and duplicate submission marker;
  release submissions use the authoritative Plugin Hub descriptor.

No new CI, desktop notifications, timed mute, recent-Hunt history or Groups
Recruiting view is included. Doom assignment/credit availability is unchanged.

## 1.2

Published at `ccd4b442a82315da87ff57556ce8c847b0bf2ec5`, build
`collector-v2-variants-1`. It includes Doom per-level and DT2 normal/awakened
evidence capabilities. Protocol support does not establish natural gameplay
timing or enable Doom Hunt credit.

## 1.1

Added in-game group recruitment alerts from the Discord bot's LFG / Queue system.
