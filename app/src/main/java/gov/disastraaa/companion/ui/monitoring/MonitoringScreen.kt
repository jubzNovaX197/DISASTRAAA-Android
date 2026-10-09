package gov.disastraaa.companion.ui.monitoring

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gov.disastraaa.companion.data.repository.SyncStatus
import gov.disastraaa.companion.service.TrackingStatus
import gov.disastraaa.companion.utils.PermissionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.refreshPermissions(context)
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            viewModel.startMonitoring(context)
        } else {
            Toast.makeText(
                context,
                "Location permission is required for disaster safety monitoring.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "DISASTRAAA",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.logout(context)
                        onLogoutClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Screen Title
            Text(
                text = "Live Safety Monitoring",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // User Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = state.userName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (state.userEmail.isNotBlank()) {
                            Text(
                                text = state.userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "ROLE: ${state.userRole.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status Badge
            val (statusColor, statusBg, statusText) = when (state.trackingStatus) {
                TrackingStatus.NOT_TRACKING -> Triple(Color(0xFF757575), Color(0xFFEEEEEE), "NOT TRACKING")
                TrackingStatus.STARTING -> Triple(Color(0xFFF57C00), Color(0xFFFFF3E0), "STARTING...")
                TrackingStatus.TRACKING -> {
                    if (state.isQueuedLocally) {
                        Triple(Color(0xFFE65100), Color(0xFFFFF3E0), "TRACKING (OFFLINE QUEUED)")
                    } else {
                        Triple(Color(0xFF1B5E20), Color(0xFFE8F5E9), "LIVE TRACKING ACTIVE")
                    }
                }
                TrackingStatus.STOPPING -> Triple(Color(0xFFE65100), Color(0xFFFFF3E0), "STOPPING...")
                TrackingStatus.ERROR -> Triple(Color(0xFFB71C1C), Color(0xFFFFEBEE), "TRACKING ERROR")
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Current Monitoring Status:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = statusBg,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isActionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = statusColor
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Privacy & Consent Notice Box
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Consent Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Your GPS location is transmitted to DISASTRAAA in real-time only while safety monitoring is active. If internet connectivity drops, location records are saved in your device's offline queue and synchronized automatically when connection returns. You can stop tracking at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error Banner (if error exists)
            AnimatedVisibility(
                visible = state.errorMessage != null && state.errorMessage!!.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }

            // Live Telemetry Card (visible when tracking or when location data is available)
            AnimatedVisibility(
                visible = state.trackingStatus == TrackingStatus.TRACKING || state.lastLatitude != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.riskAssessment?.inHazardZone == true) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (state.riskAssessment?.inHazardZone == true) Icons.Default.Warning else Icons.Default.GpsFixed,
                                contentDescription = "GPS Status",
                                tint = if (state.riskAssessment?.inHazardZone == true) Color(0xFFE65100) else Color(0xFF2E7D32),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.riskAssessment?.inHazardZone == true) "HAZARD ZONE WARNING" else "Telemetry Status",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (state.riskAssessment?.inHazardZone == true) Color(0xFFE65100) else Color(0xFF1B5E20)
                            )
                        }

                        if (state.lastLatitude != null && state.lastLongitude != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Coordinates:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "%.5f, %.5f".format(state.lastLatitude, state.lastLongitude),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        if (state.lastAccuracyMeters != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Accuracy:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "± %.1f meters".format(state.lastAccuracyMeters),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (state.lastUploadTimeMillis != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date(state.lastUploadTimeMillis!!))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Last Delivery:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Delivery State:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.isQueuedLocally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = "Queued",
                                        tint = Color(0xFFE65100),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Stored in Offline Queue",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFE65100)
                                    )
                                }
                            } else if (state.lastUploadSuccess == true) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Ingested Live by Backend",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Pending Upload",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF757575)
                                )
                            }
                        }

                        state.riskAssessment?.let { risk ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (risk.inHazardZone) Color(0xFFFFCCBC) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Risk Level: ${risk.riskLevel}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (risk.inHazardZone) Color(0xFFD84315) else Color(0xFF2E7D32)
                                    )
                                    if (risk.title != null) {
                                        Text(
                                            text = risk.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF37474F)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Permission Request Card (visible if location permissions are not granted)
            if (!state.hasLocationPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFFF57F17),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Location Permission Required",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF57F17)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To enable live safety monitoring, DISASTRAAA requires location access to compare your position against disaster hazard zones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5D4037)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                val perms = mutableListOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Grant Location Permission")
                        }
                    }
                }
            }

            // Action Buttons (START / STOP MONITORING)
            if (state.trackingStatus != TrackingStatus.TRACKING &&
                state.trackingStatus != TrackingStatus.STOPPING
            ) {
                // START MONITORING BUTTON
                Button(
                    onClick = {
                        val permissions = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (PermissionUtils.hasLocationPermissions(context)) {
                            viewModel.startMonitoring(context)
                        } else {
                            permissionLauncher.launch(permissions.toTypedArray())
                        }
                    },
                    enabled = !state.isActionInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    if (state.trackingStatus == TrackingStatus.STARTING) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "STARTING MONITORING...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "START SAFETY MONITORING",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
            } else {
                // STOP MONITORING BUTTON
                Button(
                    onClick = {
                        viewModel.stopMonitoring(context)
                    },
                    enabled = !state.isActionInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    if (state.trackingStatus == TrackingStatus.STOPPING) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "STOPPING MONITORING...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GpsOff,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "STOP MONITORING",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Offline Sync & Queue Status Card (Phase 4 Enhanced)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline Queue & Synchronization",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val syncBadgeText = when (state.syncStatus) {
                                SyncStatus.SYNCING -> "Syncing with backend..."
                                SyncStatus.WAITING_TO_SYNC -> "Waiting for connection"
                                SyncStatus.SUCCESS -> if (state.unsyncedCount == 0) "All records synchronized" else "Batch confirmed"
                                SyncStatus.ERROR -> state.lastSyncError ?: "Sync error (will retry)"
                                SyncStatus.IDLE -> if (state.unsyncedCount > 0) "${state.unsyncedCount} records queued" else "Queue empty"
                            }

                            Text(
                                text = syncBadgeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (state.syncStatus) {
                                    SyncStatus.ERROR -> Color(0xFFC62828)
                                    SyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
                                    SyncStatus.SUCCESS -> Color(0xFF2E7D32)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )

                            if (state.unsyncedCount > 0) {
                                Text(
                                    text = "${state.unsyncedCount} pending records in local Room database",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (state.unsyncedCount > 0) {
                            OutlinedButton(
                                onClick = { viewModel.syncNow(context) },
                                enabled = !state.isSyncing,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (state.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Syncing")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = "Sync",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Sync Now")
                                }
                            }
                        }
                    }

                    if (state.lastSyncTimeMillis != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val lastSyncStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(state.lastSyncTimeMillis!!))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Last successful sync: $lastSyncStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.syncFeedback != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.syncFeedback ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unsent location pings are stored securely in local Room database and synchronized automatically once network connectivity is restored.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
