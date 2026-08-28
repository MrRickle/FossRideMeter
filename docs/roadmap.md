# FossRideMeter Roadmap

Status as of the current `main`. Version `0.1.4` — pre-release, no
outside users yet.

## Done

### Phase 0 — Foundation

* Android project, Kotlin, Compose, Material 3.
* Project structure and documentation.
* Version display with git build identification and UTC build time.
* GPL-3.0 headers on every source file, plus a License screen.

### Phase 1 — Ride interface

* READY / RUNNING / PAUSED status.
* Start, Pause, Resume, Save, Cancel controls, with confirmation on the
  two that end a ride.
* Live distance, elapsed time, and calculated amount.
* Portrait and landscape layouts.

### Phase 2 — Ride timing

* `TimeProvider` / `SystemTimeProvider` behind an interface.
* Timer starts, pauses, resumes, and stops with the ride.

### Phase 3 — GPS tracking

* Runtime location permission handling.
* `GpsDistanceProvider` on fused location, with accuracy bucketed into
  GOOD / POOR / WAITING and a minimum-speed gate.
* `SimulatedDistanceProvider` so the UI can be exercised with no GPS.
  Selectable in Settings; a fresh install meters from real GPS.

### Phase 4 — Rate settings

* Per-distance rate, hourly rate, base amount, minimum amount.
* US / SI measurement system, with rates entered in the displayed unit and
  stored per meter.
* Stop-detection duration.
* Persisted in DataStore Preferences.

### Phase 5 — Rides

* Room database with rides, places, and stops.
* Ride history table with full per-ride detail.
* Ride editing (name, manual amount) and deletion.
* Place deletion from the place editor as well as from multi-select, so
  both tables delete the same two ways. Not offered where the editor is
  opened to *name* a spot — from a stop, or from the live ride.
* Multi-select on the Rides and Places tables (long-press, contextual bar,
  bulk delete and export-selected), with places re-averaged after rides
  are deleted.
* Place editing reachable from a ride's start/end place and from its
  stops list.
* Viewing a ride separated from editing it — `RideViewDialog` shows the
  record and tracks the ride by id, so it never shows a stale row. Editing
  then folded back into the rows themselves: a ride has no edit mode, and
  its name and amount are changed by tapping them.
* One `RideInfoSection` shared by the live screen and a saved ride, both
  mapping into `RideInfo` — same rows, same links, same editing. Shown
  once a ride starts.
  Before that the space holds Ride Name and Override Amount, which are
  carried onto the ride when it begins.
* Row written at ride start and re-persisted every 5 s, so a crash
  mid-ride loses at most five seconds.

### Phase 5b — Places and stops (beyond the original plan)

* Automatic place creation and radius-based matching.
* Averaged place locations, with a lock for hand-entered coordinates.
* Boundary enforcement after a place is edited.
* Places kept square with the points around them in every direction:
  deleting a place re-resolves the rides and stops that used it, and
  saving a place re-resolves the points now inside it, so a place drawn
  later applies to history — widening "Home Depot" relabels the stops
  already recorded in it, and a tighter "Home Depot Windows" drawn inside
  takes back the ones it covers.
* Consecutive stops at one place shown as a single visit, display-only, so
  the rows behind it stay available to a place drawn later.
* A place named from a stop: the stops list shows each dwell's coordinates
  and opens the place editor on a new place sitting on that point.
* Automatic stop detection with a fixed dwell anchor.
* ADD STOP beside the live screen's control button, recording a stop by
  hand from the dwell already under way.

### Phase 5c — Background operation

* Foreground service that owns tracking, so it survives backgrounding.
* Ongoing notification and a draggable floating bubble overlay.
* Notification, overlay, and battery-optimization permission prompts.

### Phase 5e — Automatic rides by location

* `Place.autoStart` / `Place.autoSave` acted on: leaving a flagged place
  starts a ride, arriving at one pauses it and saves after a grace window.
* Leaving an `autoStart` place while a ride is paused resumes that ride
  rather than starting a second one.
* `PlaceWatcher` on the platform `LocationManager` — no Play Services —
  with coarse fixes escalating to GPS only near a boundary, and crossings
  confirmed over time and count before they're believed.
