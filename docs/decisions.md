# FossRideMeter Design Decisions

A running log. Superseded decisions are kept and marked, rather than
deleted, so the reasoning stays visible.

## Project Philosophy

FossRideMeter should remain simple, understandable, and maintainable.

Prefer:

* Small focused files.
* Clear separation of responsibilities.
* Working commits.
* Simple solutions over unnecessary complexity.

## Decision: Kotlin + Jetpack Compose

Selected because:

* It is the current Android development approach.
* Compose simplifies UI development.
* It works well with modern Android tooling.

## Decision: Keep MainActivity Small

MainActivity should not become the application.

It only applies the theme and hosts `AppNavigation`.

Business logic belongs in services and models.

## Decision: Separate UI from Logic

The UI should display information. It should not calculate ride values,
manage GPS, or touch the database directly.

Bad:

```
RideScreen
 ├── GPS logic
 ├── calculations
 └── database
```

Preferred:

```
RideScreen
      |
      v
RideViewModel
      |
      v
RideTrackingService → RideMeter
                        |
                        v
       DistanceProvider / TimeProvider / AmountCalculator / RideRepository
```

## Decision: Tracking Lives in a Foreground Service, Not a ViewModel

A ViewModel dies with its Activity. A ride does not.

`RideTrackingService` is a foreground service (type `location`) that owns
the `RideMeter`, so tracking continues when the app is backgrounded or the
Activity is destroyed. `RideViewModel` binds to it, mirrors its state
flows, and forwards commands — it holds no ride logic of its own.

The cost is the bind lifecycle: `requireService()` throws if called before
the bind completes, so UI must call `startServiceIfNeeded()` first.

## Decision: Providers Behind Interfaces

`DistanceProvider` and `TimeProvider` are interfaces, and `RideMeter`
takes a `(Settings) -> DistanceProvider` factory rather than constructing
one itself.

This buys two things:

* `SimulatedDistanceProvider` can drive the entire UI with no GPS
  hardware, no permissions, and no going outside. It is the default on a
  fresh install for exactly that reason.
* Nothing in the meter knows or cares which implementation is running.

## Decision: The Ride Is Persisted as It Happens

The `RideRecord` is inserted at `start()`, not at the end, and re-written
every 5 seconds while tracking. Each `Stop` row is written the moment that
stop is detected. `pause()` fills in an end location and time.

A phone can die, be killed by the OS, or crash mid-ride. Writing only at
the end means losing the entire ride; writing continuously means losing at
most five seconds. Fields that aren't known yet are written as
placeholders and overwritten as they become real.

Applying this to stops and the end point as well — rather than only to the
row — is what collapsed the state machine to three states, because it left
nothing that a finish step had to commit. It relies on
`PlaceLocationRecalculator` rebuilding a place's average from scratch on
every call: incremental writes are safe precisely because nothing
accumulates that a later recompute can't correct.

## Decision: Three States — No Separate FINISHED *(supersedes the below)*

A ride is `READY`, `RUNNING`, or `PAUSED`. From `READY` you can only
start; from `RUNNING` you can pause, save, or cancel; from `PAUSED` you can
resume, save, or cancel.

This works because everything is persisted as it happens. Stop rows are
written at detection time rather than batched, and `pause()` provisionally
fills in the end location and time, so a paused ride is already a complete
ride — there is nothing a finish step would need to commit that isn't
committed already, and therefore nothing to undo on resume.

Two things had to be got right to make it work:

* **The end place is not resolved while running.** `PlaceResolver` creates
  a row when nothing matches, so resolving continuously — or on a pause
  that may yet resume — would fill the places table with locations the
  user merely drove through. Instead the working end place is whatever
  the last detected stop resolved to (already created, so it costs
  nothing), and the real resolve happens only on a confirmed save.
* **The trailing stop is retracted, not pre-empted.** A stop at the place
  the ride ends is written when detected, because at that moment it *was*
  a stop. Only ending the ride there retracts it. Resume and drive on, and
  it stays — correctly, since by then it really was a stop along the way.

Save therefore became the single commit point and the point of no return,
so it asks for confirmation. Cancel has to recompute the places its stops
fed, since those rows are real by then.

The single exception is an automatic arrival, which reaches `save()`
without a confirming tap — see *Automatic Save Pauses First* below for
what replaces the confirmation there.

A side benefit: `RUNNING || PAUSED` is now every non-`READY` state, so
Settings is locked for the whole of any live ride and live settings can
never reach a ride in progress.

## Decision: FINISHED Is a Separate Status from Saved *(superseded)*

`finish()` stopped tracking and filled in the end location, time, and
place, but left the ride on screen with its name and amount still
editable. `save()` committed the last edits and cleared the live window.

The reasoning was that finishing a ride and describing a ride are two
different actions, and the user is usually doing the second one while
parked, after the first. That much is still true — but it argued for a
*state*, and the state was the expensive part: because `finish()` batched
the stop writes and the place recomputation, it could not be reversed, so
a user who finished by mistake had no way back. Editing after the fact
now happens on the Rides screen, which serves the same need without a
one-way door on the ride screen.

## Decision: Automatic Save Pauses First, Then Commits

An arrival at an `autoSave` place does not save the ride. It pauses it,
starts a grace window of `Settings.autoSaveGraceMinutes` (default 5), and
commits only when that elapses. Resuming — from the app or the
notification's own button — takes it back.

Saving outright on arrival was the obvious implementation and was
rejected. Save is the point of no return and everywhere else in the app it
asks first; an automatic save is the one that *cannot* ask, which makes it
the worst possible place to also make it irreversible. A user who pulls
into the depot to drop something off and carries on would find the ride
already closed behind them.

Pausing costs nothing, which is what makes this cheap rather than a
compromise: `pause()` already writes the end location and end time, so a
paused ride is a complete, saveable ride sitting in the database. The grace
window is not protecting unsaved work — there is none — it is protecting
against the *wrong* ending being made permanent.

The window is therefore a substitute for the confirmation, not an absence
of one: the user is told what is about to happen, on a notification that
carries both answers, and silence means yes.

