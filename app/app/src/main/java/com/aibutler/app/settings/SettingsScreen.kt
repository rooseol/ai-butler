package com.aibutler.app.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onUnpaired: () -> Unit,
) {
    val connection by viewModel.connection.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setNotificationsEnabled(true)
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("서버 연결", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (connection != null) {
                        Text("주소: ${connection!!.host}:${connection!!.port}")
                        Text("상태: 페어링됨", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("페어링되지 않음")
                    }
                    TextButton(onClick = { viewModel.unpair(onUnpaired) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("연결 해제 / 다시 페어링")
                    }
                }
            }

            Text("알림", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("백그라운드 알림 유지")
                        Text(
                            "앱이 백그라운드에 있어도 연결을 유지해 에이전트 응답/스케줄 완료를 알림으로 받습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setNotificationsEnabled(enabled)
                            }
                        },
                    )
                }
            }

            Text(
                "AI Butler v0.1.0 — 개인용 AI 에이전트 메신저",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
