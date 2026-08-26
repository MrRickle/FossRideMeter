# FossRideMeter Architecture

## Overview

FossRideMeter is an Android application that meters rides. It tracks
distance and elapsed time, computes a fare-style amount from configurable
rates, and stores completed rides along with the places they start and end
at and the stops made along the way.

Kotlin + Jetpack Compose, a single `:app` Gradle module, no dependency
injection framework, no networking. Everything is on-device.

The design goal is to stay simple, maintainable, and easy to extend.
External data sources (GPS, the clock) are abstracted behind providers so
simulated and real inputs can be swapped without touching ride logic.

## Package Structure

```
org.fossridemeter.app
│
├── MainActivity.kt              theme + AppNavigation, nothing else
│
├── model                        plain data types
│   ├── Ride.kt                  in-flight ride + toRecord() mapping
│   ├── RideRecord.kt            Room entity (rides)
│   ├── Place.kt                 Room entity (places)
│   ├── Stop.kt                  Room entity (stops)
│   ├── RideLocation.kt          lat/lon/accuracy point
│   ├── RideStatus.kt            READY / RUNNING / PAUSED
│   ├── Settings.kt              user settings (DataStore-backed)
│   ├── Measurement.kt           MeasurementSystem: US / SI
│   ├── DistanceUnit.kt          FEET / MILES / METERS / KILOMETERS
│   ├── TimeUnit.kt              SECONDS / MINUTES / HOURS
│   └── DistanceProviderType.kt  SIMULATOR / GPS
│
├── service                      ride logic, no Compose
│   ├── RideTrackingService.kt   foreground service, bubble, notification
│   ├── RideMeter.kt             the ride state machine
│   ├── PlaceWatcher.kt          place-crossing detection (LocationManager)
│   ├── AutoState.kt             Off / Watching… / Suspended / PendingSave
│   ├── BootReceiver.kt          restarts watching after a reboot
│   ├── NotificationDismissedReceiver.kt  logs the ongoing notification
│   │                            being swiped away
│   ├── DistanceProvider.kt      interface: distance + gpsInfo flows
│   ├── GpsDistanceProvider.kt   fused location implementation
│   ├── SimulatedDistanceProvider.kt  scripted implementation
│   ├── TimeProvider.kt          interface: elapsedSeconds flow
│   ├── SystemTimeProvider.kt    wall-clock implementation
│   ├── DistanceStatus.kt        OFF / WAITING / GOOD / POOR
│   ├── GpsInfo.kt               current fix + status for display
│   └── AmountCalculator.kt      pure fare arithmetic
│
├── data                         persistence
│   ├── AppDatabase.kt           Room database (rides.db)
│   ├── AppRepository.kt         hand-rolled singleton container
│   ├── RideDao.kt / PlaceDao.kt / StopDao.kt
│   ├── RideRepository.kt        interface used during tracking
│   ├── RoomRideRepository.kt    its Room implementation
│   ├── PlaceResolver.kt         point → Place (create if none matches)
│   ├── PlaceLocationRecalculator.kt   averages a place's coordinates
│   ├── PlaceBoundaryEnforcer.kt re-resolves points after a place edit,
│   │                            deletion, or absorption
│   ├── Migrations.kt            MIGRATION_BASELINE, the Migration list,
│   │                            and what counts as a rebuild
│   ├── PendingRestore.kt        applies a staged restore at process start
│   ├── SchemaRescue.kt          sets the db aside before every version
│   │                            change, carries the rows back after the
│   │                            two Room can only rebuild
│   ├── SettingsRepository.kt    DataStore Preferences
│   ├── RidesBackup.kt           ride JSON export/import
│   ├── PlacesBackup.kt          place JSON/CSV/GPX import, JSON export
│   └── Converters.kt            Room TypeConverters for RideLocation
│
├── ui                           Compose screens + ViewModels
│   ├── navigation/AppNavigation.kt   NavHost, drawer, permissions
│   ├── Screen.kt                route definitions
│   ├── RideScreen.kt + RideInfoSection / RideLiveInfoSection /
│   │   RideWatchStatus / RideControlButton / RideSaveButton /
│   │   RideCancelButton / RideViewDialog / RideDeleteDialog /
│   │   StopsDialog / FieldEditDialog / RideInfo (shared shape + mappers)
│   │   / StopGroup (consecutive stops at one place, display-only)
│   ├── RidesScreen.kt, RidesViewModel.kt
│   ├── PlacesScreen.kt, PlacesViewModel.kt, PlaceEditDialog.kt /
│   │   PlaceDeleteDialog.kt
│   ├── SettingsScreen.kt
│   ├── HelpScreen.kt            renders docs/quickstart.md, copied in
│   │                            by the build as res/raw/help.md
│   ├── AboutScreen.kt, LicenseScreen.kt
│   ├── RideViewModel.kt         binds to RideTrackingService
│   ├── KeepScreenOn.kt
│   └── theme
│
├── debug                        the Advanced screen and its plumbing;
│   │                            a drawer entry, in release builds too
│   ├── AdvancedScreen.kt
│   └── DbBackupUtils.kt         raw .db file backup/restore
│
└── util
    ├── EventLog.kt              on-device log of automatic events
    ├── UnitConversions.kt       SI ↔ display units and formatting
    ├── DistanceUtil.kt          haversine
    ├── GeohashUtil.kt           geohash encode
    ├── UriIo.kt                 SAF read/write helpers
    └── AppVersion.kt            version + git stamp
```

