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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReviewScreen(vm: AppViewModel, onTrash: () -> Unit) {
    val list = vm.pendingDeletes().sortedByDescending { it.takenAt }
    val goHome: () -> Unit = { vm.screen = Screen.Home }
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
                "${list.size}개 휴지통으로 보내기", Del, OnDel, Modifier.fillMaxWidth(),
                enabled = list.isNotEmpty(), onClick = onTrash,
            )
            Text(
                "휴대폰 휴지통에 30일간 보관돼요",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
