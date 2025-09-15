package com.example.imgbase.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.imgbase.data.SupabaseConfig
import com.example.imgbase.data.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch

class UserModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UserUploadScreen(
                onSwitchToAdmin = {
                    // AdminModeActivity로 전환
                    val intent = Intent(this@UserModeActivity, AdminModeActivity::class.java)
                    startActivity(intent)
                    finish() // 현재 Activity 종료 (뒤로가기 시 돌아오지 않도록)
                }
            )
        }
    }
}


@Composable
private fun UserUploadScreen(
    onSwitchToAdmin: (() -> Unit)? = null // 관리자 모드로 전환하는 콜백
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var uploadResultUrl by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedUri = uri
        uploadResultUrl = ""
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 상단에 모드 전환 토글
            if (onSwitchToAdmin != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .padding(bottom = 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "사용자 모드",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Switch(
                        checked = false, // 사용자 모드에서는 false
                        onCheckedChange = {
                            if (it) {
                                onSwitchToAdmin() // 토글 시 관리자 모드로 전환
                            }
                        }
                    )
                }


            }

            // 메인 컨텐츠를 중앙에 배치
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (selectedUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(selectedUri),
                        contentDescription = null,
                        modifier = Modifier.height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Button(onClick = { pickImageLauncher.launch("image/*") }) {
                    Text(text = if (selectedUri == null) "이미지 선택" else "다른 이미지 선택")
                }
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        scope.launch {
                            isUploading = true

                            try {
                                val fileName = withContext(Dispatchers.IO) {
                                    getDisplayName(context, uri) ?: "image_${System.currentTimeMillis()}.jpg"
                                }

                                val bytes = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                }

                                if (bytes == null) {
                                    isUploading = false
                                    snackbarHostState.showSnackbar("파일을 읽을 수 없습니다")
                                    return@launch
                                }

                                val result = withContext(Dispatchers.IO) {
                                    SupabaseRepository.uploadImage(fileName, bytes)
                                }

                                isUploading = false

                                when (result) {
                                    is SupabaseRepository.UploadResult.Success -> {
                                        uploadResultUrl = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/${SupabaseConfig.BUCKET_NAME}/$fileName"

                                        // 성공 메시지 표시
                                        snackbarHostState.showSnackbar(
                                            message = "업로드 완료!",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    is SupabaseRepository.UploadResult.Failure -> {
                                        uploadResultUrl = ""
                                        snackbarHostState.showSnackbar(
                                            message = result.errorMessage,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                isUploading = false
                                snackbarHostState.showSnackbar(
                                    message = "오류 발생: ${e.message ?: "알 수 없는 오류"}",
                                    duration = SnackbarDuration.Long
                                )
                            }
                        }
                    },
                    enabled = selectedUri != null && !isUploading
                ) {
                    Text(text = if (isUploading) "업로드 중..." else "업로드")
                }
                Spacer(Modifier.height(12.dp))

                // 업로드 성공 시 URL과 복사 버튼을 표시
                if (uploadResultUrl.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "업로드된 이미지 URL",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = uploadResultUrl,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                copyToClipboard(context, uploadResultUrl)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "클립보드에 복사되었습니다",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("URL 복사하기")
                        }
                    }
                }
            }
        }
    }
}

private fun getDisplayName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return it.getString(index)
        }
    }
    return null
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("image_url", text)
    clipboard.setPrimaryClip(clip)
}