## Application Layers

### MainActivity

The Android entry point and the only Activity. It applies the theme and
hosts `AppNavigation`. It holds no application logic.

### UI layer

Compose + Material 3. `AppNavigation` owns the `NavHost`, the navigation
drawer, the runtime permission prompts (location, notifications, overlay,
battery optimization), the Import/Export overflow actions, and the
quit/cancel dialogs.

Screens are deliberately decomposed into small composables. Prefer adding
another small file over growing a screen file.

Routes (`ui/Screen.kt`): `ride`, `rides`, `places`, `settings`, `help`,
`about`, `license`, `advanced`.

`help` renders `docs/quickstart.md`. The build copies that one file into
`res/raw/help.md` (`copyQuickstart` in `app/build.gradle.kts`, registered
through the variant API because AGP 9 will not take a `Provider` on a
source set), so the repository copy, the README link, and the in-app
screen are the same text and cannot answer a question three ways.

The UI never talks to GPS or the database directly. It reads state from a
ViewModel and sends commands back.

### Service layer

Ride tracking lives in a **foreground service**, not a ViewModel, so it
survives the Activity being backgrounded or destroyed.

`RideTrackingService` (foreground service type `location`) owns a
`RideMeter` instance, the ongoing notification, and the draggable floating
"bubble" overlay (`TYPE_APPLICATION_OVERLAY`, requires
`SYSTEM_ALERT_WINDOW`) shown while the app is backgrounded mid-ride. The
notification and bubble are plain Android Views, not Compose.

The notification is re-posted by `refreshNotification()`, which runs when
`tracker.ride` emits, when `autoState` emits a **distinct** value, and in
`pause()` and `quit()`. That matters because of what it doesn't cover:
while the app is only watching for a departure, neither flow changes, so
nothing re-posts. From Android 14 the user can swipe a foreground
service's notification away and the service keeps running — during a ride
it reappears within a second, because the ride changes every second;
while watching, it stays gone until the state changes.

That is left alone rather than fought: `NotificationDismissedReceiver`,
wired up as the notification's delete intent, writes the dismissal to the
event log and puts nothing back. See "A dismissed notification is logged,
not re-posted" in `decisions.md`.

`RideViewModel` binds to the service through `LocalBinder`, mirrors the
service's `ride` and `gpsInfo` StateFlows into its own, and forwards every
command (`start`, `pause`, `resume`, `save`, `cancel`, `quit`).
`requireService()` throws if called before the bind completes, so UI must
call `startServiceIfNeeded()` first.

`RideMeter` (`service/RideMeter.kt`) is the actual state machine and the
densest file in the repo. Its inline comments document the stop-detection
and place-resolution reasoning — read them before changing that logic.

### Model layer

Plain Kotlin data classes. `Ride` is the in-flight ride held in memory and
streamed to the UI; `RideRecord` is the Room row. `Ride.toRecord()` maps
one to the other, falling back to `startTime` for `endTime` while the ride
is still running so the non-nullable column always has a value.

## Ride lifecycle

There are exactly three states — `READY`, `RUNNING`, `PAUSED` — and the
ride is fully persisted in every one of them. The row, its stops, and its
end location are current at all times, so there is never a half-written
ride to reconcile:

1. **`start()`** — inserts a `RideRecord` immediately, then re-writes it
   every 5 s (`PERSIST_INTERVAL`), so a crash mid-ride loses at most five
   seconds.
2. **First GPS fix** — sets `startLocation` and resolves `startPlaceId`
   right away, so the live screen can show the start place instead of
   waiting for the ride to end.
3. **Each stop** — detected, or added by the user with ADD STOP —
   writes its `Stop` row, adopts its place as the ride's *working* end
   place, and recomputes that place's average, all at the moment it is
   recorded.
4. **`pause()`** — stops tracking and provisionally ends the ride where it
   is: end location and end time are filled in, making a paused ride a
   complete, saveable ride. It deliberately does **not** resolve the end
   location to a `Place`.
5. **`resume()`** — clears the provisional end time and picks tracking back
   up. Distance and elapsed time continue, because `pause()` never reset
   the providers.
6. **`restore()`** — a ride the *previous* process was metering, picked
   up at service create. `LiveRideStore` holds the live ride's id outside
   the process, so a row still marked at startup is an interrupted ride
   rather than a finished one. It comes back **PAUSED**: between the
   death and now the vehicle may have driven fifty miles or sat still,
   and nothing recorded which, so resuming would bill a gap the app knows
   nothing about. What it did before the death is held in
   `priorMeters`/`priorSeconds` and added to the providers' totals
   if the user resumes, since the providers count from zero and no
   instance survived to continue.
7. **`save()`** — the single commit point, confirmed by the user first.
   Resolves the real end place, retracts a trailing stop that matches it
   (arriving is not a stop along the way), completes an automatic name
   with the end place, writes the row, recomputes every touched place,
   tears down the providers, and clears the live window.
8. **`cancel()`** — deletes the row outright at any status. Stops cascade
   with it (`Stop.rideId` is `ForeignKey.CASCADE`), then every place that
   lost a point is recomputed.

### Ride names

A ride is named at `start()`, not at `save()`: an unnamed running ride is
the one on screen, and `""` is no way to refer to it. The automatic name
is `MM/dd <start place> ->`, falling back to `MM/dd h:mm a` while the
start place is still resolving — a manual start has no place until the
first fix lands. `save()` completes it as `MM/dd <start> -> <end>`.