## Decision: An Automatic Start Is Backdated to the Departure

A ride begun by auto-start does not begin now. It is dated from the moment
the vehicle was first seen outside the place, and its odometer starts at
the straight-line distance from that place to where the confirming fix
found it — `Crossing.Departed` carries `leftAtMillis` and `confirmedAt`,
and `RideMeter.start()` takes them as `startedAtMillis` and
`metersAlready`.

The problem is confirmation lag, and it is not an edge case: a crossing is
believed only after two fixes agree, and unless the departure is decisive,
only after a minute of them agreeing. So the crossing is always reported
from somewhere down the road — typically 15 to 75 seconds and a few
hundred metres past the boundary.

Handing the departed place to `start()` fixed half of that and made the
other half visible. The ride correctly claimed to have started at the
place, with the place's own coordinates as its start point, and then
metered from wherever the confirming fix landed. The first leg of every
automatic ride was silently unbilled, and the ride's start location was a
point it had never measured anything from — a ride that says it began at
home, at 08:14, having travelled zero metres, while the user was already
at the end of the street.

The distance is a straight line and so slightly short of the road actually
driven. That is the whole known cost, and it is the right trade: short by
metres against short by the entire first leg. Curving it would mean
recording a path nobody sampled.

The backdate is clamped to 15 minutes. Nothing should hand `start()` a
departure older than the five minutes a stationary candidate can take to
settle, and a ride whose clock starts an hour behind bills an hour nobody
drove — so a value that far out is treated as a bad reading rather than
believed.

`priorMeters`/`priorSeconds` carry the seed, the same fields a restored
ride uses. They were named for restoration and are not about it: they mean
"what this ride had already done before these providers started counting",
which is true of a ride recovered from a dead process and of a ride that
was already driving when something noticed.

**The auto-*resume* path is corrected too.** Leaving an `autoStart` place
while `PAUSED` resumes the ride, and is late by exactly as much, so
`resume()` takes the same two arguments and `rebaseForDeparture()` applies
them to a ride already under way. Distance is measured from where the ride
*paused* rather than from the place: `pause()` recorded that point and the
ride was metered right up to it, so it is the last position the odometer
knows. The place's coordinates are only the fallback for a ride paused
before it ever got a fix.

Time and distance are handled differently there, and not for symmetry's
sake. Time is added to: the gap between the pause and the departure is a
parked vehicle and is not billable — that is what pausing means — so only
the driving since the departure is owed, and the time provider accumulates
across a pause on its own.

Distance cannot be added the same way, because the distance provider still
remembers where it was when the ride paused, and its first fix after
resuming measures from there. That is the very gap being added — counted
twice — *except* when the gap clears the 100 m the provider discards as
drift, which is precisely the case where measuring it would have been
right. Neither half is usable, so the provider's running total is folded
into `priorMeters`, the gap goes on top, and the provider is reset to
count the rest from zero with no memory of where the ride was parked.

A resume by hand passes neither argument and is untouched: the user is
pressing the button where the vehicle is, so there is no gap to account
for, and the providers keep their totals exactly as before.

## Decision: A Paused Ride Resumes on Departure

Leaving a place flagged `autoStart` while a ride is `PAUSED` resumes that
ride instead of doing nothing. `onDeparted` branches on `RideStatus`:
`READY` starts, `PAUSED` resumes, `RUNNING` ignores it.

Watching was originally stood down entirely while paused, on the argument
that a pause is a deliberate act and re-arming underneath it would fight
whoever caused it *(this reverses that; see the superseded note below)*.
The argument was right about the crossing and wrong about the conclusion.
A pause taken at a place is the middle of a job — lunch, a delivery, a
wait at the depot — and driving off from it means "carry on". The old
behaviour left the user with a ride that quietly stopped metering the
moment they pulled out, and the only way to notice was to open the app.

Starting a *new* ride there would be worse than doing nothing, which is
why this could not be handled by simply leaving `WatchingDeparture` armed:
the paused ride would be stranded, and one job would be split in two. The
crossing is identical; only what it means differs, so the meaning lives in
`onDeparted` rather than in a second kind of departure from `PlaceWatcher`.

A live grace window is excluded. `PendingSave` short-circuits
`reconcileWatching`, so an arrival that is counting down is not also
watching for a departure — driving off during a countdown is answered by
the notification's **Resume** button. Automatic decisions do not stack:
the countdown is already the one thing the app does without asking, and
layering a second automatic reversal on top of it would leave no state the
user could predict from the notification in front of them.

The cost is a departure poll while paused, which is the same poll `READY`
already pays. `Suspended` remains for the case where nothing can happen —
paused with only an `autoSave` place flagged.

## Decision: Watching Stands Down While Paused *(superseded)*

`PAUSED` armed nothing: a pause was either the user's deliberate act or
the grace window of an arrival that already fired, and re-arming
underneath either was held to fight whoever caused it.

That reasoning survives intact for the grace window, which is still
excluded. It did not survive for an ordinary pause, because "don't fight
the user" turned out to argue the other way: a user who pauses at a
stop and drives off wants the ride to continue, and standing down is what
fought them.

## Decision: Watching Has No Master Switch

Whether the app is watching for place crossings is decided entirely by
whether any place carries `autoStart` or `autoSave`. There is no global
on/off, in Settings or anywhere else.

A master switch was designed and discarded. The argument for it is real —
without one, flagging a single place means the service runs, and the
notification shows, indefinitely. The argument against it is fatal: the
feature exists for the mornings the user forgets to open the app, and a
switch that must be remembered fails in exactly that case. It would have
been one more thing to forget, guarding a feature whose entire purpose is
forgetting.

The consequences were accepted deliberately:

* Watching runs continuously once a place is flagged. The cost is bounded
  by making the poll cheap rather than by making it optional — see
  *Automatic rides by location* in `architecture.md`.
* `quit()` no longer necessarily quits. While any place is flagged it drops
  the service to watch-only rather than stopping it, because the habit of
  quitting at the end of a shift would otherwise disarm the next one. A
  ride in progress holds it open for a different reason: stopping the
  service calls `RideMeter.close()`, which cancelled the meter's scope and
  ended the ride by side effect. Quitting closes the UI; only `save()` and
  `cancel()` end a ride.
