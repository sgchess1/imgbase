package com.example.imgbase.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.imgbase.data.SupabaseConfig
import com.example.imgbase.data.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdminScreen(
                onSwitchToUser = {
                    // UserModeActivity로 전환
                    val intent = Intent(this@AdminModeActivity, UserModeActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
private fun AdminScreen(
    onSwitchToUser: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var items by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) { SupabaseRepository.listImages() }

            when (result) {
                is SupabaseRepository.ListResult.Success -> {
                    items = result.items
                    selected = emptySet()
                    if (result.items.isEmpty()) {
                        snackbarHostState.showSnackbar("버킷이 비어있습니다")
                    }
                }
                is SupabaseRepository.ListResult.Failure -> {
                    // 구체적인 에러 메시지 표시
                    snackbarHostState.showSnackbar(
                        message = result.errorMessage,
                        duration = SnackbarDuration.Long
                    )
                    items = emptyList()
                    selected = emptySet()
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val pullRefreshState = rememberPullRefreshState(refreshing = isLoading, onRefresh = { refresh() })

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {

                // 상단에 모드 전환 토글
                if (onSwitchToUser != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "관리자 모드",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Switch(
                            checked = true,
                            onCheckedChange = {
                                if (!it) {
                                    onSwitchToUser()
                                }
                            }
                        )
                    }

                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { refresh() }, enabled = !isLoading) {
                        Text("새로고침")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val targets = selected.toList()
                            scope.launch {
                                isLoading = true
                                val result = withContext(Dispatchers.IO) {
                                    SupabaseRepository.deleteImages(targets)
                                }

                                when (result) {
                                    is SupabaseRepository.DeleteResult.Success -> {
                                        snackbarHostState.showSnackbar("삭제 완료 (${targets.size}개)")
                                        refresh()
                                    }
                                    is SupabaseRepository.DeleteResult.Failure -> {
                                        isLoading = false
                                        // 구체적인 에러 메시지 표시
                                        snackbarHostState.showSnackbar(
                                            message = result.errorMessage,
                                            duration = SnackbarDuration.Long
                                        )
                                    }
                                }
                            }
                        },
                        enabled = selected.isNotEmpty() && !isLoading
                    ) {
                        Text("선택 삭제 (${selected.size})")
                    }
                }
                Spacer(Modifier.height(12.dp))


                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (items.isEmpty() && !isLoading) {

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "표시할 이미지가 없습니다",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(items, key = { it }) { name ->
                            val isSelected = name in selected
                            val imageUrl = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/${SupabaseConfig.BUCKET_NAME}/$name"
                            Surface(
                                tonalElevation = if (isSelected) 6.dp else 0.dp,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            selected = if (isSelected) selected - name else selected + name
                                        },
                                        onLongClick = {
                                            selected = if (isSelected) selected - name else selected + name
                                        }
                                    )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = name,
                                        modifier = Modifier.height(120.dp).fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = name,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // PullRefreshIndicator
            PullRefreshIndicator(
                refreshing = isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}