`RideMeter` tracks whether the name is still its own (`nameIsAutomatic`).
Anything the user types — before starting, or while the ride runs —
takes it over permanently, and nothing rewrites it afterwards. A name
cleared back to blank counts as never named. The end place is folded in
only at save, because while a ride runs `endPlaceName` is whichever stop
it last passed through, and a name that rewrote itself at every stop
would be noise.

Both `save()` and the periodic persist write the row from `activeSettings`,
the settings captured at `start()` — never from live settings — so a ride
always keeps the rates it was actually metered at.

### Why there is no FINISHED state

An earlier design had a fourth state: `finish()` stopped tracking and
batched the stop rows and place recomputation, leaving the ride on screen
until `save()`. That made finishing irreversible — you could not resume out
of it without deleting the inserted stop rows and undoing the place
averages.

Writing stops as they happen removes the thing that had to be undone.
`PAUSED` now covers both cases, because a paused ride is already complete;
`save()` inherited the commit work, and `finish()` no longer exists. This
is also why Settings can be locked for the whole of any live ride
(`RUNNING || PAUSED` is now every non-`READY` state), which is what keeps
live settings from ever reaching a ride in progress.

### Stop detection

A stop uses a **fixed** dwell anchor with a 30 m radius
(`STOP_DISTANCE_THRESHOLD_METERS`). While fixes stay inside the radius the
dwell clock keeps accumulating and the anchor does not move, so slow GPS
drift cannot repeatedly reset it. When a fix lands outside the radius,
movement has resumed: if the dwell met `Settings.stopDetectionMinutes`, it
is finalized as a `Stop` — place resolved, row written, place average
recomputed, immediately.

`addStop()` is the user saying so instead of waiting: it writes the
dwell already under way — same anchor, same start time, same row through
the shared `recordStop()` — without the dwell having met
`stopDetectionMinutes` and without movement having resumed. The
suppression that drops a first stop at the ride's start place does not
apply, because this one was asked for. It works while `PAUSED` as well as
`RUNNING`: the providers are stopped then, but the anchor and the last fix
both survive a pause.

**One dwell is at most one `Stop`.** Writing it early doesn't end it, so
`dwellStop` holds the row until the vehicle actually leaves the radius,
and that departure closes the row — giving it the real end time — instead
of detecting the same sitting a second time. Without that the duplicate
was trivially easy to produce: press Add Stop on arrival, sit longer than
`stopDetectionMinutes`, drive off, and the stops list showed the place
twice. Pressing Add Stop again at the same place extends that row for the
same reason rather than adding another: it is one visit lasting longer.

A stop also becomes the ride's **working end place**: the most
recent place the vehicle is known to have sat at is the best available
answer to "where does this ride end?" until the user says otherwise. It
costs nothing, since detecting the stop already resolved and wrote that
place. `save()` replaces it with a real resolve of wherever the ride
actually is.

### Sounds

Each ride action — start, pause, resume, save, cancel — plays a short
tone, so the user knows it happened without looking at the phone. They
are played from `RideMeter` rather than from the buttons, which is the
point: an automatic start fires with the phone in a pocket, and it has to
sound exactly like a tapped one.

`RideSounds` defaults to the platform's own `ToneGenerator` rather than
bundled audio — no assets, no decoding, and it already sounds like device
feedback rather than an app notification. Each action can instead be
pointed at a sound on the device: Settings opens
`RingtoneManager.ACTION_RINGTONE_PICKER`, so the list offered is the same
one Android's own sound settings show, and only the returned `Uri` is
stored (`Settings.startSoundUri` and friends, null meaning the built-in
tone). Per action rather than one for all five, because telling the
actions apart without looking is the point.

Either way it plays on the notification stream — `STREAM_NOTIFICATION`
for a tone, `USAGE_NOTIFICATION` for a picked sound — so it follows the
volume the user already set and goes quiet when the phone is silenced;
nothing here outranks silencing a phone. A `Uri` that stops resolving,
because the file was deleted or the app that supplied it was removed,
falls back to the tone rather than going silent: an action that makes no
sound is indistinguishable from one that didn't happen. That is also why
the picker doesn't offer "Silent" — `Settings.soundEnabled` is the off
switch, read live per sound rather than captured at `start()` like a
ride's rates, because unlike a rate it is a preference about the phone
right now.

## Providers

`DistanceProvider` exposes flows of `distance` and `gpsInfo`. Two
implementations are selected by `Settings.distanceProvider`:

* `GpsDistanceProvider` — fused location. `Settings.minimumSpeedMps` gates
  whether movement counts at all, defaulting to 3 mph (`1.34112` m/s) so a
  parked phone's drift isn't billed as distance; reported accuracy buckets
  into `DistanceStatus.GOOD` (≤ 10 m), `POOR` (≤ 30 m), or `WAITING`. See
  "The Defaults a Fresh Install Starts On" in `decisions.md` for why the
  threshold sits at walking pace rather than near traffic speed.
* `SimulatedDistanceProvider` — a scripted timeline of legs, so the whole
  UI can be exercised with no GPS hardware. Selected in Settings;
  `GpsDistanceProvider` is what a fresh install gets.

`TimeProvider` / `SystemTimeProvider` follows the same pattern for elapsed
time.

`RideMeter` receives a `(Settings) -> DistanceProvider` factory, so nothing
in the meter knows which implementation is in use.

## Data layer

