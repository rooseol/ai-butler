package com.aibutler.app.pairing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun PairingScreen(
    viewModel: PairingViewModel = viewModel(),
    initialPairingUri: String? = null,
    onPaired: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.connectFromScannedUri(it, onPaired) }
    }

    LaunchedEffect(initialPairingUri) {
        initialPairingUri?.let { viewModel.applyPairingUri(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("AI Butler와 페어링", style = MaterialTheme.typography.titleLarge)
        Text(
            "PC에서 `npm run dev`로 브릿지 서버를 실행한 뒤, 콘솔에 나오는 주소를 PC 브라우저에서 열면 " +
                "(예: http://<PC 주소>:8787/pair) 큰 QR코드가 뜹니다. 아래 버튼으로 그 QR을 스캔하세요.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )

        Button(
            onClick = {
                scanLauncher.launch(
                    ScanOptions().apply {
                        setPrompt("PC 화면의 QR 코드를 비춰주세요")
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("QR 스캔으로 연결")
        }

        Row(modifier = Modifier.padding(vertical = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text("  또는 직접 입력  ", style = MaterialTheme.typography.labelLarge)
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        var pairingUriText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = pairingUriText,
            onValueChange = {
                pairingUriText = it
                viewModel.applyPairingUri(it)
            },
            label = { Text("페어링 URI 붙여넣기 (선택)") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = viewModel.host,
            onValueChange = { viewModel.host = it },
            label = { Text("서버 IP (예: 100.x.y.z 또는 192.168.0.10)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = viewModel.port,
            onValueChange = { viewModel.port = it },
            label = { Text("포트") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = viewModel.token,
            onValueChange = { viewModel.token = it },
            label = { Text("페어링 토큰") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        when (val state = uiState) {
            is PairingUiState.Error -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
            else -> {}
        }

        Button(
            onClick = { viewModel.connect(onPaired) },
            enabled = uiState !is PairingUiState.Connecting,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            if (uiState is PairingUiState.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text("  연결하기")
            } else {
                Text("연결하기")
            }
        }
    }
}
