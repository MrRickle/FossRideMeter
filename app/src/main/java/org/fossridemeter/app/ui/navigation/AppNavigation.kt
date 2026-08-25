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
package org.fossridemeter.app.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossridemeter.app.debug.AdvancedScreen
import org.fossridemeter.app.model.RideStatus
import org.fossridemeter.app.ui.AboutScreen
import org.fossridemeter.app.ui.RideViewModel
import org.fossridemeter.app.ui.RidesViewModel
import org.fossridemeter.app.ui.Screen
import org.fossridemeter.app.ui.FossRideMeter
import org.fossridemeter.app.ui.HelpScreen
import org.fossridemeter.app.ui.LicenseScreen
import org.fossridemeter.app.ui.RidesScreen
import org.fossridemeter.app.ui.PlacesScreen
import org.fossridemeter.app.ui.PlacesViewModel
import org.fossridemeter.app.ui.SettingsScreen
import org.fossridemeter.app.util.downloadsPickerUri
import org.fossridemeter.app.util.fileNameFromUri
import org.fossridemeter.app.util.fileSizeFromUri
import org.fossridemeter.app.util.saveTextToDownloads
import org.fossridemeter.app.util.readTextFromUri
import org.fossridemeter.app.util.writeTextToUri
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import android.os.PowerManager
import android.provider.Settings

// Primary navigation destinations, now shown in the drawer instead of the
// top-bar overflow. Screen.Advanced is among them, and stays a
// destination rather than a row inside Settings for a concrete reason:
// Settings is disabled while a ride is running or paused, and the event
// log is most wanted exactly then - a ride started on its own and the
// user wants to know why. See AdvancedScreen.
private data class MenuAction(
    val label: String,
    val route: String,
    val onClick: (NavHostController) -> Unit
)

private fun menuActionsFor(): List<MenuAction> {
    return listOf(
        MenuAction("Home", Screen.Ride.route) {
            it.navigate(Screen.Ride.route) {
                popUpTo(Screen.Ride.route) { inclusive = true }
            }
        },
        MenuAction("Rides", Screen.Rides.route) { it.navigate(Screen.Rides.route) },
        MenuAction("Places", Screen.Places.route) { it.navigate(Screen.Places.route) },
        MenuAction("Settings", Screen.Settings.route) { it.navigate(Screen.Settings.route) },
        MenuAction("Help", Screen.Help.route) { it.navigate(Screen.Help.route) },
        MenuAction("About", Screen.About.route) { it.navigate(Screen.About.route) },
        MenuAction("License", Screen.License.route) { it.navigate(Screen.License.route) },
        MenuAction("Advanced", Screen.Advanced.route) { it.navigate(Screen.Advanced.route) }
    )
}

/** A picked export target that already holds data, held while the user
 * decides whether it may be replaced. */
private data class PendingExport(
    val uri: Uri,
    val fileName: String,
)

/**
 * Writes an export to a Uri the user picked, confirming first when
 * that Uri already has something in it, and saying what it wrote when
 * it's done. Both export launchers below funnel their picked Uri through
 * the lambda this returns; they differ only in which picker produced it.
 *
 * The JSON is built inside the IO dispatcher block along with the write,
 * so a large export doesn't serialize on the main thread either.
 *
 * Success names the file it actually wrote, not the name that was asked
 * for, because those are routinely different - see the launcher below.
 */
@Composable
private fun rememberExportWriter(
    onExportJson: () -> String
): (Uri) -> Unit {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }

    fun writeExport(uri: Uri) {
        scope.launch {
            try {
                val fileName = withContext(Dispatchers.IO) {
                    writeTextToUri(context, uri, onExportJson())
                    fileNameFromUri(context, uri)
                }
                Toast.makeText(
                    context,
                    "Saved to ${fileName ?: "the file you picked"}.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    pendingExport?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = { Text("Replace ${pending.fileName}?") },
            text = {
                Text(
                    "That file already has something in it. Exporting " +
                            "replaces everything in it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pending.uri
                    pendingExport = null
                    writeExport(uri)
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExport = null }) { Text("Cancel") }
            }
        )
    }

    return { uri ->
        scope.launch {
            val existing = withContext(Dispatchers.IO) {
                val size = fileSizeFromUri(context, uri) ?: 0L
                if (size > 0L) fileNameFromUri(context, uri) ?: "that file" else null
            }
            if (existing == null) {
                writeExport(uri)
            } else {
                pendingExport = PendingExport(uri, existing)
            }
        }
    }
}

