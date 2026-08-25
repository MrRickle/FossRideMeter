# FossRideMeter UI and Terminology

Compose + Material 3, a single Activity. `AppNavigation` owns the
`NavHost`, the navigation drawer, and the runtime permission prompts.
Screens are decomposed into small composables — keep that granularity
rather than growing a screen file.

Both `RideScreen` and `SettingsScreen` have separate portrait and
landscape layouts.

The person using the app is the **user**, everywhere: on screen, in the
event log, in code comments, and in these documents. It used to be "the
driver" in about half of them, which was a guess about who was holding
the phone — a ride can be metered by someone who isn't driving, and the
word had to be translated back to "user" every time anyone read it. The
vehicle is still the vehicle, and driving is still driving; it is only
the person who changed name.

## Screens

| Route | Screen | Purpose |
| --- | --- | --- |
| `ride` | `RideScreen` | The driving screen: live meter and controls |
| `rides` | `RidesScreen` | Saved ride history, one wide scrolling table |
| `places` | `PlacesScreen` | Saved places, editable |
| `settings` | `SettingsScreen` | Rates (including the stopped hourly rate), units, provider, stop detection, sounds |

The Sounds section lists the five ride actions, each naming the sound it
currently makes and opening the system sound picker when tapped. "Built-in
tone" is the default — a platform tone, not a file — and a **Built-in**
button returns an action to it. The rows are hidden while the tones are
switched off.
| `about` | `AboutScreen` | Version, git stamp, build time, donations |
| `license` | `LicenseScreen` | Full GPL-3.0 text |
| `advanced` | `AdvancedScreen` | Raw `.db` backup/restore, databases set aside by an upgrade, event log, place-link repair |

The **Advanced** screen is a normal drawer entry and ships in release
builds. It is called Advanced rather than Debug because that is what it
is: a backup before an experiment, the database an upgrade set aside, and
the log that says why a ride started on its own are all things a user
may need on a phone that has never had adb attached to it. It is still
not the user-facing export feature — that is the JSON export.

It stays a drawer destination rather than a row inside Settings because
Settings is disabled while a ride is running or paused, and the event log
is wanted exactly then.

Two of its buttons ask before acting.

**Restore from backup** names the backup's date, says what is on the
phone now is not kept, and says the app will close. It then closes the
app and opens it again — the restore itself is applied at that next
start, before anything reads the database, so a restart that doesn't
happen leaves the restore waiting rather than half-applied. It is
disabled while a ride is running or paused: it replaces the database that
ride is being written into.

**Repair place links** says what it re-links and warns not to run it with
an unfinished import — a ride imported before its places looks exactly
like a broken link, and would be re-linked to the wrong place.

Import and Export live in the TopAppBar overflow menu on the Rides and
Places screens. Import always goes through the Storage Access Framework;
Export does only where it has to.

**Export** is one tap: from API 29 it writes `rides.json` / `places.json`
straight into Downloads, with no picker and no permission, replacing
whatever the last export of that name wrote. That replacement is possible
because the file belongs to the app — from API 29 an app may update and
delete its own entries in the Downloads collection, and only its own — so
a file the user created themselves is invisible to it and stays
untouched.

**Export to...** is the system create-a-document picker, for putting a
copy on an SD card or in a cloud folder. It only appears where Export
doesn't already open a picker.

Below API 29 there is no Downloads collection to own, so Export *is* the
picker. AOSP resolves a name collision there by numbering — ask for
`rides.json` seven times and you get `rides (7).json` — and offers no
overwrite anywhere in that flow. That is accepted rather than worked
around: an "overwrite a file" item would sit in the menu on every device
to serve the versions that can't do the one-tap export. There is
deliberately no `WRITE_EXTERNAL_STORAGE` fallback either; a storage
permission in the manifest costs every user something.

Every export says what it did — "Saved to Downloads/rides.json",
"Replaced Downloads/rides.json", or on the picker path the name that was
actually written, which is how a numbered `places (1).json` becomes
visible instead of being mistaken for the backup it didn't replace.

The pickers open in Downloads via `EXTRA_INITIAL_URI`. That is a starting
location, not a grant, which is why it may name Downloads at all — a tree
request may not: since Android 11 SAF refuses to grant tree access to the
Download directory, so "pick your backup folder once" could never have
defaulted there.

