package com.hoon.photosorter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val decisions = vm.decisions
    val kindItems = remember(vm.items, vm.kind) { vm.items.filter { it.kind == vm.kind } }
    val groups = remember(vm.items, vm.kind, vm.filter) {
        vm.visible().groupBy { it.date }.toList().sortedByDescending { it.first }
    }
    val pending = vm.pendingDeletes().size

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("사진 정리", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("남은 항목 ${kindItems.count { decisions[it.id] == null }}개", color = Muted, fontSize = 14.sp)
            }
            // 안드로이드 8 에서만 보이는 앱 휴지통 입구
            if (USE_APP_TRASH && vm.trash.isNotEmpty()) {
                TextButton(onClick = { vm.screen = Screen.Trash }) {
                    Text("휴지통 ${vm.trash.size}", color = TextSub, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 사진 / 동영상
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
                .background(Surface, RoundedCornerShape(12.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Kind.entries.forEach { k ->
                val selected = vm.kind == k
                val count = vm.items.count { it.kind == k }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (selected) TextMain else Color.Transparent)
                        .clickable { vm.selectKind(k) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${if (k == Kind.IMAGE) "사진" else "동영상"} $count",
                        color = if (selected) Bg else TextSub,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        // 필터
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = if (vm.kind == Kind.IMAGE) Filter.entries else listOf(Filter.ALL, Filter.SCREENSHOT)
            filters.forEach { f ->
                val n = when (f) {
                    Filter.ALL -> kindItems.size
                    Filter.SCREENSHOT -> kindItems.count { it.isScreenshot }
                    Filter.BURST -> kindItems.count { it.groupId != -1L }
                }
                val name = when (f) {
                    Filter.ALL -> "전체"
                    Filter.SCREENSHOT -> if (vm.kind == Kind.IMAGE) "스크린샷" else "화면 녹화"
                    Filter.BURST -> "연사"
                }
                FilterChip(selected = vm.filter == f, onClick = { vm.filter = f }, label = { Text("$name $n") })
            }
        }

        when {
            !vm.loaded -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Keep)
            }
            groups.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("표시할 항목이 없어요", color = Muted)
            }
            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.first.toEpochDay() }) { (date, list) ->
                    DateCard(date, list, decisions, isLast = date == vm.lastDate) { vm.openDate(date) }
                }
            }
        }

        if (pending > 0) {
            BigButton("지울 항목 확인 · ${pending}개", Del, OnDel, Modifier.fillMaxWidth().padding(16.dp)) {
                vm.screen = Screen.Review
            }
        }
    }
}

@Composable
private fun DateCard(
    date: LocalDate,
    list: List<MediaItem>,
    decisions: Map<Long, Decision>,
    isLast: Boolean,
    onOpen: () -> Unit,
) {
    val done = list.count { decisions[it.id] != null }
    val similar = list.mapNotNull { it.groupId.takeIf { g -> g != -1L } }.distinct().size
    val thumbs = list.filter { decisions[it.id] == null }.take(3).ifEmpty { list.take(3) }
    val info = "${list.size}개" + (if (similar > 0) " · 비슷한 묶음 $similar" else "")

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
            .clickable(onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(date.label(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(info, color = Muted, fontSize = 13.sp)
        }
        if (done < list.size) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                thumbs.forEach {
                    MediaView(it, Modifier.weight(1f).height(104.dp).clip(RoundedCornerShape(8.dp)), crop = true)
                }
                repeat(3 - thumbs.size) { Spacer(Modifier.weight(1f)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { done.toFloat() / list.size },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Keep,
                    trackColor = Track,
                )
                Text(if (done == 0) "시작 전" else "${done * 100 / list.size}%", color = TextSub, fontSize = 13.sp)
            }
            if (isLast && done > 0) Text("이어서 정리하기 ›", color = Keep, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        } else {
            Text("정리 완료 · 눌러서 다시 정리", color = Done, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
