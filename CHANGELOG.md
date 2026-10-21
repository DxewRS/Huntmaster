# Huntmaster release notes

## 1.3 — release candidate, not yet published

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

## Published baseline — 1.2

Plugin Hub currently points to `ccd4b442a82315da87ff57556ce8c847b0bf2ec5`, build
`collector-v2-variants-1`. It includes Doom per-level and DT2 normal/awakened
evidence capabilities. Protocol support does not establish natural gameplay
timing or enable Doom Hunt credit.