Import says what it imported — "Imported 12 rides", or "That file had no
places in it" when the file parsed but held nothing. The empty case is
the one worth reporting: picking the wrong file otherwise looks exactly
like picking the right one. The message waits for the rows to actually be
written, not just parsed.

## AboutScreen

Version, git stamp, build time, copyright, and — when any is configured —
a **Support development** section listing the ways to donate: PayPal,
Venmo, an on-chain Bitcoin address, and a Lightning address.

Every handle and address lives in `util/Donations.kt` and nowhere else. A
blank one is dropped rather than shown, and with all of them blank the
section renders nothing at all, divider included — a build with no
addresses filled in never advertises a section that can't do anything.

Each row does the same three things. Tapping it hands the value to
whatever app claims the link: a browser for PayPal and Venmo, a wallet
for the `bitcoin:` and `lightning:` schemes, falling back to the
clipboard when no app handles it. The copy button copies the same
string, for a wallet on another device or for anyone who would rather
paste an address than follow a link. The QR button opens a dialog with
the address as a scannable code, for the passenger holding their own
phone. Addresses are shown monospaced and in full, never abbreviated,
because they are meant to be read back against the wallet they came
from.

The QR code is drawn as squares on a Canvas (`ui/QrCode.kt`), always
dark on white in both themes and with the four-module quiet zone the
spec requires — a camera pointed at another phone's screen is guessing
at polarity, and light-on-dark is the guess it gets wrong. The two
crypto payloads are uppercased so they encode in QR alphanumeric mode:
bech32 and URI schemes are both case-insensitive, and it takes the
Lightning offer from 77 modules square to 65. The two payment links
can't be — an http path is case-sensitive — and stay as bytes.

The app asks for nothing anywhere else. Donations exist on this screen
only.

## RideScreen

The live screen shows, from the top: ride status, the live meter values,
GPS status, the running total, and the control buttons.

`RideDataTable` shows a value/amount pair per row:

```
12.73 mi                $10.18
00:21:41                 $5.42
```

Base Amount and Minimum Amount rows appear only when those settings are
non-zero.

`RideInfoSection` is one composable taking a `RideInfo`; a live `Ride` and
a saved `RideRecord` each map into that shape first (see `ui/RideInfo.kt`).
Live, it shows only rows that already have a real value, so the section
grows as the ride progresses instead of displaying a wall of zeroes and
dashes. For a saved ride it shows every field: Amount, Distance, Start
Place, Stops, End Place, Duration, Stopped, Start/End Time, Rate, Per
Hour, Per Hour Stopped, Base, Minimum, Original Amount, Start/End
Location, GPS Provider.

**Stopped** and **Per Hour Stopped** appear only when there is something
to say: no stopped time means no Stopped row, and a ride recorded before
the stopped rate existed has no rate to show.

Tapping the Stops row opens `StopsDialog`.

### What is tappable

A row of label-and-value reads as a printout, so the ones that lead
somewhere are coloured `MaterialTheme.colorScheme.primary`
(`ui/Tappable.kt`) and the ones that only report a number are not. It
covers the ride's Ride Name, Amount, Start Place, Stops and End Place
rows, a stop's place name in `StopsDialog`, the sound rows in Settings,
and the donation methods on About.

`InfoRow` takes an `onClick` rather than a pre-built clickable modifier,
so a row cannot be made to lead somewhere without also being coloured as
though it does. A row whose target doesn't resolve — a place id with no
place — is left uncoloured, which is the same signal in reverse: it is
inert, and it looks it.

Whole rows in a list (Rides, Places) are not coloured. A list row is
expected to be tappable; a detail row is not.

### Buttons by status

| Status | Main control row | Secondary row |
| --- | --- | --- |
| `READY` | START | — |
| `RUNNING` | PAUSE, ADD STOP | SAVE, CANCEL |
| `PAUSED` | RESUME, ADD STOP | SAVE, CANCEL |

ADD STOP shares the row with the main control, in the space left beside
it — narrow, and reading on two lines — so it costs no vertical space on a
screen whose buttons must stay above the fold. It records a stop where the
vehicle is now and asks nothing first: it ends nothing and discards
nothing. It is disabled until a GPS fix exists.

There are only three states, and a ride in either live state is complete
enough to save at any moment — which is why both offer the same pair.