* Un-flagging every place is what turns the feature off. That is a real
  cost — it means editing configuration to stop a behaviour — and it is
  the price of not having a switch to forget.

A second exit — *Quit everything*, alongside the one that only closes the
UI — was raised later and rejected for the same reason. It is the master
switch with a different label, and a worse one: a Settings toggle is at
least somewhere a user would think to look, while "I pressed the other
Quit last night" is invisible the next morning, which is exactly the
morning the feature exists for.

The genuine need behind it — *stop this for good without uninstalling* —
is answered by the platform instead. Force-stopping the app puts the
package in Android's stopped state, so no `BOOT_COMPLETED` reaches
`BootReceiver` and the pending sweep alarm dies with the process; it stays
off across reboots until the app is launched by hand. That is a better
off switch than one built here, because it lives where a user goes to
stop an app misbehaving, and because getting back out of it is the
ordinary act of opening the app.

What was owed was the *telling*, not the switch. The quit dialog claimed
"this will stop tracking and close the app" long after that stopped being
true, and Settings → Automatic explained the missing switch only in a
source comment. Both now say what happens and how to stop it.

## Decision: LocationManager, Not Geofencing

`PlaceWatcher` polls through the platform's `android.location.LocationManager`
rather than using the Play Services Geofencing API, which is the obvious
tool for exactly this job and is better at it: OS-batched across every app
on the device, movement-aware, and free of any polling at all.

It was rejected on dependency grounds. `GpsDistanceProvider` is the app's
only Google Play Services dependency and the only file that would have to
change to remove it; geofencing would have made that dependency permanent
and structural in a GPL application whose distribution channel is still
undecided (see *Free Application Model*). A fix a minute needs nothing
fused.

Note that geofencing would **not** have saved the background-location
permission — it requires it too, on API 29+. That was the strongest
argument for it and it turned out not to be true, which left battery as
the only advantage, and battery is answerable by making the poll cheap.

## Decision: Auto Mode Is Not a Ride Status

`AutoState` is a second, orthogonal value on the service rather than a
`WATCHING` entry in `RideStatus`.

Adding a status would have been smaller in the moment and wrong: a ride is
`READY`, `RUNNING` or `PAUSED`, and whether the app happens to be watching
for a place crossing is not a fact about the ride at all. The app watches
*while* `READY` and *while* `RUNNING`, for different things — states that
can be true simultaneously are not alternatives, and the three-state model
(above) only works because nothing else has been smuggled into it.

## Decision: An Automatic Start Belongs to the Place It Left

`RideMeter.start()` accepts the place an automatic start departed from,
instead of resolving the ride's start place from its first GPS fix the way
a manual start does.

A confirmed departure means the vehicle has already gone — that is what
makes it a departure. The first fix of the ride therefore lands down the
road, and resolving it created a brand new place a block from the one the
user actually left, then linked the ride to that. Every automatic ride
littered the places table with a near-duplicate of a place that already
existed, and the ride history showed the wrong origin.

Using the place's stored coordinates also keeps the start point inside the
place it points at, which `PlaceBoundaryEnforcer` requires — a start point
outside its own place would be detached and re-resolved on the next edit.

Averaging is not distorted by this: a place's location is the mean of its
linked points, and adding a point equal to the current mean leaves it
where it was.

## Decision: Default Place Radius Is 200 ft

Auto-created places get a 200 ft (60.96 m) radius, raised from 25 m.

25 m was chosen when places only had to be distinguished from each other
after the fact, where a tight radius is a virtue. Automatic rides changed
what the number has to survive: a radius smaller than a coarse location
fix's error cannot be resolved at all without spending GPS on it, so tight
places made the cheap tier of `PlaceWatcher` useless exactly where it
mattered. 200 ft covers a driveway or a small lot rather than a single
parking space, and can be read off a coarse fix on a good day.

Existing places keep their radius; this only affects newly created ones.
Imported places without a radius column still use their own 50 m default
in `PlacesBackup.kt`.

## Decision: Row Selection Lives in the ViewModel

`SelectionState` is held by `RidesViewModel` and `PlacesViewModel`, not by
the screens that draw the tables.

The obvious home for "which rows are highlighted" is the screen, and it
would work right up to the point where something outside the screen has to
read it. The contextual top bar is drawn by `AppNavigation`, because it
*replaces* the app bar rather than appearing inside the table — so the
count, "select all" and delete all live outside the composable that owns
the rows. Hoisting the state to the ViewModel both of them already share
was cheaper than routing callbacks up through the NavHost.

The empty set doubles as "not in selection mode". A separate boolean would
be a second source of truth for the same fact, and the two would
eventually disagree — deselecting the last row is exactly the case that
gets missed.

## Decision: Store SI Internally, Convert Only for Display

Distances are meters, times are seconds, rates are per meter — always,
everywhere in storage and computation. `util/UnitConversions.kt` is the
only place units are converted, and only for display or for parsing what
the user typed.

Mixed units are a persistent source of silent, hard-to-find errors. Miles
must not leak into stored values.

## Decision: Place Matching Is Radius Only

A point matches a place when `distance <= place.radiusMeters`. That is the
whole rule, and it is the same rule for named and unnamed places.

A geohash is used only as a readable, typeable placeholder name when a new
place is auto-created. It plays no part in matching — geohash cell
boundaries fall in arbitrary places, so two points a meter apart can land
in different cells, which is the wrong behavior for "am I at this place?".

Places overlap, though, so the rule can return several matches, and
something has to pick. That used to be whichever row the database handed
back first — table order, in other words, which is no answer at all: a
leftover placeholder could shadow the "Home Depot" drawn around it
forever, and which one won depended on insertion order nobody could see.
The order now is: a **named** place beats an unnamed placeholder (the
user named one, the app invented the other), then the **tighter radius**
wins (the more specific answer — a shop inside a mall is the shop), then
the **nearer centre**. It is a tie-break among things that already match,
not a second matching rule; nothing outside its own radius is ever
matched by it.

