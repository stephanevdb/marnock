package com.marnock.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.marnock.app.BuildConfig
import com.marnock.app.clipboard.ClipboardAccessibilityService
import com.marnock.app.MarnockApp
import com.marnock.app.discovery.DiscoveredPeer
import com.marnock.app.sync.ConnectionPath
import com.marnock.app.ui.theme.MarnockExtra
import com.marnock.app.update.ApkInstaller
import com.marnock.app.update.AppUpdate
import com.marnock.app.update.UpdateChecker
import com.marnock.app.update.UpdateNotifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: MarnockApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by app.agent.status.collectAsState()
    val path by app.agent.path.collectAsState()
    val lastClip by app.agent.lastClipboard.collectAsState()
    val clipboardOn by app.settings.clipboardEnabledFlow.collectAsState(initial = false)
    val localOnly by app.settings.localOnlyFlow.collectAsState(initial = true)
    val paired by app.settings.pairedFlow.collectAsState(initial = false)
    var scanning by remember { mutableStateOf(false) }
    var relayUrl by remember { mutableStateOf(com.marnock.app.data.AppSettings.DEFAULT_RELAY_URL) }
    var showRelay by remember { mutableStateOf(false) }
    val peers by app.agent.discoveredPeers().collectAsState()
    val transfers by app.agent.transferProgress().collectAsState()
    val findRinging by app.agent.findRinging.collectAsState()
    var clipboardA11yTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) clipboardA11yTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val clipboardA11yOn = remember(clipboardA11yTick) {
        ClipboardAccessibilityService.isEnabled(context)
    }
    var wifiPermTick by remember { mutableStateOf(0) }
    val wifiPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { wifiPermTick++ }
    val wifiPermOk = remember(wifiPermTick) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val nearby = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
        fine || nearby
    }

    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(AndroidTab.Home) }
    val installer = remember { ApkInstaller(context.applicationContext) }
    val downloadProgress by installer.progress.collectAsState()

    LaunchedEffect(Unit) {
        runCatching { UpdateChecker().check() }
            .onSuccess { update ->
                availableUpdate = update
                if (update != null) {
                    UpdateNotifier.notify(context, update.version)
                }
            }
    }

    if (scanning) {
        QrScanScreen(
            onResult = { qr ->
                scanning = false
                app.agent.pairFromQr(qr)
            },
            onCancel = { scanning = false }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == AndroidTab.Home,
                    onClick = { tab = AndroidTab.Home },
                    icon = { Icon(Icons.Outlined.LaptopMac, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = tab == AndroidTab.Phone,
                    onClick = { tab = AndroidTab.Phone },
                    icon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = "Phone") },
                    label = { Text("Phone") }
                )
                NavigationBarItem(
                    selected = tab == AndroidTab.Transfer,
                    onClick = { tab = AndroidTab.Transfer },
                    icon = { Icon(Icons.Outlined.UploadFile, contentDescription = "Transfer") },
                    label = { Text("Transfer") }
                )
                NavigationBarItem(
                    selected = tab == AndroidTab.Settings,
                    onClick = { tab = AndroidTab.Settings },
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 24.dp)
        ) {
            when (tab) {
                AndroidTab.Home -> {
                    BrandHero(status = status, path = path, paired = paired)
                    UpdateCard(
                        availableUpdate = availableUpdate,
                        updateDismissed = updateDismissed,
                        updating = updating,
                        updateError = updateError,
                        downloadProgress = downloadProgress,
                        onUpdate = {
                            scope.launch {
                                val update = availableUpdate ?: return@launch
                                updateError = null
                                if (!installer.canRequestPackageInstalls()) {
                                    installer.openInstallPermissionSettings()
                                    updateError = "Allow installing apps from Marnock, then tap Update again"
                                    return@launch
                                }
                                updating = true
                                val result = installer.downloadAndPromptInstall(update.downloadUrl)
                                updating = false
                                result.onFailure {
                                    updateError = it.message ?: "Update failed"
                                }
                            }
                        },
                        onLater = { updateDismissed = true }
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    PairingAction(paired = paired, onScan = { scanning = true })
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionLabel("Nearby Macs")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (peers.isEmpty()) {
                            "None yet — keep the Mac app open on the same Wi‑Fi."
                        } else {
                            "Tap a Mac to connect over LAN."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PeerList(peers = peers, onConnect = { app.agent.connectToPeer(it) })
                }
                AndroidTab.Phone -> {
                    SectionLabel("Phone")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (findRinging) {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = {
                                    Text("Find My Phone is ringing", style = MaterialTheme.typography.titleMedium)
                                },
                                supportingContent = {
                                    Text("Stop the alert from here or the Mac.", style = MaterialTheme.typography.bodyMedium)
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.PhoneAndroid,
                                        contentDescription = "Find phone",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                trailingContent = {
                                    TextButton(onClick = { app.agent.stopFindRing() }) { Text("Stop") }
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Text(
                            text = "Use Find phone from the Mac menu bar when this device is misplaced.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        AccessRow(
                            headline = "Wi‑Fi name access",
                            supporting = if (wifiPermOk) {
                                "Location must stay on for Android to expose the SSID (password is never shared)"
                            } else {
                                "Grant Location / Nearby devices so Mac can show your Wi‑Fi name"
                            },
                            icon = Icons.Outlined.Wifi,
                            actionLabel = if (wifiPermOk) "Location" else "Grant",
                            onClick = {
                                if (!wifiPermOk) {
                                    val perms = buildList {
                                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                        }
                                    }.toTypedArray()
                                    wifiPermLauncher.launch(perms)
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }
                            }
                        )
                    }
                }
                AndroidTab.Transfer -> {
                    SectionLabel("Transfers")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (transfers.isEmpty()) {
                        Text(
                            text = "Incoming and outgoing files appear here. Incoming files save to Downloads/Marnock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            transfers.takeLast(8).forEachIndexed { index, t ->
                                if (index > 0) HorizontalDivider()
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = t.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${t.direction} · ${t.status}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (t.bytesTotal > 0L) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { (t.bytesDone.toFloat() / t.bytesTotal.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (t.status == "sending" || t.status == "receiving" || t.status == "offering") {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        TextButton(onClick = { app.agent.cancelTransfer(t.id) }) { Text("Cancel") }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionLabel("Share")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use Android Share → Marnock to send files or open links on your Mac. " +
                            "Photos and files work over LAN or relay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ListItem(
                        headlineContent = { Text("Share target ready", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = {
                            Text("Files save to Downloads/Marnock on either device", style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                    )
                }
                AndroidTab.Settings -> {
                    SectionLabel("Sync")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        SettingToggle(
                            headline = "Clipboard",
                            supporting = if (lastClip.isBlank()) {
                                if (clipboardA11yOn) {
                                    "Mirror text with Mac. Copies sync in the background"
                                } else {
                                    "Mirror text with Mac. Enable clipboard access below for instant phone→Mac copies"
                                }
                            } else {
                                "Last: ${lastClip.take(48)}${if (lastClip.length > 48) "…" else ""}"
                            },
                            icon = Icons.Outlined.ContentCopy,
                            checked = clipboardOn,
                            onCheckedChange = { scope.launch { app.settings.setClipboardEnabled(it) } }
                        )
                        HorizontalDivider()
                        AccessRow(
                            headline = "Clipboard access",
                            supporting = if (clipboardA11yOn) {
                                "On — copies on this phone go to the Mac instantly"
                            } else {
                                "Required for instant phone→Mac copies; otherwise tap the send notification"
                            },
                            icon = Icons.Outlined.ContentCopy,
                            actionLabel = if (clipboardA11yOn) "Granted" else "Open",
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        )
                        HorizontalDivider()
                        AccessRow(
                            headline = "Notification access",
                            supporting = "Needed to mirror alerts to your Mac",
                            icon = Icons.Outlined.Notifications,
                            actionLabel = "Open",
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionLabel("Connection")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        SettingToggle(
                            headline = "Local-only",
                            supporting = "Prefer LAN and skip the relay",
                            icon = Icons.Outlined.Wifi,
                            checked = localOnly,
                            onCheckedChange = { scope.launch { app.settings.setLocalOnly(it) } }
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = if (showRelay) "Hide relay URL" else "Relay URL",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "Optional internet path when away from home",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = "Relay",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                TextButton(onClick = { showRelay = !showRelay }) {
                                    Text(if (showRelay) "Hide" else "Edit")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                    AnimatedVisibility(
                        visible = showRelay,
                        enter = fadeIn() + slideInVertically { it / 3 },
                        exit = fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = relayUrl,
                                onValueChange = { relayUrl = it },
                                label = { Text("WebSocket URL") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { scope.launch { app.settings.setRelayUrl(relayUrl) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Save relay URL")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "SMS, phone, contacts, photos, nearby Wi‑Fi, and notification access are needed for full sync. " +
                            "Call audio stays on this phone or its Bluetooth headset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private enum class AndroidTab { Home, Phone, Transfer, Settings }

@Composable
private fun UpdateCard(
    availableUpdate: AppUpdate?,
    updateDismissed: Boolean,
    updating: Boolean,
    updateError: String?,
    downloadProgress: Float,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    val update = availableUpdate
    if (update == null || updateDismissed) return
    Spacer(modifier = Modifier.height(20.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = "Update",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Update available", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "v${update.version} (you have ${BuildConfig.VERSION_NAME})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (updating && downloadProgress >= 0f) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (updateError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = updateError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUpdate, enabled = !updating) {
                    Text(if (updating) "Downloading…" else "Update")
                }
                TextButton(onClick = onLater) { Text("Later") }
            }
        }
    }
}

@Composable
private fun BrandHero(
    status: String,
    path: ConnectionPath,
    paired: Boolean
) {
    val pathLabel = when (path) {
        ConnectionPath.Lan -> "LAN"
        ConnectionPath.Relay -> "Relay"
        ConnectionPath.Offline -> "Offline"
    }
    val chipIcon: ImageVector = when (path) {
        ConnectionPath.Lan -> Icons.Outlined.Wifi
        ConnectionPath.Relay -> Icons.Outlined.Link
        ConnectionPath.Offline -> Icons.Outlined.WifiOff
    }
    val chipColor = when {
        path == ConnectionPath.Lan -> MarnockExtra.colors.connected
        path == ConnectionPath.Relay -> MarnockExtra.colors.relay
        paired -> MarnockExtra.colors.connecting
        else -> MarnockExtra.colors.offline
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Marnock",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Phone and Mac, same network — no account required.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        AnimatedContent(
            targetState = Triple(status, pathLabel, paired),
            transitionSpec = {
                (fadeIn(tween(280)) + slideInVertically { it / 4 })
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "status"
        ) { (s, p, isPaired) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = s,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            buildString {
                                append(p)
                                append(" · ")
                                append(if (isPaired) "Paired" else "Not paired")
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = chipIcon,
                            contentDescription = null,
                            tint = chipColor
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconContentColor = chipColor
                    )
                )
            }
        }
    }
}

@Composable
private fun PairingAction(
    paired: Boolean,
    onScan: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pairScale"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (paired) "Reconnect or re-pair" else "Pair with your Mac",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Open Marnock on your Mac and scan its pairing QR.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onScan,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .scale(scale),
            interactionSource = interaction
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCodeScanner,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (paired) "Scan pairing QR again" else "Scan Mac pairing QR"
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingToggle(
    headline: String,
    supporting: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(headline, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(supporting, style = MaterialTheme.typography.bodyMedium)
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun AccessRow(
    headline: String,
    supporting: String,
    icon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(headline, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(supporting, style = MaterialTheme.typography.bodyMedium)
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(actionLabel)
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun PeerList(
    peers: List<DiscoveredPeer>,
    onConnect: (DiscoveredPeer) -> Unit
) {
    if (peers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        peers.forEachIndexed { index, peer ->
            val visibleState = remember(peer.deviceId) {
                MutableTransitionState(false).apply { targetState = true }
            }
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(tween(280, delayMillis = index * 60)) +
                    slideInVertically(
                        animationSpec = tween(320, delayMillis = index * 60),
                        initialOffsetY = { it / 2 }
                    )
            ) {
                PeerConnectRow(peer = peer, onConnect = { onConnect(peer) })
            }
        }
    }
}

@Composable
private fun PeerConnectRow(
    peer: DiscoveredPeer,
    onConnect: () -> Unit
) {
    OutlinedCard(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = "${peer.host}:${peer.port}",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.LaptopMac,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Text(
                    text = "Connect",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}
