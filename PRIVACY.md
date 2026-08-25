# Privacy

FossRideMeter records where you drove. That is the whole point of it, and
it is also the most sensitive thing a phone can know about you, so this
document says exactly what happens to it.

Short version: **the app has no network permission.** It cannot send your
rides anywhere, because it cannot open a connection at all. Everything
below is detail on that, plus the three ways data can leave the device
when you or the operating system move it.

This document describes version 0.1.1 and is written from the source. If
it disagrees with the code, the code is what runs — please open an issue.

## The app does not have internet access

`AndroidManifest.xml` does not declare `android.permission.INTERNET`, and
neither does any library merged into it. You can check the built APK:

```
aapt2 dump permissions app-release.apk    # or: aapt dump permissions
```

Without that permission, Android refuses every socket the process tries
to open. This is not a promise about intent; it is a promise the
operating system enforces.

There is no account, no sign-in, no analytics, no crash reporting, no
advertising identifier, and no telemetry of any kind.

## What is stored, and where

All of it is in the app's private storage unless marked otherwise. Other
apps cannot read it.

| What | Where | Contains |
|---|---|---|
| Rides, places, stops | `rides.db` | Start and end coordinates, stop coordinates, times, distances, amounts, and the names you give places |
| Settings | DataStore preferences | Rates, units, detection thresholds |
| The live-ride marker | SharedPreferences | The id of a ride in progress, so it can be recovered if the app is killed |
| Event log | `files/events.log` (and one rotated `.log.1`) | Automatic start/save events, **including coordinates**. Readable and clearable from the Advanced screen |
| Databases set aside by an upgrade | `files/pre-upgrade/*.db` | A complete copy of the database as it was before a schema change. Newest five kept |
| A staged restore | `files/pending-restore.db` | Whatever backup you asked to restore, until the next launch applies it |
| Manual backup | `Android/data/org.fossridemeter.app/files/rides_backup.db` | A complete copy of the database, made when you tap Backup on the Advanced screen. App-specific external storage |

Uninstalling the app deletes all of it, including the manual backup.

Files **you** exported — the JSON from Export, or a `.db` you shared out —
are yours and stay wherever you put them.

## Where location comes from

Two different providers, depending on the setting:

* **Metering a ride** uses Google Play services' fused location provider
  (`play-services-location`). The app asks it for fixes; what Google Play
  services itself does on your device is governed by Google's terms, not
  by this app. This is the only Google dependency in the app, and it is
  why an F-Droid build would need a variant that uses the platform
  provider instead.
* **Watching for departures and arrivals** at automatic places uses the
  platform `LocationManager` directly — no Play services, no geofencing
  API. It takes one fix per sweep, at the interval you set.

The GPS simulator is a third option in Settings. It uses no location at
all; it plays back a scripted route so the app can be tried without
driving anywhere.

## The two ways data can leave the device

Both of them are you moving a file. The app never initiates either.

1. **Export.** Rides and places export as JSON through the system file
   picker. You choose the destination. If you choose a cloud folder, the
   file goes to that cloud — the app only writes to the location you
   picked.
2. **Share.** The Advanced screen can share a `.db` backup, or a database
   an upgrade set aside, through the system share sheet. It goes to
   whatever app you pick.

## Android's backup service is turned off

`android:allowBackup="false"`.

Left on, Android's own backup service would copy the app's data —
including `rides.db`, every ride with its coordinates — into your device
backup, which on a phone with Google Play services means your Google
account. The app would not be doing the copying and could not see the
result, but your driving history would leave the device all the same. The
app holds no `INTERNET` permission precisely so that cannot happen;
handing the data to the system to upload instead would be the same
outcome by another route.

The rules files exclude everything for both cloud backup and
device-to-device transfer as well, so that turning the flag back on
cannot quietly start shipping ride history.

The cost is real and worth stating: **a new phone does not get your rides
back by itself.** Use Export, or the database backup on the Advanced
screen, before you switch devices.

## Permissions, and why each one exists

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Metering distance, and resolving which place a ride started or ended at |
| `ACCESS_BACKGROUND_LOCATION` | Only for automatic rides by place. A location service started from the background — after a reboot, say — receives no fixes at all without it. Requested when you first flag a place, not at launch |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Metering continues while the app is not on screen. This is the ongoing notification you see |
| `POST_NOTIFICATIONS` | That ongoing notification |
| `SYSTEM_ALERT_WINDOW` | The optional floating amount bubble while the app is in the background |
| `RECEIVE_BOOT_COMPLETED` | Re-arms watching for automatic places after a restart |
| `WAKE_LOCK`, `SCHEDULE_EXACT_ALARM` | Watching for a departure polls on an alarm, because a timer does not wake a sleeping phone. The wake lock covers the few seconds each check takes |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional. Without it, Doze can stretch the checking interval to tens of minutes and an automatic start is missed |

Every one of these is used for the feature named beside it and nothing
else. Location is never sampled when no ride is running and no place is
flagged for automatic starts.

## Deleting your data

* A single ride, place, or stop: delete it in the app.
* Everything: uninstall, or clear the app's storage in system settings.
* The event log: Clear on the Advanced screen.
* Files you exported or shared: delete them wherever you put them.

## Licence

FossRideMeter is free software under GPL-3.0-or-later. You can read every
line of what is described here, and build it yourself.