## Decision: Deleting a Place Re-Resolves What Pointed At It

Rides and stops carry a plain `placeId` column, not a foreign key, so
deleting a place can never cascade into ride history — losing rides would
be far worse than losing a label on them. That left the deletion's other
half unanswered for a while: the rows kept an id pointing at nothing, and
a ride read `-` where its place had been while a stop read `?`.

Deleting a place removes the *label*, not the *fact that the vehicle was
there*. The point is still recorded on the row, so the answer is the one
already used when an edited place stops covering a point it used to:
re-resolve through `PlaceResolver` (see "Place Matching Is Radius Only").
The point either lands on some other existing place it falls within, or
gets a brand new geohash placeholder — the same row a ride metered today
would have produced. `PlaceBoundaryEnforcer.reattachDeleted()` runs after
`deleteByIds()`, and the order matters: resolving first would match the
very place being deleted.

This does mean deleting an unnamed placeholder immediately re-creates one
at the same spot under the same geohash name. That is correct rather than
annoying — a place is where rides happened, and deleting the row does not
un-happen them. Deleting a *named* place is the case that matters, and it
does what was asked: the name is gone, the visit is not.

Re-resolution is targeted at the ids just deleted rather than sweeping for
any dangling reference. A ride import whose places have not been imported
yet dangles exactly the same way (see `RidesBackup`), and re-resolving
those would strand the rides on placeholders that the real places, arriving
minutes later with their original ids, could no longer reclaim.
`reattachOrphans()` is that unrestricted sweep, and it stays a debug-menu
action for repairing rows orphaned before any of this existed — it cannot
tell a deletion's leftovers from an import still waiting for its places,
so a human decides when it is safe.

A row whose point was never recorded at all has nothing to resolve from;
its id is cleared instead, which reads identically and leaves nothing
dangling.

## Decision: A Place Drawn Later Applies to History

Places are created bottom-up: the first time a point falls outside
everything known, a placeholder appears around it with a 200 ft radius and
a geohash for a name. Naming happens later and often bigger — the user
draws "Home Depot" 400 ft wide so it covers the store and its parking lot.

Nothing used to connect those two acts. The stops recorded inside the
store had each resolved to their own placeholder months earlier and kept
them, so the ride list showed a column of geohashes inside a place the
user had already named. Preferring named places in resolution (see
"Place Matching Is Radius Only") fixes what happens *next* time; nothing
re-resolved a point once it was filed.

So saving a place re-resolves the points inside it — all of them, whatever
they are currently attached to. `PlaceResolver` decides where each one
lands, exactly as it would for a point metered today, and the new place
wins the ones it should. The unnamed placeholders that end up holding
nothing are then deleted, because a placeholder is bookkeeping the app
invented for itself and an empty one is just clutter in the places list. A
placeholder still holding points of its own — they can sit up to its own
radius outside the new place — is left alone.

The same mechanism runs downward, and that is what makes sub-places work:
drawing "Home Depot Windows" 80 ft wide *inside* Home Depot takes back the
stops it covers, because resolution prefers the tighter radius. So the
coarse view and the fine view are the same feature seen from two
directions, and neither is a one-way door.

Only unnamed places are ever deleted by this, which is a stronger
guarantee than it sounds: saving through the place editor always sets
`isNamed`, so an unnamed place is one no user has ever touched. A named
place inside another named place is left alone.

The alternative was to leave the placeholders and merely prefer the named
place for new points. That keeps history "as it was recorded", but the
recording was never a judgement — it was the app's guess in the absence of
a name. The user naming the area *is* the judgement. A place is where
rides happened, and the points don't move; only the label they answer to
does.

## Decision: Stops Group for Display, and Are Never Merged

Once Home Depot covers the whole store, one visit is several stops —
park, sit, move to another door, sit again — and the ride reads "Home
Depot → Home Depot → Home Depot", which tells the user nothing.

The obvious fix is to merge those rows: keep the first, extend its end
time over the last, delete the rest. It is also the wrong one, and for a
reason worth writing down: those rows are the only record of *where
inside* the place the vehicle actually was. Merge them and the lumber
yard, the garden centre and the returns door become one averaged point,
and no place drawn afterwards can ever tell them apart again. The merge
would be permanent, silent, and unrecoverable.

So the grouping is display-only (`ui/StopGroup.kt`). Consecutive stops at
one place render as a single visit — in the rides table, in the
`Start → … → End` summary, and as one entry in `StopsDialog` with its
dwells listed underneath. The rows keep their own points and times.

That buys a property the merge could never have: the moment a tighter
place claims one of those dwells, it separates back out on its own,
because it no longer resolves to the same place as its neighbours. The
display follows the data instead of replacing it.

Only *consecutive* stops group. Leaving and coming back later is two
visits and reads as two. An unresolved stop never groups — least of all
with another unresolved stop, since "somewhere" and "somewhere else" are
two absences of an answer, not one place.

A grouped visit's duration is the wall-clock span from the first dwell's
start to the last one's end, so it counts the minutes spent moving between
them. That is the honest answer to "how long were you at Home Depot", and
the individual dwells are right there for anyone who wants the other one.

## Decision: A Place Is Named From the Stop That Found It

To draw "Home Depot Windows" the user needs to know where the windows
department was — and a stop's coordinates were, until now, in the database
and nowhere else. The place editor takes typed coordinates, but nothing on
screen said what to type.

`StopsDialog` therefore shows each dwell's own recorded point, and tapping
it opens the place editor on a new place already sitting on that spot.
It is the one screen where the question "which part of this place was
that?" comes up, and the one that already knows the answer.

The proposed place is unsaved — a `Place` built by `placeAt()` with an id
no row carries yet — and goes through the *same* editor and the *same*
save path as any edit (`PlacesViewModel.updatePlace` upserts). A place
named by hand is therefore not a second kind of place: it is re-resolved
on save like every other, which is what pulls the stop onto it.

Its default radius is 80 ft, far tighter than the 200 ft an auto-created
placeholder gets, because this one is being drawn *inside* something
bigger and a generous radius would swallow the neighbouring aisles it
exists to tell apart.

