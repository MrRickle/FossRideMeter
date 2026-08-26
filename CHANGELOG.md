# Changelog

Notable changes per release. Dates are the release date, not the build
date. Versions follow `MAJOR.MINOR.PATCH`; while the leading digit is 0
the shape of things may still change between minor versions.

## Unreleased

### Fixed

- **Pausing and resuming a ride on the Simulator sent the distance back
  to zero** and replayed the simulated route from the beginning,
  including its opening minute of waiting. The simulator cleared itself
  when it was started, and resuming a ride starts the distance provider
  again. Real GPS rides were never affected.

## 0.1.2 — 2026-08-26

### Changed

- **New defaults for a fresh install.** None of these touch a ride
  already saved, or an install that has set its own value.
  - **Minimum speed: 3 mph** (was 0). Below that speed a GPS fix adds no
    distance, so a parked phone's drift is no longer metered as travel.
    The threshold sits at walking pace so a crawl through a car park or a
    jam still bills its miles.
  - **Stop detection: 3 minutes** (was 5), so a real wait at a door is
    charged at the stopped rate. Still far longer than any light or queue.
  - **Automatic watch: every 30 seconds** (was 60), so a departure is
    noticed sooner. This is the idle poll only — a suspected crossing
    already polls faster to confirm.
  - **Automatic save grace: 2 minutes** (was 5), so a ride that has
    genuinely ended doesn't sit uncommitted. Resuming still takes it back.

### Added

- A **Help** screen in the drawer: how to install, what each permission
  is for, a first ride, and what to do when a permission toggle will not
  turn on because Android has restricted settings for an app installed
  from a file. It is the same text as
  [`docs/quickstart.md`](docs/quickstart.md), copied into the app when it
  builds rather than kept as a second copy.

## 0.1.1 — 2026-08-25

### Fixed

- **The default per-distance rate was $1,287.48 per mile.** Rates are
  stored per meter and shown per mile, and the default read `0.80` — which
  looks like eighty cents a mile and is not. A fresh install now starts at
  **$1.75 per mile**. An install that has already set its own rate is
  unaffected, and so is every ride already recorded, since a ride stores
  the rates it was metered at.
- Settings fall back to the values on `Settings()` itself rather than to
  literals repeated in `SettingsRepository`, so the two can no longer
  disagree about what a default is.

## 0.1.0 — 2026-08-25

First release. Everything below is new, so this entry describes what the
app is rather than what changed.

### Metering

- Meters a ride's distance and elapsed time by GPS and computes a
  fare-style amount from a per-distance rate, an hourly rate, a base
  amount, and a minimum.
- Tracking runs in a foreground service, so it survives the app being
  backgrounded, and offers a draggable floating amount bubble while it is.
- A ride is written to the database from the moment it starts and
  re-written every five seconds, so a crash mid-ride costs at most five
  seconds. A ride interrupted by the process being killed comes back
  **paused**, because nothing recorded what happened during the gap.
- Stops along a ride are detected by dwell time, or added by hand with
  ADD STOP.
- Time spent at those stops bills at its own hourly rate, so waiting at a
  door can be charged differently from driving. Waiting at a light is not
  a stop, and time while the ride is paused is not ride time at all.

### Places

- A ride's start, end, and stops resolve to places. Unnamed ones are
  created automatically with a geohash for a name; naming one is what
  makes it yours.
- Editing a place re-resolves the history around it: widening one claims
  the stops already recorded inside it, and drawing a tighter one inside
  it takes back what it covers.
- A place can start a ride automatically when you leave it, and pause and
  save one when you arrive. Automatic starts are backdated to the
  departure, so the ride doesn't begin down the road from where you left.

### Data

- **Upgrades migrate the database** from schema 6 onward. Every upgrade
  also sets a complete copy of the database aside first, in case a
  migration is wrong; the last five are reachable from the Advanced
  screen and can be shared off the phone.
- Rides and places export and import as JSON through the system file
  picker. Places also import from CSV and GPX.
- The Advanced screen holds a raw database backup and restore, the
  automatic-by-location event log, and the databases set aside by
  upgrades.

### Privacy

- The app has **no `INTERNET` permission**, so it cannot send anything
  anywhere — Android enforces that regardless of what the code intends.
- Android's own backup service is switched off, so ride history is not
  copied into a cloud backup. **A new phone will not restore your rides
  by itself** — export them first.
- See [PRIVACY.md](PRIVACY.md) for what is stored where, and why each
  permission exists.

### Upgrading from a build before this one

Any install older than schema version 6 predates real migrations. Its
database is set aside whole before the tables are rebuilt, and the rows
are carried across column by column. If something is missing afterwards,
the complete copy is still on the phone: **Advanced → Set aside by an
upgrade**, where it can be shared out. A column that was added as
`NOT NULL` with no default cannot be carried, and takes its whole table
with it — which is exactly the case that file exists for.
