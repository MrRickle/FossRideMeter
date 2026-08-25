# Quick start and app Help Screen

## Installing

There is no app store listing. Download the APK from the releases page,
open it, and let your browser or file manager install unknown apps when
Android asks.

Later versions install/update earlier ones, data is preserved. 
To install your own or any build with a different key you must uninstall, 
which deletes your rides.
Export first if you are about to try one.

## If a permission toggle will not turn on

Android blocks some settings for apps installed from a file rather than
from a store. The toggle looks normal and does nothing.

To unblock it:

1. Open Settings, Apps, FossRideMeter.
2. Tap the three dots in the top right corner.
3. Choose "Allow restricted settings".
4. Go back and set the permission again.

This will allow **Display over other apps** to be turned on so 
the floating amount bubble can work.

## What each permission is for

* **Location** — measuring distance, and working out which place a ride
  started and ended at. Nothing is measured while no ride is running.
* **Notifications** — the ongoing notification while a ride is metering.
  Android requires it for work that continues in the background.
* **Display over other apps** — the floating amount bubble, so you can
  see the fare while using something else. Optional.
* **Ignore battery optimisation** — optional, and worth it if you use
  automatic rides. Without it Android can stretch the checks between
  looking for you leaving a place, and a start is missed.
* **Background location** — only asked for when you first set a place to
  start or save a ride on its own. Without it the app cannot see you
  leave while it is in the background.

## Your first ride

1. Open Settings and set your rates.
2. Go back to the meter and press **START**.
3. Live distance, time, amount start showing.
4. Press **SAVE**, & confirm to end your ride.

* **PAUSE** stops the meter without ending the ride.
* **CANCEL** throws the ride away.
* **ADD STOP** manually records a stop.
* Settings has a stop detection time setting, a stop is added after that many minutes not moving.   

## Rates

* **Per mile** or per kilometre — charged on distance travelled.
* **Per hour** — calculated from time while moving.
* **Per hour stopped** — calculated from time while stopped.
* **Base amount** — added to every ride.
* **Minimum amount** — The amount until the calculated amount is greater.  
* Saved rides keep the rates they were created with.

## Places

* A place is where a ride starts, ends, or stops. 
* The app names them with a geohash location (like `dp3wjy6n`) until you rename it.
* Open **Places**, tap one, and give it a name. Renaming a place renames it
wherever it has been used.
* The location may be copied and pasted into your maps app to see where it is.
* You may also paste a location from your maps app into a place. The radius
determines what locations in rides and ride stops this place includes.  
* 'Advanced', 'Repair place links' fixes links if needed.  

Two switches in the place editor make rides happen on their own:
* **Auto-start when leaving** — driving out of this place starts a ride.
* **Auto-save when arriving** — arriving here pauses the ride and saves 
it a few minutes later, unless you resume first.

* Setting either switch on for any place turns watching on, 
* Clearing both switches for every place turns watching off.

## Backing up

Everything stays on your phone. Nothing is uploaded, and Android's own
backup is switched off, so
**rides will not be restored automatically.**

Export them yourself: In both the Rides and Places screens, tap the three dots, then
**Export**. Both write a JSON file wherever you point them, and Import reads them back.

Uninstalling the app deletes everything it holds, including the database
backup on the Advanced screen. **Export** before you uninstall.

## Something wrong?

The Advanced screen has an event log that records automatic starts and
saves, which is the first place to look if a ride did not start when you
expected. It also holds a database backup, and any database an upgrade
set aside.

Bugs and questions: https://github.com/MrRickle/FossRideMeter/issues