## Decision: Naming a Place Does Not Lock Its Location

`isNamed` and `locationLocked` are separate flags because they answer
separate questions.

A named place should keep converging on its true center as more rides link
to it — naming is about display, not about asserting coordinates. Only
directly typing coordinates sets `locationLocked`, and only that stops
`PlaceLocationRecalculator` from averaging.

## Decision: Fixed Dwell Anchor for Stop Detection

While fixes stay within 30 m of the anchor, the dwell clock accumulates
and the anchor does not move.

A rolling anchor that followed each new fix would let slow GPS drift reset
the clock indefinitely, so a genuine long stop would never be detected. A
fixed anchor cannot be defeated that way.

A trailing stop at the same place the ride ended is dropped: arriving is
not a stop along the way.

## Decision: A Fix Only Counts When It Can Resolve the Boundary

`PlaceWatcher` drops a fix whose accuracy radius straddles a watched
place's edge, rather than reading its coordinates as a verdict. Such a
fix neither builds a candidate crossing nor abandons one.

Containment was originally the same `distance <= radius` test used
everywhere else, with accuracy consulted only when deciding whether to
escalate to GPS. Parked overnight in a garage under a metal roof, with
`autoWatchAccuracy = GPS`, that produced 17 departures and 17 arrivals
from a vehicle that never moved. The reason is visible in the numbers:
against a 30 m radius the median fix sat 25 m from the centre with 20 m
of claimed error, so three quarters of all fixes could not actually tell
inside from outside - and every one of them was still counted as though
it could.

The same run also showed `getCurrentLocation` answering with a cached
fix: a quarter of sweeps were byte-identical to the sweep before, which
let a single reading satisfy the two-observation bar on its own. Fixes
are now matched on their own clock and a repeat is not counted twice.

Replayed against that night's log, the two rules together take 17 false
departures down to 3.

The three that remain are fixes that were confidently wrong - 91 m from
home reporting 10 m accuracy, while the vehicle sat still. No
accuracy-based rule catches a receiver that lies about its accuracy,
and neither does distance or progression: that burst ran 74 m, 90 m,
91 m, monotonically outward, before snapping back.

Speed does, for free. GNSS speed is a Doppler measurement, not a
difference of two positions, so it stays near zero for a stationary
receiver whose position is wandering. A departure now requires the fix
to report movement as well as distance, which costs nothing in time
because the value was already aboard the fix. Fixes reporting no speed
are counted as before, and standing outside for five minutes confirms
on distance alone, so leaving and then parking still registers.

## Decision: No Dependency Injection Framework

`AppRepository` is a hand-rolled singleton container.

For a single-module app with a handful of dependencies, Hilt or Koin costs
more in build time, generated code, and concepts than it returns.
Revisit if the graph grows.

## Decision: Real Migrations From Version 6 *(supersedes the below)*

Room exports its schema to `app/schemas`, the JSON is committed, and
`AppDatabase` is given real `Migration` objects plus
`fallbackToDestructiveMigrationFrom(dropAllTables = true, 1…5)`.

**Why version 6 and not version 1.** A migration can only be written or
tested against a schema that was recorded, and nothing before 6 was —
`exportSchema` was off. Reconstructing five schemas from git history to
serve devices that don't exist is work with no audience; every install
is on 6. So 6 is `MIGRATION_BASELINE`, everything below it keeps the old
behaviour, and the rule for the future is: don't lower the baseline to
make an old database open, write the migration.

**What this actually buys.** Not just that data survives an upgrade —
that `SchemaRescue` mostly did already. It is that a version at or above
the baseline with no migration for it now *throws on open*. The failure
mode it replaces was silent: change an entity, bump the version, ship,
and the tables go. Now it fails on the developer's phone at the moment
of the change. `MigrationConfigTest` moves that earlier still, to
`./gradlew test`, with no device in the loop.

**What is still destructive**, and deliberately: a database from below
the baseline, and a downgrade to a build that has never heard of the
schema on disk. Both are cases where there is nothing to migrate *with*.
`SchemaRescue` is what those two cost — see "The database is still set
aside before every upgrade" below.

## Decision: Destructive Migrations, For Now *(superseded)*

The database uses `fallbackToDestructiveMigration(dropAllTables = true)`.

This is acceptable *only* because there are no outside users and no data
worth preserving. It must be replaced with real migrations before the app
ships to anyone. This is tracked as the top item in `roadmap.md`.

## Decision: Use Git Build Identification

A build number alone is not enough during development.

The About screen displays the release version, a git identifier, and the
UTC build time:

```
0.1.0+6354923-dirty
```

The `-dirty` suffix marks a build made from an uncommitted tree. This
allows every test installation to be identified. The cost is that the
Gradle configuration cache is invalidated whenever the tree's dirty state
changes.

## Decision: Keep Main Branch Buildable

The main branch should always:

* Build successfully.
* Install successfully.
* Run successfully.

Features should be completed in small steps.

## Decision: Debug Database Tools Stay Debug-Only

**Superseded** — see "The debug menu is visible while it is
load-bearing" and "The Advanced screen is a user-facing screen" below. The reasoning still holds; what changed is that the
set-aside databases are on that screen, and an unreachable safety net is
not one. Real migrations have since taken most of that reason away
without settling the question — the menu is still visible.

Raw `.db` file backup and restore is a developer tool. It is gated behind
`BuildConfig.DEBUG` and hidden behind a long-press on "About".

The user-facing equivalent is JSON export/import through the Storage
Access Framework, which is portable, inspectable, and doesn't depend on
the schema version matching.

## Decision: An Interrupted Ride Comes Back Paused

The system reclaims processes under memory pressure without warning and
without anything being wrong — a location foreground service is a late
choice, not an exempt one — and `START_STICKY` brings the service back
within a minute or two. Observed in the field on 2026-08-20: a ride that
started at 13:22 metered 18 miles, the process was taken at 14:32
(`ApplicationExitInfo` reason `LOW_MEMORY`, importance
`FOREGROUND_SERVICE`, 106 MB RSS), and the service was back two minutes
later.