* Watching survives reboot (`BootReceiver`) and needs
  `ACCESS_BACKGROUND_LOCATION`, requested when a place is first flagged.
* No master switch: flagging a place is the switch.

### Phase 5d — Import / export

* Rides export/import as JSON via SAF, stops included in the bundle.
* Places export as JSON; import auto-detects JSON, CSV, or GPX.
* Raw `.db` file backup and restore, on the Advanced screen.

### Surviving an upgrade

* Room exports its schema to `app/schemas` (committed) and **migrates**
  from version 6 on. A version at or above `MIGRATION_BASELINE` with no
  `Migration` written for it now throws on open instead of dropping the
  tables — the mistake fails on a developer's phone, not a user's.
* Two upgrades still can't be migrated and are rebuilt from scratch: a
  database older than the baseline, from before schemas were exported,
  and a downgrade to a build that has never heard of the schema on disk.
* `SchemaRescue` is what those two cost. It copies the database aside
  before Room opens it, then copies every row back into the rebuilt
  tables for the columns both schemas share. The carry-forward runs only
  when Room really did rebuild; after a migration the rows are already
  across, and copying them again would duplicate every one.
* The set-aside files live in `filesDir/pre-upgrade`, newest five kept,
  and can be shared off the device from the Advanced screen. They are the old
  schema, so they are for reading, not for restoring into a newer build.
  The copy is still taken on every version change, migrated or not — a
  migration can be wrong, and then it is all that is left.
* The debug menu became the **Advanced** screen: same drawer entry, same
  items, a name that says who it is for. Restore and Repair place links
  each ask before acting, and a restore is applied at the next process
  start rather than swapped in under a running app.

### Ready to publish

* Version `0.1.4`, signed release build verified against the release key.
* `CHANGELOG.md`, and an Installing section in the README covering
  sideloading, the signing-key rule, and what a fresh install meters.
* A fresh install meters real GPS; the simulator is a Settings away.
* `PRIVACY.md`, written from the manifest and the storage the app
  actually uses. The headline is checkable: no `INTERNET` permission in
  the merged manifest, so the process cannot open a socket.
* **The stopped hourly rate applies.** Time at a ride's stops bills at
  `Settings.stoppedHourlyRate`, the rest at `hourlyRate`; both the
  seconds and the rate are stored on the ride so it explains its own
  amount. It also has an editor in Settings, which it never had.
* **The first real migration**, 6 → 7, adding those two columns —
  written, exported to `schemas/7.json`, and covered by the tests that
  were built for it.
* R8 on for release: shrink, resource shrink, and obfuscate. 14.1 MB →
  1.9 MB. `app/proguard-rules.pro` is short because Room, Compose,
  kotlinx.serialization and play-services-location all ship consumer
  rules; what it adds is the enum constant names this app persists, and
  line numbers for readable crash reports.
* Android's backup service is off (`allowBackup="false"`), with both
  rules files excluding everything for cloud backup and device-to-device
  transfer, so ride history isn't copied to a Google account by a route
  the app doesn't control. Export is the replacement, and the README and
  privacy policy both say a new phone won't restore rides by itself.

### Help in the app

* A **Help** drawer entry above About, rendering `docs/quickstart.md`.
  The build copies that one file into `res/raw/help.md`, so the
  repository copy, the README link, and the screen are the same text.
* It answers the question a sideloaded install actually raises: a
  permission toggle that will not turn on, because Android hides **Allow
  restricted settings** behind a three-dot menu.

### Donations

* PayPal, Venmo, Bitcoin, and Lightning on the About screen, each one a
  tap to the handling app, a copy button, and a QR code. Configured in
  `util/Donations.kt`; a blank one is dropped, and with all of them
  blank the section renders nothing. PayPal, Bitcoin, and Lightning are
  filled in; Venmo is not.
* QR codes encoded with ZXing core and drawn on a Canvas
  (`ui/QrCode.kt`) — the one third-party dependency the feature added,
  pure Java with no Android or Play services in it.

### Surviving the process being killed

* Service creation and the previous process's exit reason
  (`ApplicationExitInfo`) go in the event log.
* A ride interrupted by a process kill is picked up at the next service
  create and comes back paused.

## Next

### Before publishing

Ordered by what hurts a first-time user most.