/**
 * The pickers open in Downloads rather than wherever the last app left
 * them, because that is where these exports go by default and where the
 * user goes looking for one to import. `EXTRA_INITIAL_URI` is only a
 * starting location - it grants nothing, which is why it may name
 * Downloads at all where a tree request may not.
 */
private class CreateJsonDocument : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsPickerUri())
}

private class OpenAnyDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsPickerUri())
}

/**
 * SAF "create document" launcher: makes a **new** file for the export.
 *
 * It cannot replace an existing one, and that isn't something this app
 * gets to decide. AOSP's picker resolves a name collision by silently
 * numbering the new file - ask for rides.json seven times and you get
 * rides (7).json - with no overwrite offered anywhere in that flow. That
 * is why the export toast names the file it actually wrote, and why the
 * one-tap export below doesn't use this picker at all.
 *
 * This is the whole of export below API 29, where a numbered new file is
 * simply what an export is. The confirm-before-replacing check still
 * runs on this path, for third-party providers that hand back an
 * existing document instead of numbering a new one.
 */
@Composable
private fun rememberJsonExportLauncher(
    onExportJson: () -> String
): ManagedActivityResultLauncher<String, Uri?> {

    val writeExport = rememberExportWriter(onExportJson)

    return rememberLauncherForActivityResult(CreateJsonDocument()) { uri ->
        uri?.let(writeExport)
    }
}

/**
 * One-tap export: writes [fileName] into Downloads with no picker at
 * all, replacing what the last export of the same name wrote.
 *
 * This is the ordinary way to export from API 29 on. It needs no
 * permission and asks nothing, because the file it replaces is one this
 * app created - see saveTextToDownloads. Replacing it silently is the
 * point: a backup that accumulates rides (1..7).json isn't a backup, and
 * a full export supersedes the previous full export completely. The
 * toast still says which of the two happened.
 */
@Composable
@RequiresApi(Build.VERSION_CODES.Q)
private fun rememberDownloadsExport(
    fileName: String,
    onExportJson: () -> String
): () -> Unit {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return {
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    saveTextToDownloads(context, fileName, onExportJson())
                }
                Toast.makeText(
                    context,
                    if (saved.replaced) "Replaced Downloads/${saved.fileName}."
                    else "Saved to Downloads/${saved.fileName}.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        Unit
    }
}

/**
 * SAF "open document" launcher that reads the picked file and hands the
 * text (plus its display name, for callers that sniff format from the
 * extension) to [onImport]. The accepted mime types are passed at the
 * launch() call, not here, since Rides and Places differ there.
 *
 * [onImport] returns the line to show when it's done, because only the
 * caller knows what was imported and how many of it. It suspends, so
 * that line isn't shown until the rows are actually written - see
 * RidesViewModel.importJson. An import that finds nothing to import is
 * the case worth reporting most: picking the wrong file otherwise looks
 * exactly like picking the right one.
 */