Room, database `rides.db`, currently **version 7**, three entities:
`RideRecord`, `Place`, `Stop`. KSP runs the Room compiler; `Converters`
handles `RideLocation`.

Migrations are **real** from version 6 on. `data/Migrations.kt` holds
`MIGRATION_BASELINE` (6, the oldest version with an exported schema),
`MIGRATIONS` (the list, empty while the baseline is also the current
version), and `PRE_BASELINE_VERSIONS`. `AppDatabase.build()` hands Room
the migrations and then
`fallbackToDestructiveMigrationFrom(dropAllTables = true, 1…5)`, so a
version at or above the baseline with no migration written for it
**throws on open** instead of quietly dropping the tables. The schema is
exported to `app/schemas` by the Room Gradle plugin and committed: a
migration can only be written or tested against a schema that was
recorded, and the version before a change is the half that is otherwise
gone by the time it is needed.

Two upgrades still can't be migrated, and both rebuild from scratch: a
database from **below the baseline**, written before schemas were
exported, and a **downgrade**, where the running build has never heard of
the schema on disk (`fallbackToDestructiveMigrationOnDowngrade`). Those
two are what `SchemaRescue` is now for, and `rebuiltFromScratch()` in
`Migrations.kt` is what tells them from a migration.

`SchemaRescue` wraps the open on both sides:

* **Before** Room opens the file, `setAside()` reads its schema version
  with a plain `SQLiteDatabase` handle — checkpointing the write-ahead
  log into the file first, so the copy is the whole database — and if it
  isn't the current one, copies the file to `filesDir/pre-upgrade`. The
  newest five are kept. An unreadable version counts as a changed one:
  copying a file that didn't need it costs storage, the other way round
  costs the rides.
* **After** Room has rebuilt the tables — and *only* then —
  `carryForward()` copies every row back for the columns the two schemas
  share, places then rides then stops so a stop's foreign key finds its
  ride. `AppDatabase.build()` asks for `openHelper.writableDatabase` to
  force the rebuild to happen there rather than at whatever the first
  query turns out to be.

The set-aside copy is taken on **every** version change, migrated or not:
it is cheap, and a migration that turns out to be wrong is exactly the
case with nothing else left to fall back on. The carry-forward is the
half that is gated. Running it after a successful migration would copy
rows in on top of rows the migration already moved — duplicating every
one, or, since the ids come across too, failing on every insert and
reporting a loss that never happened. `copyTable` also refuses a
destination table that already has rows, as a second line of defence.

It is best-effort and not a migration: a column added takes its default,
a column dropped is left behind, and a column **renamed** reads as one of
each, so its data lands in neither. The set-aside file is the backstop —
whatever the carry-forward missed is still in a complete copy, shareable
off the device from the Advanced screen. `AppDatabase.lastRescue` holds the
one-line summary of what came across, and the same line goes to
`EventLog`.

The insert is a plain `INSERT` rather than `INSERT OR IGNORE`, and the
count reported comes from `SELECT count(*)` on the destination rather
than from counting calls that didn't throw. Both matter: `OR IGNORE`
turns a `NOT NULL`-with-no-default violation into a silent success, which
reports rows carried into a table that has none.

**Restoring a backup happens at the next process start**, not when the
button is tapped. `PendingRestore.stage()` copies the backup somewhere
the next launch will find it; `PendingRestore.apply()` runs at the top of
`AppDatabase.build()`, before Room opens anything, and puts it in place —
then deletes the stale `-wal` / `-shm` and clears `LiveRideStore`, whose
ride id belongs to the file that was just replaced. The restored file
goes on to meet the migrations, or `SchemaRescue`, exactly as an upgrade
would.

Swapping the file in the running process was what this used to do, and
closing Room's instance was not enough: `AppRepository` went on handing
out DAOs built on the closed handle, and every screen went on collecting
Flows from it. `AppRestart.restart()` ends the process and asks the
system to open the app again, but the restore does not depend on that
working — it lands at the start of whichever process opens the database
next, exactly once.

`AppDatabase.clearInstance()` exists for tests and for anything else that
needs a fresh connection to the file.

`AppRepository` is a hand-rolled singleton container that hands out the
DAOs and the place helpers — there is no DI framework. Ride reads and
writes during tracking go through the `RideRepository` interface
(`RoomRideRepository`); the raw `RideDao` is used only for bulk
export/import.

Settings live in DataStore Preferences (`SettingsRepository`), exposed as a
`Flow<Settings>` whose defaults match `Settings()`.

## Places

A place is a location a ride can start at, end at, or stop within. Three
flags are easy to conflate:

* **Matching** is only `distance <= place.radiusMeters` — the same rule for
  named and unnamed places (`PlaceResolver`). New placeholder places are
  created with a 200 ft (60.96 m) radius and a geohash for a name. The
  geohash is
  *only* the readable placeholder name; it plays no part in matching.
  Places overlap, so several can match one point: a named place is
  preferred over an unnamed placeholder, then the tighter radius, then the
  nearer centre. That is a tie-break, not a second rule — nothing outside
  its own radius is ever matched.
* **`isNamed`** is display-only. It does not gate matching or averaging —
  but it does decide what can be absorbed (below), and saving through the
  place editor always sets it, so an unnamed place is one no user has ever
  touched.
* **`locationLocked`** is set only by directly editing coordinates — *not*
  by naming a place — and is what stops `PlaceLocationRecalculator` from
  averaging.
* **`PlaceLocationRecalculator`** recomputes a place's coordinates as the
  average of every linked ride point and stop, from scratch on each call,
  *after* the rows are in the database.
