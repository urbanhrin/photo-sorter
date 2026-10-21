package com.hoon.photosorter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** 안드로이드 8 전용 앱 휴지통 화면 */
@Composable
fun TrashScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val list = vm.trash
    val goHome: () -> Unit = { vm.screen = Screen.Home }
    var confirmEmpty by remember { mutableStateOf(false) }
    BackHandler(onBack = goHome)

    Column(Modifier.fillMaxSize()) {
        TopBar("휴지통 ${list.size}개", "30일이 지나면 자동으로 지워져요 · 누르면 되살리기", goHome)

        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("휴지통이 비어 있어요", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.name }) { e ->
                    val daysLeft = ((e.deletedAt + TRASH_KEEP_MS - System.currentTimeMillis()) / 86_400_000L + 1).coerceAtLeast(0)
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(PhotoBg)
                            .clickable { vm.restore(listOf(e)) },
                    ) {
                        AsyncImage(
                            model = TrashRepo.file(ctx, e),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Pill("${daysLeft}일 남음", Modifier.align(Alignment.TopEnd).padding(8.dp))
                        if (e.isVideo) Pill("동영상", Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("모두 되살리기", Keep, OnKeep, Modifier.weight(1f)) { vm.restore(list) }
                BigButton("비우기", Del, OnDel, Modifier.weight(1f)) { confirmEmpty = true }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("휴지통을 비울까요?") },
            text = { Text("${list.size}개가 영구 삭제되고 되살릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { confirmEmpty = false; vm.emptyTrash(list) }) {
                    Text("비우기", color = OnDel, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("취소", color = TextSub) }
            },
        )
    }
}
