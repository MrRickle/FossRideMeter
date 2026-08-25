// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * This file is part of FossRideMeter.
 * Copyright (C) 2026 Rick Hallock
 *
 * FossRideMeter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FossRideMeter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FossRideMeter. If not, see <https://www.gnu.org/licenses/>.
 *
 * As a special exception, this program may be combined and distributed
 * with the Google Play services client libraries - see
 * LICENSE-EXCEPTION.txt.
 */
package org.fossridemeter.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import androidx.core.net.toUri
import org.fossridemeter.app.model.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A short tone for each thing that happens to a ride, so the user knows
 * it happened without looking at the phone.
 *
 * That is the whole point: the screen is face down in a cradle, or the
 * phone is in a pocket while an automatic start fires on the way out of
 * the drive. Every sound therefore comes from the meter itself rather
 * than from a button handler, so a ride that starts by leaving a place
 * sounds exactly like one that starts by being tapped.
 *
 * The default tones are the platform's own (ToneGenerator) rather than
 * bundled audio: no assets to ship, no decoding, and they already sound
 * like device feedback rather than like a notification from an app. Each
 * action can be pointed at a sound from the device instead - anything
 * the system sound picker offers, which is the same list Android's own
 * sound settings show - and then that plays in the tone's place.
 *
 * On STREAM_NOTIFICATION deliberately. It follows the volume the user
 * already set for notifications, and it goes quiet when the phone is
 * silenced, which is what silencing a phone is for. Nothing here is
 * important enough to override that.
 */
class RideSounds(
    private val context: Context,
    private val scope: CoroutineScope,
    /**
     * Read live, per sound, rather than captured: unlike a ride's rates,
     * these are preferences about the phone right now. Turning sounds off
     * mid-shift has to stop them mid-shift, and a sound chosen mid-shift
     * has to be the one that plays next.
     */
    private val settings: () -> Settings,
) {

    // Held only so a Ringtone isn't collected while it is still playing;
    // dropping the last reference to one can cut it off.
    private var playing: Ringtone? = null

    enum class Sound(val tone: Int, val durationMs: Int) {

        /** Two rising beeps - the meter is running. */
        START(ToneGenerator.TONE_PROP_BEEP2, 300),

        /** Held mid tone - metering has stopped but the ride hasn't. */
        PAUSE(ToneGenerator.TONE_PROP_PROMPT, 200),

        /** One beep - back to running, deliberately lighter than START. */
        RESUME(ToneGenerator.TONE_PROP_BEEP, 150),

        /** The acknowledgement tone: written down, done with. */
        SAVE(ToneGenerator.TONE_PROP_ACK, 300),

        /** The platform's negative tone. Nothing was kept. */
        CANCEL(ToneGenerator.TONE_PROP_NACK, 300),
    }

    fun play(sound: Sound) {

        val settings = settings()
        if (!settings.soundEnabled) return

        val chosen = when (sound) {
            Sound.START -> settings.startSoundUri
            Sound.PAUSE -> settings.pauseSoundUri
            Sound.RESUME -> settings.resumeSoundUri
            Sound.SAVE -> settings.saveSoundUri
            Sound.CANCEL -> settings.cancelSoundUri
        }

        if (chosen != null) {
            playChosen(chosen, sound)
            return
        }

        playTone(sound)
    }

    /**
     * A sound from the device, played on the notification stream so it
     * behaves like the tone it replaces - the user's notification
     * volume governs it, and silencing the phone silences it.
     *
     * A Uri can stop being playable between being picked and being used:
     * the file is deleted, or an app that provided it is uninstalled. It
     * falls back to the built-in tone rather than going quiet, since a
     * silent action is indistinguishable from one that didn't happen.
     */
    private fun playChosen(uri: String, sound: Sound) {

        try {
            val ringtone = RingtoneManager.getRingtone(context, uri.toUri())
                ?: return playTone(sound)

            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            playing?.takeIf { it.isPlaying }?.stop()
            playing = ringtone
            ringtone.play()

        } catch (e: Exception) {
            Log.w("RideSounds", "Could not play $uri for $sound", e)
            playTone(sound)
        }
    }

    private fun playTone(sound: Sound) {

        scope.launch {

            var generator: ToneGenerator? = null

            try {
                generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
                generator.startTone(sound.tone, sound.durationMs)

                // startTone returns immediately; releasing under a
                // playing tone cuts it off, so the instance is held for
                // as long as it is making noise and a little after.
                delay(sound.durationMs + RELEASE_MARGIN_MS)

            } catch (e: Exception) {
                // The audio hardware can simply be unavailable - another
                // app holding it, or a device with nothing to play it
                // on. A beep is never worth taking a ride down for.
                Log.w("RideSounds", "Could not play $sound", e)

            } finally {
                generator?.release()
            }
        }
    }

    private companion object {
        const val VOLUME = 80
        const val RELEASE_MARGIN_MS = 150L
    }
}