What came back was a fresh `RideMeter` at `READY`. The ride simply
stopped existing: metering ended silently, the row stayed frozen at its
last five-second snapshot with `endTime == startTime` and no end place,
and the watcher re-armed for a *departure* while the vehicle was parked
somewhere else — so the trip home was never metered either. Everything
needed to recover it was already in the database. Nothing looked.

**Why a marker rather than a status column.** `RideRecord` has no status,
and `endTime == startTime` is what a live row looks like rather than a
fact about one. A status column is the tidier answer and is
still not taken. The reason it wasn't no longer applies — adding one
used to mean wiping the rides it is meant to protect, and it is now an
ordinary schema change with a migration to write — so this is a choice
to revisit rather than a constraint. `LiveRideStore` keeps the
live ride's id in SharedPreferences instead — set before the row is
written, cleared at save and cancel, `commit()` rather than `apply()`
because its whole value is being on disk when the process is killed
without warning.

**Why paused, not running.** Between the death and the restart the
vehicle may have driven fifty miles or sat still, and nothing recorded
which. Resuming would quietly bill a gap the app knows nothing about.
Pausing states exactly what is known — the ride ran to its last persisted
moment — and a paused ride is already complete and saveable, which is the
whole reason there is no `FINISHED` state. The user resumes it or saves
it.

**Why the providers need a baseline.** `pause()` never resets the
providers, so an ordinary resume continues their running totals; that is
why the collectors can let a provider's total *replace* the ride's. A
restored ride has no such instances, so `priorMeters`/`priorSeconds`
hold what it did before and are added to whatever a resumed provider
counts from zero.

## Decision: Export Owns a File in Downloads, Not a Folder Grant

Export writes into the MediaStore Downloads collection from API 29,
rather than asking the user to grant a backup folder with
`ACTION_OPEN_DOCUMENT_TREE`.

The problem being solved is that the create-a-document picker cannot
overwrite. AOSP numbers around a name collision — seven exports of
`rides.json` leave `rides (7).json` — so repeat backups pile up copies
and no menu item the app can offer changes that, because the picker
never presents an overwrite at all.

A folder grant would fix it and was rejected on cost. It cannot default
to Downloads: since Android 11 SAF refuses tree access to the Download
directory outright, so the user would have to pick a subfolder,
confirm a system warning, and do it before their first export ever
worked. That is setup at install time for a personal app that had none.

The Downloads collection needs neither. From API 29 an app may insert
without permission, and may update and delete its own entries — and only
its own — with no consent dialog. So export replaces the file the last
export wrote, and cannot touch a file the user made themselves. Below
API 29 there is nothing to own, and export is simply the picker with its
numbered names; that is accepted rather than papered over with an
"overwrite a file" item that would sit in the menu on every device to
serve the versions that can't do the one-tap export, or with a
`WRITE_EXTERNAL_STORAGE` permission that would cost every user
something.

## Decision: Gross Amount Terminology *(superseded)*

Originally, the displayed calculation was to be called "gross" —
mileage amount plus time amount, explicitly not profit, income, or
earnings after expenses.

The word is no longer used anywhere in the app; the UI says "Amount" and
"Total", and the distinction is documented in `ui.md` instead. The
underlying point still stands: the figure is a calculation, not earnings.

## Decision: Free Application Model

The initial goal is a free application. It is licensed GPL-3.0-or-later.

Possible future options:

* Advertisement supported.
* Optional donation. **Started** — see below.
* Free/open-source distribution.
* A paid build with features the free one doesn't have.

No decision has been made yet beyond the license and donations.

### The database is still set aside before every upgrade

This started as the answer to destructive migrations and outlived them.
`SchemaRescue` runs on either side of Room's open: it copies the whole
database file somewhere safe *before* Room touches it, and — when Room
has rebuilt the tables — copies the rows back for every column the two
schemas still share.

The two halves are now gated differently, and that is the point.

**The copy is taken on every version change, migrated or not.** It costs
one file write on the launch after an upgrade, and it is the only thing
left to fall back on when a migration turns out to be wrong. A migration
that drops the wrong column is a quieter failure than no migration at
all, and it is the failure this now insures against.

**The carry-forward runs only when Room really did rebuild the tables** —
below the baseline, or a downgrade. `rebuiltFromScratch()` in
`Migrations.kt` is what decides, from the version read off the file
before Room opened it. Running it after a successful migration would
copy rows in on top of rows the migration had already moved: every row
duplicated, or — since the ids come across too — every insert failing on
the primary key and the user told the whole table was lost. `copyTable`
also refuses a destination that already has rows, because a guard on a
data path is worth having twice. An unreadable version is neither case
and is left alone; the set-aside copy stands as the backstop.

The carry-forward is deliberately not a migration. A column added takes its default,
a column dropped is left behind, and a column *renamed* reads as one of
each — the data lands in neither, which is the case a schema change has
to be checked against. The set-aside file is what makes this a trade and
not a loss: whatever the carry-forward misses is still sitting in a
complete copy of the old database, and the Advanced screen can share it off
the device.

Two details are load-bearing and were both wrong in the first version.
The insert is a plain `INSERT`, not `INSERT OR IGNORE` — `OR IGNORE`
suppresses constraint violations rather than raising them, so a column
added as `NOT NULL` with no default returns success for every row and
writes nothing, and the user is told ten rides were carried into an
empty table. And the count reported comes from `SELECT count(*)` on the
destination afterwards rather than from counting calls that didn't
throw, so the number shown is the number of rows actually there.

Tables are copied places, then rides, then stops, because stops carry a
foreign key to rides and a row whose parent isn't in yet is a row
rejected. A table that fails entirely doesn't take the others with it.

### The Advanced screen is a user-facing screen

The question the entry below left open — whether a screen shipped in
release builds should go back behind a hidden gesture once migrations
made it less load-bearing — is answered the other way. It stays visible,
and it is called **Advanced**.

**Why not hidden.** Look at what is actually on it: a backup taken before
an experiment, the database an upgrade set aside, and the log that says
why a ride started on its own. The audience for those is an interested
user, not only the developer, and every one of them is wanted on a
phone that has never had adb attached to it. A screen called "Debug menu"
tells that user the screen is not for them, which is the same problem
as hiding it, spelled differently.