@Composable
private fun rememberTextImportLauncher(
    onImport: suspend (text: String, fileName: String?) -> String
): ManagedActivityResultLauncher<Array<String>, Uri?> {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return rememberLauncherForActivityResult(OpenAnyDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val (text, fileName) = withContext(Dispatchers.IO) {
                        readTextFromUri(context, uri) to fileNameFromUri(context, uri)
                    }
                    val message = onImport(text, fileName)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/** "1 ride" / "12 rides", so the import toasts read like sentences. */
private fun countOf(n: Int, singular: String, plural: String): String =
    if (n == 1) "1 $singular" else "$n $plural"

private fun titleFor(route: String?): String = when (route) {
    Screen.Ride.route -> "foss RIDE METER"
    Screen.Rides.route -> "Rides"
    Screen.Places.route -> "Places"
    Screen.Settings.route -> "Settings"
    Screen.Help.route -> "Help"
    Screen.About.route -> "About"
    Screen.License.route -> "License"
    Screen.Advanced.route -> "Advanced"
    else -> "FossRideMeter"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppNavigation() {

    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Single shared RideViewModel for the whole app - passed down to every
    // screen that needs it, instead of each screen fetching its own via
    // viewModel(). This keeps the service binding singular.
    val rideViewModel: RideViewModel = viewModel()

    // Hoisted here for the same reason, plus one of their own: the
    // top-bar overflow below owns the Import/Export items, and it lives
    // outside the NavHost. A viewModel() call inside a composable() entry
    // is scoped to that NavBackStackEntry, so the top bar couldn't reach
    // it. Cost is that both now collect their DB flows for the whole app
    // lifetime rather than only while their screen is open - a few rows
    // at personal-app scale.
    val ridesViewModel: RidesViewModel = viewModel()
    val placesViewModel: PlacesViewModel = viewModel()

    val ridesExportLauncher = rememberJsonExportLauncher { ridesViewModel.exportJson() }
    val ridesImportLauncher = rememberTextImportLauncher { text, _ ->
        val imported = ridesViewModel.importJson(text)
        if (imported == 0) "That file had no rides in it."
        else "Imported ${countOf(imported, "ride", "rides")}."
    }

    val placesExportLauncher = rememberJsonExportLauncher { placesViewModel.exportJson() }

    // The one-tap path, from API 29 on, and null below it - which is
    // also what the menu reads to decide which shape of export to offer.
    // A composable behind an if is fine here because the condition can't
    // change while the process lives.
    val ridesDownloadsExport =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            rememberDownloadsExport("rides.json") { ridesViewModel.exportJson() }
        else null

    val placesDownloadsExport =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            rememberDownloadsExport("places.json") { placesViewModel.exportJson() }
        else null

    // Separate launchers rather than a flag on the existing ones: the
    // payload lambda runs when the file is written, not when the picker
    // opens, so which one is in play has to be decided up front.
    val ridesSelectedExportLauncher =
        rememberJsonExportLauncher { ridesViewModel.exportSelectedJson() }
    val placesSelectedExportLauncher =
        rememberJsonExportLauncher { placesViewModel.exportSelectedJson() }
    val placesImportLauncher = rememberTextImportLauncher { text, fileName ->
        val imported = placesViewModel.importPlaces(text, fileName)
        if (imported == 0) "That file had no places in it."
        else "Imported ${countOf(imported, "place", "places")}."
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("AppNavigation", "Notification permission granted: $granted")
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!alreadyGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* result ignored - we just re-check canDrawOverlays() when actually needed */ }

    LaunchedEffect(Unit) {
        if (!AndroidSettings.canDrawOverlays(context)) {
            val intent = Intent(
                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }
    }

    // Background location is asked for only once a place actually asks to
    // be watched, rather than at launch with every other permission. It
    // is the most invasive prompt the app raises ("Allow all the time"),
    // and arriving right after the user flags a place is the one moment
    // its purpose is self-evident.
    //
    // It is genuinely required, not belt-and-braces: BootReceiver starts
    // the tracking service while the app is in the background, and such a
    // service receives no location fixes at all without it. Watching
    // would appear to run - notification and all - and silently never
    // fire.
    val watchablePlaces by placesViewModel.places.collectAsState()
    val wantsWatching = watchablePlaces.any { it.autoStart || it.autoSave }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("AppNavigation", "Background location granted: $granted")
        if (!granted) {
            // From Android 11 the system will not re-prompt for this, and
            // on some versions never shows a dialog for it at all - the
            // only route to "Allow all the time" is the app's own
            // settings page, so send the user there rather than leaving
            // the feature quietly broken.
            context.startActivity(
                Intent(
                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    LaunchedEffect(wantsWatching) {
        if (wantsWatching && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!alreadyGranted) {
                backgroundLocationLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> rideViewModel.appBackgrounded()
                Lifecycle.Event.ON_START -> rideViewModel.appForegrounded()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var menuExpanded by remember { mutableStateOf(false) }
    var showQuitConfirm by remember { mutableStateOf(false) }

    // --- multi-select on the two table screens -------------------------
    //
    // The selection lives in the ViewModels because the bar that acts on
    // it is drawn here, not by the tables (see SelectionState). This
    // resolves it down to whichever table is actually on screen, so the
    // rest of the code below can stay ignorant of which one that is.
    val ridesSelection by ridesViewModel.selection.selected.collectAsState()
    val placesSelection by placesViewModel.selection.selected.collectAsState()

    val isRidesRoute = currentRoute == Screen.Rides.route
    val isPlacesRoute = currentRoute == Screen.Places.route

    val selectionCount = when {
        isRidesRoute -> ridesSelection.size
        isPlacesRoute -> placesSelection.size
        else -> 0
    }

    val clearSelection = {
        ridesViewModel.selection.clear()
        placesViewModel.selection.clear()
    }

    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }

    // Leaving the screen leaves the mode. A selection that survived
    // navigation would put the contextual bar back on a table the user
    // had already walked away from.
    LaunchedEffect(currentRoute) {
        if (!isRidesRoute && !isPlacesRoute) clearSelection()
    }

    BackHandler(enabled = selectionCount > 0) {
        clearSelection()
    }
    val ride by rideViewModel.ride.collectAsState()
    BackHandler(enabled = currentRoute == Screen.Ride.route) {
        if (ride.status == RideStatus.READY) {
            rideViewModel.quit()
            (context as? ComponentActivity)?.finishAndRemoveTask()
        } else {
            (context as? ComponentActivity)?.moveTaskToBack(true)
        }
    }
    val settingsLocked = ride.status == RideStatus.RUNNING || ride.status == RideStatus.PAUSED

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {

                Text(
                    text = "FossRideMeter",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )

                HorizontalDivider()

                Spacer(Modifier.height(8.dp))

                menuActionsFor().forEach { action ->
                    val disabled = action.label == "Settings" && settingsLocked
                    val selected = currentRoute == action.route

                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = action.label,
                                color = if (disabled) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        selected = selected,
                        onClick = {
                            if (!disabled) {
                                scope.launch { drawerState.close() }
                                action.onClick(navController)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {

        Scaffold(
            topBar = {
                if (selectionCount > 0) {
                    // Contextual bar: while rows are selected the normal
                    // chrome is replaced rather than added to, so the
                    // actions on screen are only ever the ones that apply
                    // to the selection. Closing it is the same gesture as
                    // Back.
                    TopAppBar(
                        title = { Text("$selectionCount selected") },
                        navigationIcon = {
                            IconButton(onClick = { clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    if (isRidesRoute) {
                                        ridesViewModel.selection.selectAll(
                                            ridesViewModel.rides.value.map { it.id }
                                        )
                                    } else {
                                        placesViewModel.selection.selectAll(
                                            placesViewModel.places.value.map { it.id }
                                        )
                                    }
                                }
                            ) {
                                Text("All")
                            }

                            IconButton(
                                onClick = {
                                    if (isRidesRoute) {
                                        ridesSelectedExportLauncher.launch("rides-selected.json")
                                    } else {
                                        placesSelectedExportLauncher.launch("places-selected.json")
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export selected")
                            }

                            IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                            }
                        }
                    )
                } else {
                TopAppBar(
                    title = { Text(titleFor(currentRoute)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // Navigation between screens now lives in the
                        // drawer - this overflow is reserved for actions
                        // that act on the app/current screen rather than
                        // taking you somewhere else.
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            // Import/Export act on whichever list is on
                            // screen, so they only show up on the two
                            // screens that have something to import or
                            // export. The top bar title ("Rides" /
                            // "Places") is what says which one, so the
                            // items themselves stay unqualified.
                            val isRides = currentRoute == Screen.Rides.route
                            val isPlaces = currentRoute == Screen.Places.route

                            if (isRides || isPlaces) {

                                DropdownMenuItem(
                                    text = { Text("Import") },
                                    onClick = {
                                        menuExpanded = false
                                        if (isRides) {
                                            // Rides only ever import/export as our
                                            // own JSON (no CSV/GPX equivalent - a
                                            // ride is a whole trip, not a point),
                                            // so this stays narrowly typed.
                                            ridesImportLauncher.launch(arrayOf("application/json"))
                                        } else {
                                            // "*/*" rather than specific mime
                                            // types - CSV/GPX files are commonly
                                            // reported with generic or missing
                                            // mime types by content providers,
                                            // which would otherwise get filtered
                                            // out of the picker entirely. Format
                                            // is sniffed from the file
                                            // name/content instead (see
                                            // parsePlacesAuto).
                                            placesImportLauncher.launch(arrayOf("*/*"))
                                        }
                                    }
                                )

                                // Export is one tap into Downloads,
                                // replacing the file the last export
                                // wrote. Below API 29 there is no such
                                // thing, so the same item falls back to
                                // the picker and its numbered names -
                                // which is why there is no "overwrite a
                                // file" item anywhere: it would be a
                                // second export item, on every device,
                                // to serve the version that can't do the
                                // first one.
                                val downloadsExport =
                                    if (isRides) ridesDownloadsExport else placesDownloadsExport

                                DropdownMenuItem(
                                    text = { Text("Export") },
                                    onClick = {
                                        menuExpanded = false
                                        if (downloadsExport != null) {
                                            downloadsExport()
                                        } else if (isRides) {
                                            ridesExportLauncher.launch("rides.json")
                                        } else {
                                            placesExportLauncher.launch("places.json")
                                        }
                                    }
                                )

                                // Somewhere other than Downloads - an SD
                                // card, a cloud folder. Only worth its
                                // place in the menu where Export doesn't
                                // already open a picker.
                                if (downloadsExport != null) {
                                    DropdownMenuItem(
                                        text = { Text("Export to...") },
                                        onClick = {
                                            menuExpanded = false
                                            if (isRides) {
                                                ridesExportLauncher.launch("rides.json")
                                            } else {
                                                placesExportLauncher.launch("places.json")
                                            }
                                        }
                                    )
                                }

                                HorizontalDivider()
                            }

                            DropdownMenuItem(
                                text = { Text("Quit") },
                                onClick = {
                                    menuExpanded = false
                                    showQuitConfirm = true
                                }
                            )
                        }
                    }
                )
                }
            }
        ) { innerPadding ->

            if (showDeleteSelectedConfirm) {
                val what = if (isRidesRoute) "ride" else "place"
                AlertDialog(
                    onDismissRequest = { showDeleteSelectedConfirm = false },
                    title = {
                        Text("Delete $selectionCount ${what}${if (selectionCount == 1) "" else "s"}?")
                    },
                    text = {
                        Text(
                            if (isRidesRoute) {
                                "Their stops go with them, and the places they " +
                                    "fed are averaged again without them. This " +
                                    "cannot be undone."
                            } else {
                                "Rides that started, ended or stopped at them keep " +
                                    "their history. Those points get a new unnamed " +
                                    "place at the same spot. This cannot be undone."
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteSelectedConfirm = false
                                if (isRidesRoute) {
                                    ridesViewModel.deleteSelected()
                                } else {
                                    placesViewModel.deleteSelected()
                                }
                            }
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showQuitConfirm) {
                AlertDialog(
                    onDismissRequest = { showQuitConfirm = false },
                    title = { Text("Quit FossRideMeter?") },
                    // Quit stopped meaning quit when watching arrived, and
                    // for a while this dialog still claimed it stopped
                    // tracking. It is the one place the user asks the
                    // question, so it is also where the answer belongs -
                    // including how to stop watching for good, which is
                    // otherwise only findable by knowing it exists.
                    text = {
                        Text(
                            if (wantsWatching || ride.status != RideStatus.READY) {
                                "Quit will close this app, however if the meter is running " +
                                    "it will continue to run " +
                                    "and if there are any places with " +
                                    "auto-start or auto-save set they will continue to work.\n" +
                                    if (wantsWatching) {
                                        "To stop auto functions you have two options \n" +
                                            "1: Make sure no place has auto-start or auto-save on.\n" +
                                            "2: In Android's app settings force stop " +
                                            "FossRideMeter - all automations will be stopped " +
                                            "until you open the app again."
                                    } else {
                                        "To end the ride, save or cancel it before quitting."
                                    }
                            } else {
                                "This will stop tracking and close the app."
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showQuitConfirm = false
                            rideViewModel.quit()
                            (context as? ComponentActivity)?.finishAndRemoveTask()
                        }) { Text("Quit") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showQuitConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            NavHost(
                navController = navController,
                startDestination = Screen.Ride.route,
                modifier = Modifier.padding(innerPadding)
            ) {

                composable(Screen.Ride.route) {
                    val livePlaces by ridesViewModel.places.collectAsState()
                    val liveStopsByRide by ridesViewModel.stopsByRide.collectAsState()

                    FossRideMeter(
                        rideViewModel = rideViewModel,
                        places = livePlaces,
                        stopsByRide = liveStopsByRide,
                        // Same save path as the Rides and Places screens,
                        // so a place named mid-ride still runs
                        // PlaceBoundaryEnforcer.
                        onUpdatePlace = { updated ->
                            placesViewModel.updatePlace(updated)
                        }
                    )
                }

                composable(Screen.Settings.route) {

                    val settings by rideViewModel.settings.collectAsState()

                    SettingsScreen(
                        settings = settings,
                        onSettingsChanged = {
                            rideViewModel.updateSettings(it)
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.Rides.route) {

                    val rides by ridesViewModel.rides.collectAsState()
                    val places by ridesViewModel.places.collectAsState()
                    val stopsByRide by ridesViewModel.stopsByRide.collectAsState()

                    val settings by rideViewModel.settings.collectAsState()

                    RidesScreen(
                        rides = rides,
                        settings = settings,
                        places = places,
                        stopsByRide = stopsByRide,
                        onUpdateRide = {
                            ridesViewModel.updateRide(it)
                        },
                        onDeleteRide = {
                            ridesViewModel.deleteRide(it)
                        },
                        // Deliberately the PlacesViewModel, not the
                        // RidesViewModel: this is the same save path the
                        // Places screen uses, boundary enforcement and
                        // all.
                        onUpdatePlace = { updated ->
                            placesViewModel.updatePlace(updated)
                        },
                        selectedIds = ridesSelection,
                        onToggleSelection = { ridesViewModel.selection.toggle(it) }
                    )
                }

                composable(Screen.Places.route) {

                    val places by placesViewModel.places.collectAsState()
                    val settings by rideViewModel.settings.collectAsState()

                    PlacesScreen(
                        places = places,
                        settings = settings,
                        onUpdatePlace = { updated ->
                            placesViewModel.updatePlace(updated)
                        },
                        onDeletePlace = { id ->
                            placesViewModel.deletePlace(id)
                        },
                        selectedIds = placesSelection,
                        onToggleSelection = { placesViewModel.selection.toggle(it) }
                    )
                }

                composable(Screen.Help.route) {
                    HelpScreen()
                }

                composable(Screen.About.route) {
                    AboutScreen()
                }

                composable(Screen.License.route) {
                    LicenseScreen()
                }

                composable(Screen.Advanced.route) {
                    // Same condition that locks Settings, for a stronger
                    // reason: a restore replaces the database the ride is
                    // being written into, and restarts the process.
                    AdvancedScreen(rideActive = settingsLocked)
                }
            }
        }
    }
}