* **Published** to `github.com/MrRickle/FossRideMeter`, public, as a
  single squashed commit — the 255 local commits stayed local by
  choice. Push `main` only: `--all`, `--tags`, and `--mirror` would send
  the history that was deliberately left behind.
* **Cutting a release:** bump `versionCode` and `versionName`, move the
  changelog's Unreleased section under the new heading, tag `vX.Y.Z`, build
  `assembleRelease`, attach the APK under a name carrying its version,
  and keep that build's `mapping.txt` — without it a stack trace from a
  shrunk build can't be read.

### Distribution channels, and what each one still needs

The channel decides how much of the above is optional — see the
free-application decision in `decisions.md`.

* **Direct APK / GitHub release.** Reachable now. Signing works, and
  `keystore.properties` and the `.jks` are correctly gitignored and
  untracked.
* **F-Droid.** Blocked on something structural: `play-services-location`
  is proprietary, and F-Droid's inclusion policy does not take non-free
  dependencies. It would need a build flavor whose `DistanceProvider`
  runs on the platform `LocationManager` instead — which `PlaceWatcher`
  already demonstrates is workable, and which `DistanceProvider` is
  exactly the seam for. Also needs fastlane metadata, which doesn't
  exist.
* **Google Play.** The most work by far.
  `ACCESS_BACKGROUND_LOCATION` triggers the declaration form and a video
  review; `SYSTEM_ALERT_WINDOW` and `SCHEDULE_EXACT_ALARM` each need
  their own justification; a privacy policy URL is mandatory for a
  location app; and the donation links run into the payments policy
  already flagged in `decisions.md`.

### Tests

There are four classes: `AmountCalculatorTest` covers the fare
arithmetic, including the moving/stopped split and its clamp, and the
rest are about the schema.

* `MigrationConfigTest` (JVM) — every version from the baseline up has a
  migration covering it, no migration starts below the baseline, and
  `rebuiltFromScratch()` answers correctly on both sides of both
  boundaries. Deliberately a JVM test: bumping `SCHEMA_VERSION` without
  writing the migration fails on `./gradlew test`, with no emulator
  standing between the mistake and hearing about it.
* `MigrationTest` (instrumented) — creates the baseline schema, runs the
  migrations, and lets Room validate the result against the exported
  JSON. Needs real SQLite, so it needs a device.
* `SchemaRescueTest` (instrumented) — what `carryForward` carries, what
  it loses when a `NOT NULL` column was added with no default (the whole
  table), and that it refuses a destination that already has rows.

Everything else is still uncovered, and the rest of this section is the
list. New JVM tests belong under `app/src/test/java/org/fossridemeter/app/`
(the older `org/fossRideMeter/app/` directory, with the non-matching
package casing, is empty).

The pure, provider-free pieces are the obvious starting point:
`AmountCalculator`, `UnitConversions`, `GeohashUtil`, `DistanceUtil`,
`PlaceResolver`, and `RideMeter`'s stop detection driven by
`SimulatedDistanceProvider`.

`PlaceWatcher`'s crossing state machine belongs on that list and is not yet
covered: arming without firing, confirmation by both time and count, and
the coarse-fix escalation rule are all decidable without a device.

### Decide whether the tiered location check earns its keep

`Settings.autoWatchAccuracy` defaults to a GPS fix per check; `TIERED`
keeps the cheapest-first path with its escalation machinery. That path is
the source of most of the complexity in `PlaceWatcher` and most of the
latency in auto-start. Measure what it actually saves —
`adb shell dumpsys batterystats --charged org.fossridemeter.app` over a
parked night — and delete it if the saving doesn't justify it.

### Bulk view of selected rides

Now that viewing a ride is its own dialog, multi-select could offer a
non-destructive action beside delete and export: a combined view of the
selected rides. Bulk *edit* remains meaningless — name and manual amount
are inherently per-ride — but reading several at once is not.

### Release preparation

* UI polish pass.
* Help screen.
* Decide the distribution channel (see the free-application decision in
  `decisions.md`).

## Future ideas

Possible, none committed:

* Maps and route display.
* Multiple rate profiles.
* User notes per ride.
* Totals and summaries over a date range.
* Cloud backup.

Features should be added only when they improve the core ride-metering
experience.