* **`PlaceBoundaryEnforcer`** re-resolves points their place no longer
  fits. `enforceBoundary()` runs after **any** place save — an edit or a
  brand new place — and is three steps, in this order:

  1. Points linked to this place that now fall *outside* it are
     re-resolved away: the place shrank or moved out from under them.
  2. Points that now fall *inside* it are re-resolved, wherever they were
     filed before. This is what makes a place drawn later apply to
     history — name "Home Depot" 400 ft wide and the stops already
     recorded in the store answer to it. It works downward too: a
     tighter "Home Depot Windows" drawn inside claims what it covers,
     because `PlaceResolver` prefers the smaller radius.
  3. Unnamed placeholders inside this place that step 2 emptied are
     deleted — bookkeeping the app invented, now pointed at by nothing.
     One still holding points of its own (they can sit up to its own
     radius outside this place) is left alone.

  Only unnamed places are ever deleted, which is safe in a stronger sense
  than it sounds: saving through the editor always sets `isNamed`, so an
  unnamed place is one no user has ever touched. A named place inside
  another named place stays, and resolution keeps them apart by radius.

  `reattachDeleted()` runs after places are deleted and re-resolves every
  ride point and stop that pointed at one of them, from the location that
  point recorded — a deleted place is replaced by a geohash placeholder
  rather than leaving the row pointing at nothing. Every place that gained
  or lost points is recomputed, whichever path ran.

  Deletion is targeted at the ids just deleted rather than sweeping for
  anything dangling, because an imported ride may legitimately reference a
  place whose own import hasn't happened yet; re-resolving that would
  strand it on a placeholder the real place could no longer reclaim.
  `reattachOrphans()` is the unrestricted sweep and exists only as a
  an Advanced-screen action for rows orphaned before deletion re-resolved them.

  `enforceBoundaryForAll()` runs the whole pass over every named place and
  is likewise an Advanced-screen action, for databases that predate any of it.

`autoStart` / `autoSave` mark a place as one that begins or ends a ride by
itself — see *Automatic rides by location* below.

## Automatic rides by location

A place flagged `autoStart` begins a ride when the vehicle **leaves** it; a
place flagged `autoSave` ends one when the vehicle **arrives**. Flagging a
place is the only switch: there is no separate on/off for the feature, and
watching runs whenever any place carries a flag (see *Watching Has No
Master Switch* in `decisions.md`).

### The pieces

`PlaceWatcher` answers one question — did we just cross the boundary of a
watched place — and nothing else. It creates no rows, decides nothing about
rides, and reports `Departed` / `Arrived` crossings for
`RideTrackingService` to act on. Containment uses the same single rule as
`PlaceResolver`: `distance <= place.radiusMeters`.

`AutoState` is what the machinery is doing: `Off`, `WatchingDeparture`,
`WatchingArrival`, `WatchingResume`, `Suspended`, or `PendingSave`. `Off`
means nothing is flagged, so there is nothing to watch for at all;
`Suspended` means the ride is `PAUSED` with only an `autoSave` place
flagged, so there is no departure to watch for and nothing will happen
until the user resumes by hand. It is deliberately **not** a fourth
`RideStatus` — whether the app is watching is orthogonal to whether a ride
exists, and can be true alongside any status.

`RideTrackingService` points the watcher at the crossing that matters for
the ride's current status:

| Ride status | Watching for | On crossing |
|---|---|---|
| `READY` | departure from an `autoStart` place | `start()`, with the departed place as the ride's start |
| `RUNNING` | arrival at an `autoSave` place | `pause()`, then commit after a grace window |
| `PAUSED` | departure from an `autoStart` place | `resume()`, backdated the same way — the ride carries on rather than a new one beginning |

An automatic start passes the departed place into `RideMeter.start()`
rather than letting the ride discover its own start place. By the time a
departure is confirmed the vehicle has left, so the ride's first GPS fix
is already down the road; resolving that would create a new place beside
the one actually departed from and link the ride to it. Using the place's
own coordinates also keeps the start point inside the place it claims, so
`PlaceBoundaryEnforcer` won't later detach it. Averaging is unaffected —
a place's location is the mean of its linked points, and adding a point
equal to that mean leaves it unchanged.

The same lag applies to the clock and the odometer, so `Crossing.Departed`
also carries `leftAtMillis` (when the vehicle was first seen outside) and
`confirmedAt` (where the confirming fix put it). `start()` takes those as
`startedAtMillis` and `metersAlready`: the ride is dated from the
departure and seeded through `priorMeters`/`priorSeconds` with the
straight-line distance from the place to the confirming fix. Without them
a ride starts *at* the place but meters *from* down the road, and the
first leg — typically 15–75 s of driving — is never billed. `start()`
clamps the backdate to 15 minutes, so a stale or bad value cannot bill
time nobody drove.

An automatic **resume** is late by exactly as much, so `resume()` takes
the same two arguments and `rebaseForDeparture()` applies them to a ride
already running: the driven seconds are added to `priorSeconds`, while the
distance provider's running total is folded into `priorMeters` and the
provider reset — it still remembers where the ride paused, and would
otherwise re-measure the same gap on its first fix back. Distance there is
measured from where the ride paused, not from the place. A resume by hand
passes neither and is unchanged. See *An Automatic Start Is Backdated to
the Departure* in `decisions.md`.