**Why not inside Settings.** Settings is disabled while a ride is running
or paused, because `activeSettings` are captured at `start()`. The event
log is most wanted exactly then — a ride began on its own and the user
wants to know why. Beyond that, Settings holds values that shape a ride;
this holds actions on data. Merging them makes Settings a junk drawer.

**What the name costs.** A promise about who a screen is for is a promise
about how its buttons behave. Two acted on a single tap and now confirm
first: *Restore from backup*, which replaces everything and is disabled
mid-ride, and *Repair place links*, whose orphan sweep cannot tell a
deleted place's leftovers from a ride import whose places haven't been
imported yet. That warning existed only as a code comment before.

None of this makes it the export feature. That is still the JSON export.

### A restore lands at the next start, not at the tap

Restoring used to close Room's instance and copy the backup over the
live file. That is not enough, and the failures are the loud kind:
`AppRepository` goes on handing out DAOs built on the closed handle,
every screen goes on collecting Flows from it, and the marker naming the
live ride points at a row the new file has never had. What the user
sees is deletes that don't take and a rides list that has emptied, and a
toast saying "restart the app now" is not read by anyone in that state.

So the tap only *stages* the file, and the swap happens at the top of
`AppDatabase.build()` on the next launch, before Room opens anything —
the same order `SchemaRescue` works in, and for the same reason. The
restored file then meets the migrations exactly as an upgrade would.

`AppRestart.restart()` ends the process and asks the system to open the
app again, so the user doesn't have to know any of this. It is
deliberately not load-bearing: if the relaunch is refused, or the process
is killed first, or the app is next opened a week later, the restore
still lands exactly once, at the start of a process where nothing has
read the old file yet. A restart that must work is a restart that will
eventually not.

### A dismissed notification is logged, not re-posted

From Android 14 the ongoing notification of a foreground service can be
swiped away, and the service goes on running. `setOngoing(true)` no
longer prevents it.

While a ride is running this is invisible: the ride state changes every
second, every change calls `refreshNotification()`, and the notification
is back before the user's thumb has left the screen. While the app is
only *watching* for a departure, nothing changes — `autoState` sits on
`WatchingDeparture` and a `StateFlow` doesn't re-emit the same value — so
nothing re-posts it and it stays gone. The two cases look identical from
outside the phone, which is how this was found: a build where watching
was plainly still on in the app, with no notification anywhere.

It is left dismissed. Android made that gesture deliberate, and coming
back a minute later on the next sweep would be taking a decision back
from the person who made it. Nothing is broken by the notification being
gone — the service is still up, the app still shows the watch status, and
the system's own location indicator still appears when a fix is taken.

What was actually missing is the record. `NotificationDismissedReceiver`
is the notification's delete intent, and writes one line to the event log
naming the state the notification was showing when it went. It says
"dismissed manually" rather than naming the user, because what arrives
is a dismissal and nothing here can tell whose thumb it was. That is the
same reason the automatic-by-location events are logged at all: they
happen in a pocket, with no adb attached, and the log is the only witness.

It is a receiver rather than a service action on purpose. A dismissal is
not a tap on a notification action and does not carry the same permission
to start a service from the background; a broadcast to our own unexported
component always arrives.

### Stopped time is time at a stop, not time below a speed

`Settings.stoppedHourlyRate` sat in the model for a long time being
stored, round-tripped through DataStore, and applied to nothing — with no
editor in Settings either. Making it real needed a definition of
"stopped", and there were three candidates.

A taxi meter would use **speed**: seconds under some threshold bill as
waiting time. It was rejected for two reasons. It bills against something
the user never sees — nothing in this app displays "time below 2 mph" —
so an amount could not be explained after the fact, which is the whole
point of storing the rates on the ride. And `minimumSpeedMps` defaults to
0, so it would have done nothing at all until someone set a threshold
they'd never been asked for.

**Time inside any dwell** was rejected for the first of those reasons on
its own: a dwell that never became a stop is recorded nowhere.

So stopped time is the time at this ride's **stops** — the rows already
detected, already persisted, and already shown to the rider in the
`A → B → C` summary. A charge can be pointed at. A two-minute red light
is not a stop and bills as driving, which is a defensible line to draw
and an easy one to explain.

**It is recomputed, never accumulated.** A stop's end time is corrected
when the vehicle drives off, and `save()` can retract a trailing stop
entirely, so a running total would be a total of things that stopped
being true. `RideMeter.stoppedSecondsNow()` sums the rows plus the dwell
under way, on every one-second tick — the same argument
`PlaceLocationRecalculator` makes for recomputing an average from
scratch.

**A pause is not stopped time.** `pause()` banks the total and `resume()`
shifts `dwellAnchorTime` forward by the length of the pause, so the dwell
clock measures ride time rather than wall time. Without that, pausing for
lunch inside the dwell radius came back as an hour of stopped time the
ride's own clock had never counted — and since the total is clamped to
the elapsed time, a long enough lunch would have billed the entire ride
at the stopped rate.

**Both numbers are stored on the ride.** `stoppedSeconds` and the
`stoppedHourlyRate` in force join `perMeterRate`, `hourlyRate`,
`baseAmount` and `minimumAmount` on the row, because a saved ride has to
explain its own amount without reference to what Settings say today. The
rate is nullable so a ride from before this reads `-` instead of claiming
a rate of zero was in force; its `stoppedSeconds` is 0, so its stored
amount is exactly what it always was.

### Backup is off, and export is the replacement

`android:allowBackup="false"`, with both rules files excluding everything
for cloud backup and for device-to-device transfer.

The app holds no `INTERNET` permission, which is the strongest privacy
claim it can make: the process cannot open a socket, whatever its code
intends. Auto Backup would have gone straight around that. The system —
not the app — would copy `rides.db`, every ride with its coordinates,
into the user's device backup, which on a phone with Play services means
their Google account. The app wouldn't be doing it and couldn't see the
result, and the driving history would leave the device anyway. A promise
with a hole that size in it isn't one.

