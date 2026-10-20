package com.hoon.photosorter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun CompareScreen(vm: AppViewModel, groupId: Long, from: LocalDate) {
    val group = remember(vm.items, groupId) { vm.groupItems(groupId) }
    var keep by remember(groupId) {
        mutableStateOf(group.filter { vm.decisions[it.id] == Decision.KEEP }.map { it.id }.toSet())
    }
    val back: () -> Unit = { vm.screen = Screen.Sort(from) }
    BackHandler(onBack = back)

    Column(Modifier.fillMaxSize()) {
        TopBar("비슷한 사진 ${group.size}장", "남길 사진을 누르세요 · 나머지는 지울 항목으로", back)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(group, key = { _, it -> it.id }) { i, item ->
                val k = item.id in keep
                val shape = RoundedCornerShape(16.dp)
                // 화면 높이의 절반 가까이 차지해서 두 장씩 위아래로 비교된다
                Box(
                    Modifier.fillParentMaxHeight(0.48f).fillMaxWidth().clip(shape)
                        .border(3.dp, if (k) Keep else Color.Transparent, shape)
                        .clickable { keep = if (k) keep - item.id else keep + item.id },
                ) {
                    MediaView(item, Modifier.fillMaxSize())
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(
                            "${i + 1} · ${if (k) "남기기" else "지우기"}",
                            bg = if (k) Keep else Del, fg = if (k) OnKeep else OnDel, bold = true,
                        )
                        Pill(timeLabel(item.takenAt, withSeconds = true))
                    }
                }
            }
        }

        val del = group.size - keep.size
        BigButton(
            if (keep.isEmpty()) "남길 사진을 1장 이상 골라주세요" else "${keep.size}장 남기고 ${del}장 지우기",
            TextMain, Bg,
            Modifier.fillMaxWidth().padding(16.dp),
            enabled = keep.isNotEmpty(),
        ) {
            vm.update(group.associate { it.id to if (it.id in keep) Decision.KEEP else Decision.DELETE })
            back()
        }
    }
}