`PAUSED` watches the same crossing as `READY` but means something
different by it: leaving an `autoStart` place resumes the paused ride
instead of starting a new one. A pause taken somewhere is the middle of a
job — lunch, a delivery, a wait — so driving off is carrying on, and
starting again would strand the paused ride and split one job in two.
Only `RideStatus` distinguishes them; the crossing is identical, which is
why `onDeparted` branches on status rather than the watcher reporting two
kinds of departure.

The one pause this does *not* apply to is a live grace window: `PendingSave`
short-circuits `reconcileWatching`, so an arrival counting down is not
also watching for a departure. Driving off during a countdown is answered
by the notification's **Resume** button, not by a second automatic
decision layered on top of the first.

### Battery

Watching for a departure has no ride to piggyback on, so its poll is the
entire running cost of the feature. Two things keep it small:

`Settings.autoWatchAccuracy` chooses how a sweep gets its fix, and the
default is **`GPS`** — one real fix per sweep, no tier below it. A GPS fix
resolves a place outright, so containment is decided the moment it
arrives. Everything described below under *cheapest fix first* belongs to
the other setting, `TIERED`.

The tiered path saves real battery — a confirmed, stationary vehicle uses
no GPS at all — but a coarse fix routinely cannot resolve a 60 m place, and
the escalation machinery that recovers from this costs more delay than the
saving was judged to be worth by default. The setting exists so the cost
can be measured (`adb shell dumpsys batterystats --charged
org.fossridemeter.app`) rather than guessed, and the tiered path removed if
it doesn't earn its keep.

* **Cheapest fix first** (`TIERED` only). A sweep takes a coarse fix
  (platform `FUSED`, else `NETWORK`, else `GPS`) every
  `Settings.autoWatchSeconds`, default 30. GPS is powered up only when the
  coarse fix cannot settle the question by itself:

  * it **disagrees** with the containment already believed, **cannot
    prove** that disagreement within its own accuracy, and something has
    **moved** since the last sweep (measured against that accuracy, so
    fixes jittering inside their own error radius read as parked); or
  * it **agrees**, but the belief it agrees with has never been confirmed
    by a fix precise enough to have decided it.

  That second case matters more than it looks. A ±1500 m fix agrees with
  *any* belief, so a badly seeded one is never revisited: every later
  coarse fix nods along, agreement suppresses escalation, and the watcher
  sits on a wrong answer indefinitely. `occupancyConfirmed` records
  whether a fix that could actually resolve containment has done so; until
  one has, agreement earns an escalation every
  `ESCALATION_COOLDOWN_MILLIS`. Once confirmed, a parked vehicle costs no
  GPS at all. A standing disagreement escalates anyway after five
  minutes.

  The first version escalated on ambiguity alone, which was close to
  backwards: a poor fix has a huge error radius, so it straddles every
  boundary, so weak signal — precisely when GPS is slowest and costliest —
  guaranteed a burst every single sweep, all night, parked in the drive.
* **Arrivals are free.** In `WatchingArrival` there is no poll. A running
  ride already streams GPS at 1 Hz, so the service feeds those fixes to
  the watcher through `submit()`.

### Why crossings are confirmed twice over

A single fix on the wrong side of a boundary must not start or end a ride.
A candidate crossing has to hold for 60 s **and** across at least two
fixes before it is believed — both, not either: time alone would admit one
stale outlier after a long gap, and count alone would let a 1 Hz ride
stream confirm an arrival within two seconds of driving past the driveway.

The first observation after arming only records which side we are on and
reports nothing, so sitting inside an auto-start place when watching begins
— the normal parked case — is not a departure.

### Reacting quickly without polling constantly

Those rules made a real departure fire two to three minutes late: up to a
minute to notice, then a minute of confirmation, plus fix acquisition. The
delay is fixed at the two points where it costs nothing to be quick,
leaving the idle cadence — which is where the battery actually goes —
untouched at 60 s.

* **The sweep is an alarm, not a timer.** `delay()` schedules onto a timer
  that does not wake a sleeping CPU, so with the screen off the poll ran
  when the phone happened to be up for other reasons: over one night
  parked, a 60 s interval measured a median of 60 s and a worst case of
  **32 minutes**, with 42 gaps over three minutes. A foreground service
  does not help — it keeps the process alive, not the processor awake.
  Sweeps are scheduled as `ELAPSED_REALTIME_WAKEUP` alarms instead, each
  one scheduling the next from its own `finally` so the interval can
  follow the candidate cadence and a long sweep cannot pile up behind
  itself. The alarm holds the CPU only for `onReceive`, so the sweep runs
  under a short `PARTIAL_WAKE_LOCK` of its own, released as soon as the
  fix is in.

  `setExactAndAllowWhileIdle` is what keeps time in Doze, and it is
  reliable for an app the user has taken off battery optimisation — which
  the app already asks for. Without that the code falls back to the
  inexact `setAndAllowWhileIdle`, which still wakes the device but at the
  system's convenience. `PlaceWatcher` logs which of the two it got at
  every arming, so a log with a gap in it says why.

* **The candidate cadence.** While a crossing is suspected, sweeps run
  every `CANDIDATE_POLL_SECONDS` (15) instead of the idle interval. This
  costs a few extra fixes per departure and nothing at all the rest of the
  time, because nothing is pending while parked.