This was found writing `PRIVACY.md` from the source rather than from
memory, which is the argument for writing it that way: the manifest still
had the generated `allowBackup="true"` and two rules files whose every
rule was commented out. The README was claiming everything stays on the
device at the time.

**The cost is real.** A new phone doesn't restore rides by itself any
more. What replaces it is the JSON export and the Advanced screen's
database backup, both of which the user aims somewhere deliberately —
and both of which say so in the README and the privacy policy, because a
backup people think they have and don't is worse than none.

The excludes are kept even though `allowBackup="false"` makes them moot.
They cost nothing, they document the intent, and they mean a later
decision to turn the flag back on has to be a decision about *what* to
back up rather than an accident that ships coordinates.

### The debug menu is visible while it is load-bearing *(superseded)*

It used to be gated behind `BuildConfig.DEBUG` and a long-press on
"About". It is now an ordinary drawer entry that ships in release
builds, for exactly as long as the reason holds: while upgrades work by
setting the database aside, that file is the only complete copy of the
rides that couldn't be carried forward, and a safety net reachable only
by a gesture nobody has been told about is not a safety net. The raw
`.db` backup and restore are there for the same reason.

None of this makes it a user feature. The event log, the orphan sweep
and the raw file restore are all still debug tools with debug wording.

Real migrations have since landed, and with them most of that reason:
upgrades no longer drop the tables, so the set-aside file is insurance
against a wrong migration rather than the only copy of rides that
couldn't be carried forward. That reopened the question and it was
settled the other way — see "The Advanced screen is a user-facing screen"
above. The screen stays; what changed is that it stopped calling itself a
debug menu and started behaving like something a user is allowed to
touch.

### Donations are a section on About, not a prompt

The ways to donate live at the bottom of the About screen and nowhere
else: PayPal, Venmo, an on-chain Bitcoin address, and a Lightning
address. Nothing in the app asks for money while it is being used, and
nothing counts rides to decide when to ask. A user who wants to give
something goes looking for it, and About is where they look.

Two payment services and two crypto rails rather than one of each,
because they cost nothing to carry: the whole feature is four strings in
`util/Donations.kt` and a list built from whichever are non-blank. A
method that isn't configured doesn't exist as far as the UI is concerned,
so the section can ship dark and light up when an address is pasted in.

Nothing is embedded but the destination itself. A tap opens the handle or
address in whatever app claims it, and there is no SDK, no payment
processing, and no network call from this app — which is what keeps
"there is no network access" true on the README. The QR codes are
generated on the device from the same strings, so that stays true of
them too; ZXing core is an encoder, and nothing in it reaches the
network.

### Every donation method also gets a QR code

Tapping a row only helps when the app that takes the money is on *this*
phone, and copying only helps when the string can be carried to a phone
where it is. Neither covers the case the feature actually exists for: a
passenger in the back seat, holding their own phone, with nothing
crossing between the two devices but what their camera can see. So each
row has a QR button as well.

The code is drawn as rectangles on a Compose Canvas rather than
rasterised to a bitmap, so it is sharp at whatever size the dialog gives
it, and it is dark-on-white in both themes — a scanner can be told which
polarity to expect, but a camera pointed at a screen is guessing, and
light-on-dark is the guess it gets wrong.

The crypto payloads are uppercased before encoding and the payment links
are not, which looks inconsistent and isn't. QR alphanumeric mode holds
uppercase A–Z and digits at well under the cost of byte mode; bech32
addresses and URI schemes are both case-insensitive, so uppercasing them
is free and takes the Lightning offer from 77 modules square to 65. An
http URL's path is case-sensitive, so `PAYPAL.ME/RIDEMETER` is not a
page, and those two stay as they are — short enough that byte mode costs
nothing that matters.

One thing to check before publishing on Google Play: its payments policy
governs how apps may take money, and donation links out of a free app
have caused trouble for other projects. This has no bearing on F-Droid or
a direct APK.

### A paid build stays possible, and what keeps it that way

The GPL is a grant to *other people*; it does not bind the copyright
holder. As sole author, Rick Hallock can ship this code as GPL-3.0 to the
public and under proprietary terms to paying customers — the same
dual-licensing arrangement Qt and MySQL use. Selling the GPL build itself
is separately allowed, but every buyer would get the source and the right
to redistribute it, so that is a convenience fee rather than exclusivity.

Three things have to stay true for the closed-build option to survive,
and two of them are decisions to make *before* they bind:

* **Contributions.** A merged patch belongs to whoever wrote it, licensed
  to this project under the GPL only. Relicensing it into a proprietary
  build then needs that person's permission. If a paid build matters, a
  CLA or copyright assignment has to be in place before the first outside
  patch is accepted — retrofitting one means finding every contributor
  again.
* **Dependencies.** Currently clean: everything in `app/build.gradle.kts`
  is Apache-2.0 (AndroidX, Compose, Room, kotlinx.serialization) except
  the proprietary Play services client, and none of it imposes terms on a
  proprietary build. Adding a GPL library would end this.
* **What has already shipped.** Any released version stays GPL forever
  and cannot be retracted; only future versions can carry different
  terms. Nothing has shipped yet (`0.1.0` is the first), which is the cheapest
  possible moment to decide.

This is reasoning, not legal advice. Get a lawyer's read before actually
selling anything.

## Decision: A Linking Exception for Google Play Services

`GpsDistanceProvider` uses the fused location provider, which makes
`com.google.android.gms` the app's one proprietary dependency (see
"LocationManager, Not Geofencing" for why it is the only one). Combining GPL code with proprietary libraries is a
long-standing gray area.

It is not a problem for the copyright holder, who cannot infringe his own
copyright — but it would be inherited by anyone redistributing the app,
which is precisely the freedom the GPL is chosen for. `LICENSE-EXCEPTION.txt`
therefore states the permission outright, as an additional permission
under GPL v3 section 7, and every source file's header points at it.

The exception is deliberately narrow: it covers combining with the Play
services client libraries and nothing else, and grants no rights in those
libraries themselves.
