package com.hoon.photosorter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** useTrash=false 는 안드로이드 10 이하: 휴지통이 없어 바로 영구 삭제된다 */
@Composable
fun ReviewScreen(vm: AppViewModel, useTrash: Boolean, onTrash: () -> Unit) {
    val list = vm.pendingDeletes().sortedByDescending { it.takenAt }
    val goHome: () -> Unit = { vm.screen = Screen.Home }
    var confirm by remember { mutableStateOf(false) }
    BackHandler(onBack = goHome)

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "지울 항목 ${list.size}개",
            "지우면 ${sizeLabel(list.sumOf { it.size })}가 비워져요 · 누르면 다시 남기기",
            goHome,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { item ->
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .clickable { vm.update(mapOf(item.id to Decision.KEEP)) },
                ) {
                    MediaView(item, Modifier.fillMaxSize(), crop = true)
                    Pill("지움", Modifier.align(Alignment.TopEnd).padding(8.dp), bg = Del, fg = OnDel, bold = true)
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            BigButton(
                if (useTrash) "${list.size}개 휴지통으로 보내기" else "${list.size}개 영구 삭제",
                Del, OnDel, Modifier.fillMaxWidth(),
                enabled = list.isNotEmpty(),
                onClick = { if (useTrash) onTrash() else confirm = true },
            )
            Text(
                if (useTrash) "휴대폰 휴지통에 30일간 보관돼요"
                else "이 휴대폰은 휴지통이 없어서 지우면 되살릴 수 없어요",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("${list.size}개를 영구 삭제할까요?") },
            text = { Text("지운 사진과 동영상은 되살릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onTrash() }) {
                    Text("영구 삭제", color = OnDel, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("취소", color = TextSub) }
            },
        )
    }
}