* **Decisive departures skip the wait.** Two consecutive fixes each more
  than `DECISIVE_MARGIN_METERS` (100 m) beyond the boundary *and* beyond
  their own accuracy confirm immediately. The fix must itself be at least
  as precise as that margin: without that guard a ±1500 m cell-tower fix
  clears a ±1500 m threshold merely by being wrong, and network
  positioning is offset rather than random, so it can be wrong the same
  way twice running and start a ride from a stationary car. Two fixes are still required, so
  a stray reading still cannot start a ride; only the minute of waiting is
  waived, and that minute exists for the marginal case — jitter is not
  100 m past the edge twice running.

Arrivals deliberately get no fast path. A vehicle driving *through* a
large place looks just as decisively inside it, and confirming that early
would end a ride at a place the user never stopped at. Arrivals are fed
by the ride's 1 Hz stream anyway, so their latency is the confirmation
window alone.

### Arming is serialized

`reconcileWatching()` is called from five collectors — settings, the
ride, the places table, and the automatic paths — all on
`Dispatchers.Default`. Deciding the desired state and acting on it is a
check-then-act, and two of them racing performed both halves twice: the
event log shows one instant arming the watcher as `WatchingDeparture`
*and* `WatchingResume`, and each restart registering its own alarm
receiver while the other was still registered.

A broadcast reaches every registered receiver, so a receiver left behind
kept sweeping alongside the live one. Since they share `pollJob`, each
also cancelled the other's fix mid-request — the
`getCurrentLocation failed - The operation has been canceled` lines. A
phone parked overnight on a 60-second setting was taking 2.15 fixes a
minute, in bursts of two and three at the same instant, which is both a
battery cost and more chances for one stale reading to be counted twice.

Two things fix it. `reconcileWatching()` and the watcher's `start()`/
`stop()` are synchronized, so an arming completes before another begins;
and each receiver captures the generation it was registered in, so one
that outlives its arming stays quiet instead of sweeping.

### Diagnosing it

Automatic start cannot be watched over adb: it fires while the phone is in
a pocket and the car is pulling out of the drive, which is precisely when
it isn't plugged into anything, and logcat's ring buffer may or may not
still hold the evidence on return.

So `PlaceWatcher` and the automatic decisions in `RideTrackingService`
write to `util/EventLog.kt` as well as logcat — a plain file in the app's
`filesDir`, rolled over at 256 KB keeping one previous copy. It records
every sweep (provider, coordinates, accuracy, whether it escalated), each
arming and what containment it seeded, candidate crossings and how long
they held, confirmed crossings, and the auto state changes. A sweep that
gets no fix at all says so — that is the signature of having only
while-in-use location permission.

A fix is only evidence once. The same measurement handed back twice is
ignored, and identity is the measurement itself — latitude, longitude and
accuracy — not the timestamp attached to it: a receiver with nothing new
to say re-emits its last position with a *fresh* timestamp, which the
original timestamp-only check read as new evidence. That produced a false
auto-start at 03:57 on 2026-08-21, parked overnight: one stray fix landed
82 m from a 30 m place, was re-emitted every half minute, and three
"separate" observations of that single bad reading cleared both
confirmation bars. The ride ran for hours at 0.00 miles. Two fixes
agreeing to the last bit of a double are one fix replayed; a receiver
that is genuinely still measuring jitters in the seventh decimal.

Each sweep also prints the actual inputs to the containment test — for
every watched place, the distance to it and its radius, and whether that
counts as inside. The first version logged only the fix's accuracy, which
read plausibly as a distance and cost a drive to disambiguate.

Service creation is logged too, with why the previous process ended
directly after it (`util/ExitReasons.kt`, from `ApplicationExitInfo`,
API 30+). A restart is not a neutral event here: the meter comes back
`READY` with no memory of a ride that may still have been running, so the
signature of a lost ride is a long gap in this log, then "Service
created", then the watcher arming for a *departure* while the vehicle is
parked somewhere else entirely. Whether that was a crash, a low-memory
kill, an OEM freeze, or the user force-stopping the app decides which
fix it needs, and only the platform knows.

It's read back from the Advanced screen, which can also clear it. The same information, live, is on the ride screen (see
*Watch status* in `ui.md`). `EventLog` swallows its own failures: a logger that can break
the thing it observes is worse than no logger.

### Reboots and permissions

`BootReceiver` restarts the service after a reboot, but only if some place
is actually flagged. Auto-start exists for the mornings the user forgets
the app, and a phone that rebooted overnight would otherwise have forgotten
too.

This is what forces `ACCESS_BACKGROUND_LOCATION`. `location` is permitted
as a foreground service type from `BOOT_COMPLETED`, but *starting* is not
*reading*: a location service started while the app is in the background
receives no fixes at all without that permission — the notification would
appear and nothing would ever fire. It is requested lazily, when a place is
first flagged, rather than at launch with the other permissions.

`quit()` therefore no longer always stops the service. While any place is
flagged it closes the UI and drops to watch-only, and a ride that is
`RUNNING` or `PAUSED` holds it open the same way — `RideMeter.close()`
cancels the meter's scope, so stopping mid-ride ended the ride by side
effect and left a row nothing would ever finish. Only with nothing flagged
and no ride in progress does it shut down completely, as before.

## Units

Everything is stored and computed in **SI internally** — meters, seconds,
`perMeterRate` — and converted only for display, via
`util/UnitConversions.kt` (`displayDistance`, `distanceToUnit`,
`unitToDistance`, and the `DistanceUnit` extensions).

`Settings.measurementSystem` (US or SI) drives which unit the UI shows and
which unit the user types rates in. Do not let miles leak into stored
values.