SAVE ends the ride and clears the live window. It asks for confirmation
first, because it is the point of no return: there is no finished-but-not-
saved state to back out from, and the end place is resolved (creating a new
place if the ride ended somewhere unknown) on confirmation. CANCEL deletes
the ride outright and also asks first.

Editing a ride after the fact is done from the Rides screen, not the ride
screen.

## Watch status on the live screen

Above *Ride Information* — and above the fill-in-ahead fields while
`READY` — the live screen shows what the automatic-by-location watcher is
doing and where it thinks the vehicle is:

```
Watching for departure
44.97213, -93.26384  ±13m  gps  9zvxdt8k
inside Home
```

The first line is what the watcher is armed for:

| Line | Meaning |
|---|---|
| `Watching for departure` | Ready; leaving an auto-start place will begin a ride |
| `Running - not left X yet` | Metering, but still inside auto-save place X — no arrival can fire until the vehicle leaves and comes back |
| `Watching for arrival` | Metering and outside every auto-save place; reaching one will end the ride |
| `Paused - will resume on leaving X` | The ride is paused inside auto-start place X; driving off resumes it rather than starting a new ride |
| `Auto paused - resume to re-arm` | Paused with only auto-save places flagged — there is no departure to watch for, so nothing happens until you resume by hand |
| `Auto off - no place is flagged` | Nothing is being watched for at all |

The **provider** is named because it explains the accuracy. A sweep uses
the cheapest provider available — often cell/wifi positioning, good for
hundreds of metres — while a running ride uses GPS at 1 Hz. Seeing ±280 m
from `network` and ±8 m from `gps` is the design working, not a fault.
`±?` means the fix reported no accuracy at all (the simulated provider).

### One order, wrapped

The live screen reads in a single order however the phone is held:

1. **Total**
2. **Buttons**
3. **Meter details** — the GPS line and the data table
4. **Watch status**
5. **Ride Information**

Portrait is that list straight down. Landscape wraps it into two columns,
breaking after the buttons: total and buttons on the left, everything from
the meter details onward on the right. Nothing is reordered by the wrap —
the eye follows the same sequence either way.

The controls sit second, directly under the total, because that is what
keeps them on screen. The meter's column is a fixed 330 dp and does not
scroll, so anything drawn above the buttons pushes them toward the bottom
edge: three lines of watch status did it, and the data table does it again
on its own whenever a base or minimum amount is set and its two extra rows
appear. With the buttons above all of that, nothing downstream can reach
them, and the right-hand column scrolls to absorb whatever grows.

On a screen too small for an info section at all, the whole list falls
back into the meter's column: cramped beats invisible.

This is separate from the GPS line under the meter on purpose. That one reports
the **ride's** `DistanceProvider`, which doesn't exist until a ride
starts — so it reads "GPS Off" for the whole time watching is the only
thing running, and looks exactly like nothing happening.

The third line is the one that matters when auto-start doesn't fire: it
says whether the current fix falls inside a watched place. Standing where
you park and seeing *"not inside any watched place"* means that place's
location or radius is the problem, not the watcher.

The section is hidden entirely when nothing is being watched and no fix
has been taken.

## Ride Information

One section, **identical on the live screen and on a saved ride**. Both
map their own form of ride into `RideInfo` first, so there is nothing to
keep in step by hand.

There is no edit mode and no Edit button. A ride has exactly two things
that can be changed after the fact, and they are edited by tapping their
rows — the same gesture that opens a place:

| Row | Tapping it |
|---|---|
| **Ride Name** | one-field editor |
| **Amount** | one-field editor; blank clears the override and returns to the metered figure |
| **Start Place** / **End Place** | the place editor |
| **Stops** | the stops list, whose entries open places too, and whose coordinates make new ones |

Every other row is measured and inert.

```
Rides → tap a ride → Ride Information
                       ├─ tap Ride Name / Amount → edit that field
                       ├─ tap a place → place editor
                       ├─ tap Stops → stops list → place editor / new place
                       ├─ Delete
                       └─ Close

Places → tap a place → Edit Place
                       ├─ Delete
                       ├─ Cancel
                       └─ Save
```

Edits are written as they are confirmed, onto the row as it stands at that
moment — the dialog is handed a freshly looked-up record each time it
draws, so nothing is captured long enough to go stale.

## Reaching a place from a ride

Place names are links to the place editor, so a place can be fixed at the
moment you notice it's wrong rather than being looked up again on the
Places screen. This works from a saved ride and from the ride in progress
alike:

```
Rides → tap a ride → Start Place / End Place → place editor
Rides → tap a ride → Stops → tap a stop's name → place editor
Rides → tap a ride → Stops → tap a stop's coordinates → new place there

RideScreen (live) → Start Place / End Place → place editor
RideScreen (live) → Stops → tap a stop's name → place editor
RideScreen (live) → Stops → tap a stop's coordinates → new place there
```

Naming the place you are sitting in, mid-ride, is the case this most
exists for.

Tapping coordinates opens the same editor on a place that doesn't exist
yet, already sitting on that stop's own point, with an 80 ft radius to
start from. Saving it writes the place and pulls that stop onto it. This
is how a place inside a place gets drawn: the store is Home Depot, and the
corner of it you keep stopping in becomes Home Depot Windows.

Dialogs stack, so the place editor appears above whichever of them opened
it, and closing it returns there.

Saving from that editor is the *same* path the Places screen uses, so
`PlaceBoundaryEnforcer` runs and every ride affected by a changed radius
or location is re-resolved — not just the ride you happened to open it
from.

Because of that, saving the ride afterwards writes back only the two
fields the ride editor owns (name and amount), applied to the row as it
then stands. A place edit can legitimately change the ride's start or end
place underneath the open dialog, and writing the whole captured record
back would silently undo it.

The view holds the ride's **id**, not a copy of the row, and looks it up
again as it draws — so a place edit that re-resolves this ride is
reflected immediately, and a ride deleted from elsewhere closes its dialog
instead of lingering as a stale copy.

## The live screen below the meter

Everything above **Ride Details** — the running amount, the GPS status,
the controls — is unchanged. Below it, the space holds one of two things
depending on whether a ride exists yet. (On very short screens it holds
neither, as before.)

### READY — filling in ahead of a ride

Just two fields: **Ride Name** and **Override Amount**. No detail, because
a ride that hasn't started has no distance, no places and no times — it
would be a column of dashes.

Neither field is attached to anything yet. Whatever is in them when START
is pressed is carried onto the ride that begins — a name for the run
about to happen, or a fare agreed at the kerb before setting off. This
works for an automatic start too, since that goes through the same
`RideMeter.start()`.

Saving or cancelling a ride clears them again, ready for the next one.

### RUNNING / PAUSED — the ride's detail

The same rows a saved ride shows, in the same order, with the same place
links and the same stops dialog. The ride's **name is the first row**,
read-only: a saved ride carries its name in the view dialog's title, and
the live screen has no such title.

The two fields are gone by this point, but the ride's name and amount are
still editable — by tapping their rows in the information itself, exactly
as on a saved ride. A fare agreed on arrival can be entered before saving,
without going to the Rides screen for it.

Live settings are safe to show as the ride's rates because Settings is
locked for the whole of any live ride (`RUNNING || PAUSED`), so the rates
on screen are necessarily the ones being metered.

## Selecting rows

The Rides and Places tables both support multi-select, using the standard
Android contextual pattern:

* **Long-press** any row to start selecting, and it selects that row.
* While selecting, a **plain tap** toggles a row instead of opening its
  editor — so a whole run of rows can be picked without dialogs appearing.
* The top bar is **replaced** (not added to) by a contextual bar showing
  `N selected`, with **All**, **export**, **delete**, and an **X** to
  leave.
* **Back** leaves selection mode, same as the X. Navigating away leaves it
  too.

Deselecting the last row leaves the mode — an empty selection *is* "not
selecting", so there is no separate state to get out of step.

Delete asks for confirmation and says what else it takes with it: deleting
rides also deletes their stops and re-averages the places those points fed;
deleting places leaves the rides that used them intact — each ride point
and stop that pointed at a deleted place is re-resolved from the location
it recorded, so those rows read as a fresh geohash placeholder (or as
another place they happen to fall within) rather than losing their label.

Export writes the same JSON as the overflow-menu Export, restricted to the
selection, to `rides-selected.json` / `places-selected.json`. The selection
stays active afterwards rather than clearing — the payload is built when
the file is written, not when the picker opens, so the rows have to still
be selected at that point.

## Terminology

### Distance

Total distance traveled during the ride. Stored in **meters**; displayed
in miles (US) or kilometers (SI) according to
`Settings.measurementSystem`. GPS accuracy is shown in feet (US) or meters
(SI).