## Amount calculation

`AmountCalculator` is pure:

```
distanceAmount = meters × perMeterRate
timeAmount     = elapsedSeconds / 3600 × hourlyRate
subtotal       = baseAmount + distanceAmount + timeAmount
total          = max(subtotal, minimumAmount)
```

A ride can also carry a `manualAmount` that overrides the calculated one
for display; both are stored on the row, so the original calculation is
never lost.

The rates themselves are snapshotted onto each `RideRecord`
(`perMeterRate`, `hourlyRate`, `baseAmount`, `minimumAmount`, plus the
`distanceProvider` in use), so changing Settings later never
retroactively alters a stored ride.

Time is billed two ways. `Ride.stoppedSeconds` — the seconds spent at
this ride's stops — bills at `Settings.stoppedHourlyRate`, and everything
else at `hourlyRate`. Both the seconds and the stopped rate are
snapshotted onto the `RideRecord` alongside the other rates, so a saved
ride still explains its own amount; `stoppedHourlyRate` is nullable there
and reads as `-` on a ride recorded before it existed, whose
`stoppedSeconds` is zero.

`RideMeter.stoppedSecondsNow()` recomputes the total from scratch on each
one-second tick rather than accumulating it, because the inputs move: a
stop's end time is corrected when the vehicle drives off, and `save()`
can retract a trailing stop entirely. The dwell under way is counted from
its anchor rather than from its row — a stop recorded by hand holds a
provisional end time until the departure gives it a real one — and counts
only once it has lasted `stopDetectionMinutes` or the user has called
it a stop. Anything shorter is a traffic light, and traffic lights are
driving.

A pause freezes it: `pause()` banks the total, and `resume()` shifts
`dwellAnchorTime` forward by the length of the pause, so both the stopped
total and the stop-detection threshold measure ride time rather than the
wall clock. Without that, pausing for lunch inside the dwell radius came
back as an hour of stopped time the ride's own clock never counted.

## Import / export

User-facing export and import go through the Storage Access Framework
(`util/UriIo.kt`), reachable from the TopAppBar overflow menu.

* **Rides** — JSON (`RidesBackup.kt`). A ride's stops travel inside the
  ride bundle. A ride referencing an absent place id renders `-`.
* **Places** — JSON export; import auto-detects JSON, CSV, or GPX
  (`PlacesBackup.kt`, `parsePlacesAuto`).

Rides and places export independently of each other.

`Screen.Advanced` / `AdvancedScreen` does raw `.db` file backup and
restore via `DbBackupUtils`, reads the event log, runs the place-link
repair (`PlaceBoundaryEnforcer.enforceBoundaryForAll()` then
`reattachOrphans()`), and lists the databases `SchemaRescue` set aside
during an upgrade so they can be shared off the device.

It is a normal drawer entry and ships in release builds — as **Advanced**,
not as a debug menu, because every item on it is something a user may
need on a phone that has never had adb attached. It is still not the
user-facing export feature; that is the JSON export.

The name is a promise, and two buttons keep it. **Restore** asks first,
naming the backup's date and saying the app will close, and is disabled
while a ride is running or paused — it replaces the database that ride is
being written into. **Repair place links** asks first as well, because
its orphan sweep cannot tell a deleted place's leftovers from a ride
import whose places haven't been imported yet.

## Build information

`versionName` is `0.1.1`. `compileSdk` / `targetSdk` 37, `minSdk` 26,
JVM toolchain 17.

Release builds run R8 — code shrinking, resource shrinking, and
obfuscation — which takes the APK from 14.1 MB to 1.9 MB, most of that
being unused Material icons. `app/proguard-rules.pro` is deliberately
short: Room, Compose, kotlinx.serialization and play-services-location
all ship consumer rules inside their own artifacts, so the only rules
this app adds are for what *it* knows and R8 can't see.

That is the enum constant names in `model`. Settings persist
`DistanceProviderType`, `DistanceUnit`, `MeasurementSystem` and
`WatchAccuracy` by `.name` and read them back with `valueOf()`;
`RideRecord.distanceProvider` makes the same round trip through a
database column; and every `@Serializable` enum is encoded into exported
JSON by constant name. Renaming one is not obfuscation, it is a data
format change, and it would surface as an `IllegalArgumentException`
while reading settings at launch. Verified in `mapping.txt`: `GPS -> GPS`,
`SIMULATOR -> SIMULATOR`.

`AppDatabase` and `AppDatabase_Impl` keep their fully-qualified names
through Room's own consumer rule, which is what makes Room's
`Class.forName(name + "_Impl")` work in a shrunk build.

**Keep `app/build/outputs/mapping/release/mapping.txt` for any build
given to anyone** — without it a stack trace from that build cannot be
read back.

`BuildConfig.GIT_VERSION` (short hash, `-dirty` suffix when the working
tree is dirty) and `BuildConfig.BUILD_TIME` (epoch millis) are stamped at
configure time and shown on the About screen, so an installed build traces
back to a commit. `AppVersion.buildTime` formats that instant in the
device's current timezone, so the About screen reads as local time:

```
0.1.1+6354923-dirty
```

Because these run `git` during configuration, changing the tree's dirty
state invalidates the Gradle configuration cache.

Release signing reads `keystore.properties` if present; it and
`local.properties` are local-only and gitignored.

Every `.kt` file carries the GPL SPDX header from `license-header.txt`.
Run `./add_license_headers.sh` (idempotent) after adding source files.