```
Distance        12.73 mi
```

### Time / Duration

Elapsed ride time, stored in seconds and displayed as `HH:MM:SS`. It
starts when the ride starts, pauses with the ride, and stops when the ride
is finished.

```
Duration        00:21:41
```

### Stopped

How much of that duration was spent at this ride's stops, in the same
`HH:MM:SS` form. A dwell counts once it has lasted the stop-detection
time or the user has pressed ADD STOP; waiting at a light does not.
Time while the ride is paused is not stopped time — it isn't ride time at
all.

```
Stopped         00:06:12
```

### Amount

The calculated value for the ride:

```
movingSeconds  = elapsedSeconds - stoppedSeconds
distanceAmount = meters × perMeterRate
timeAmount     = movingSeconds  / 3600 × hourlyRate
               + stoppedSeconds / 3600 × stoppedHourlyRate
subtotal       = baseAmount + distanceAmount + timeAmount
Amount         = max(subtotal, minimumAmount)
```

Time at a stop bills at its own rate, so waiting at a door can be charged
differently from driving. Set both to the same figure and the split makes
no difference — which is what a ride recorded before the stopped rate
existed does, since its `stoppedSeconds` is zero.

Rates are stored per meter internally and entered by the user in the
displayed unit (per mile or per kilometer).

An Amount is a calculation only. It does not represent profit, earnings,
or take-home income after expenses, taxes, or fees.

### Original Amount vs Amount

A ride can carry a **manual amount** typed by the user, which overrides the
calculated value for display. Both are kept on the row, so the original
calculation is never lost:

* **Amount** — `manualAmount ?: calculatedAmount`, what is displayed.
* **Original Amount** — `calculatedAmount`, what the rates produced.

### Base Amount / Minimum Amount

**Base** is a flat amount added before distance and time. **Minimum** is a
floor the total is raised to. Both are zero by default and are hidden from
the live table when zero.

### Place

A saved location a ride can start at, end at, or stop within. A place is
auto-created the first time a point doesn't fall inside any existing
place, initially named after its geohash until the user renames it.
Matching is purely `distance <= place.radiusMeters`; where several places
cover the same point, the named one wins, then the tighter radius.

Saving a place re-resolves every ride point and stop inside it, whatever
place they were attached to before. So widening "Home Depot" to 400 ft,
enough to cover the store and its lot, relabels the stops already recorded
inside — they don't stay as the geohashes they were first filed under. Any
unnamed placeholder left holding nothing afterwards is deleted; named
places are never deleted this way, so a shop inside a mall stays its own
place.

It works downward as well, which is what makes places inside places
usable: drawing "Home Depot Windows" at 80 ft *inside* Home Depot takes
back the stops it covers, because the tighter radius wins.

The Places table columns are: Name, Location, Radius, Named, Locked, Auto.

A place can be deleted two ways, the same two a ride can: from the editor
opened by tapping its row, and through multi-select (see *Selecting
rows*). The editor's **Delete** sits left of Cancel, tinted with the
error colour, and asks for confirmation first — it says the rides and
stops here keep their locations and come back as an unnamed place, and
warns separately when the place being deleted is one that starts or saves
rides automatically.

Delete shows up only where the place is a place: the same editor opens
from a stop and from the live ride, and there it is being used to *name*
a spot rather than manage one, so the button isn't drawn. A place the
stops list has only proposed has nothing to delete yet either.

* **Named** — the user has given it a real name (display only).
* **Locked** — the user has typed coordinates directly, so the location is
  frozen and no longer averaged from linked rides and stops.
* **Auto** — `autoStart` starts a ride when the vehicle *leaves* this
  place; `autoSave` ends one when it *arrives*. Both are edited in the
  place editor, and flagging any place is what turns watching on — there
  is no separate switch for the feature.

### Automatic rides

A place can start and end rides by itself. Leaving a place flagged
**auto-start** begins a ride — or, if a ride is already paused, resumes
it, because driving off from a pause is carrying on rather than starting
over. Arriving at one flagged **auto-save** pauses the running ride and
starts a countdown, and the ride is saved when it runs out.

While this is active the ongoing notification is the whole of the UI, and
says which of them is happening:

| Notification | Meaning |
|---|---|
| `Watching for departure` | Ready; leaving an auto-start place will begin a ride |
| `Running - watching for arrival` | Metering; arriving at an auto-save place will end it |
| `Arrived at X - saving in 4:37` | Paused by an arrival, counting down, with **Resume** and **Save now** buttons |
| `Paused - will resume when you drive off` | Paused inside an auto-start place; leaving it resumes the ride |
| `Paused - nothing automatic until you resume` | Paused with only auto-save places flagged; watching is stood down |

Only the countdown carries buttons — it is the one moment the app is about
to do something irreversible on its own, so both answers to it are
reachable without opening the app. Resuming takes the ride back and it
keeps metering.

**Quit** closes the UI. It stops the service only when there is nothing
left to keep alive — no place flagged *and* no ride in progress.

* Any place flagged: the app closes and the notification stays, now
  reading `Watching for departure`. Swiping that notification away is
  allowed from Android 14 on, and watching carries on without it — it
  reappears at the next change of state, and the event log records that
  it was dismissed.
* A ride `RUNNING` or `PAUSED`: the service stays up and the ride is
  untouched, because ending a ride is **Save** or **Cancel** and nobody
  else gets to decide. Quitting mid-ride used to take the meter down with
  the service, leaving a row nothing would ever finish.
* Neither: Quit stops everything, as it always did.

The confirmation dialog says which of those is about to happen rather than
claiming the first one always does.

There is deliberately no second "quit everything" exit — that is the
master switch again, entered through a different door, and it fails the
same way: a state entered the night before, invisible the morning it
matters. **How to stop watching**, in the order the app intends:

1. **Clear the flags.** Turn off auto-start and auto-save on every place.
   This is the app's own off switch, and the only one that is visible
   inside the app afterwards.
2. **Force stop** FossRideMeter in Android's app settings, to hold it
   without touching the places. This is durable, not just until the next
   reboot: a force-stopped package is in Android's *stopped* state, so it
   receives no `BOOT_COMPLETED`, `BootReceiver` never runs, and the
   pending sweep alarm is cancelled with the process. Opening the app
   re-arms everything.
3. **Digital Wellbeing → Pause app** does the same by suspending the
   package, if that is easier to find.

**Disabling** the app is generally *not* available: Android only offers
Disable for preinstalled system apps, so a sideloaded FossRideMeter shows
Uninstall instead. Revoking its location permission is a fourth route, but
a poor one — watching stays armed, keeps its notification, and silently
never fires, which is exactly the failure the watch status line exists to
make visible.

Settings → **Automatic** has the tunables: how often to check for a
departure (default 60 s), how long the grace window lasts (default 5 min),
and **Location Check Accuracy**:

* **GPS every check** (default) — one real fix per check. A departure is
  noticed on the check it happens.
* **Cheapest first** — asks wifi/cell positioning first and only spends
  GPS when that can't answer. Much cheaper while parked, but a coarse fix
  often can't resolve a place at all, so departures are noticed late.

### Stop

A place visited *during* a ride — the clinic, the store — as opposed to
where the ride started or ended. A stop is recorded when the vehicle stays
within 30 m for at least `Settings.stopDetectionMinutes` and then moves
again — written to the database at that moment, so the stop list is real
from the first stop onward. A trailing stop at the same place the ride
ended is retracted at save rather than counted twice.

ADD STOP on the live screen records one on the user's say-so instead of
waiting for the dwell to be long enough and for movement to resume. It
finalizes the dwell already under way — the same anchor, the same start
time detection would have used — so a stop added by hand is the same row
as a detected one, and the first-stop-at-the-start-place suppression does
not apply to it.

The Stops row summarizes the ride as a path:

```
Start Place → Stop → Stop → End Place
```

Consecutive stops at the same place are one entry in that path, and one
entry in the stops list — a visit to a big place is often several dwells
(park, sit, move, sit again) and repeating its name three times says
nothing. The stops themselves are never merged: the list shows the dwells
behind a grouped visit, each with its own times and coordinates, and a
dwell claimed by a tighter place separates back out on its own. A grouped
visit's duration is the span from the first dwell's start to the last
one's end, so it includes the time spent moving between them.

Leaving a place and coming back later is two visits and reads as two.

A leg whose place never resolved to a name renders as `?`. That means the
point itself was never recorded — a place deleted out from under a stop is
re-resolved instead of blanked (see *Selecting rows*).

### Total

The final calculated amount for the ride, before any expenses, taxes,
fees, or other adjustments.
