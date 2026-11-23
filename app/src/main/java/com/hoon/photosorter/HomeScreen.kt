package com.hoon.photosorter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
                Text("Swik", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (vm.pickMode) "올릴 사진을 골라 모아요 · 원본은 그대로"
                    else "남은 항목 ${kindItems.count { decisions[it.id] == null }}개",
                    color = Muted, fontSize = 14.sp,
                )
            }
            // 안드로이드 8~10 에서만 보이는 앱 휴지통 입구
            if (USE_APP_TRASH && vm.trash.isNotEmpty()) {
                TextButton(onClick = { vm.screen = Screen.Trash }) {
                    Text("휴지통 ${vm.trash.size}", color = TextSub, fontWeight = FontWeight.Bold)
                }
            }
            // 목록 / 달력 전환
            Row(Modifier.background(Surface, RoundedCornerShape(10.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(false to "목록", true to "달력").forEach { (cal, name) ->
                    val selected = vm.calendarMode == cal
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (selected) TextMain else Color.Transparent)
                            .clickable { vm.calendarMode = cal }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            name, fontSize = 14.sp,
                            color = if (selected) Bg else TextSub,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // 정리 / 고르기
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()
                .background(Surface, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(false to "정리", true to "고르기").forEach { (pick, name) ->
                val selected = vm.pickMode == pick
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(11.dp))
                        .background(if (selected) TextMain else Color.Transparent)
                        .clickable { vm.pickMode = pick },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name, fontSize = 16.sp,
                        color = if (selected) Bg else TextSub,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // 고르기: 담을 폴더
        if (vm.pickMode) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val active = vm.activeFolder()
                    vm.folders.forEach { f -> FolderChip(f, selected = f.id == active?.id) { vm.setActive(f.id) } }
                    if (vm.folders.size < MAX_FOLDERS) {
                        val shape = RoundedCornerShape(20.dp)
                        Box(
                            Modifier.height(40.dp).clip(shape).border(1.5.dp, Track, shape)
                                .clickable { vm.newFolder() }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (vm.folders.isEmpty()) "+ 담을 폴더 만들기" else "+ 새 폴더", color = TextSub, fontSize = 14.sp) }
                    }
                }
                if (vm.folders.isNotEmpty()) {
                    TextButton(onClick = { vm.screen = Screen.Folders }) { Text("관리", color = TextSub, fontWeight = FontWeight.Bold) }
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
            vm.calendarMode -> CalendarView(vm, groups.toMap(), decisions, Modifier.weight(1f).fillMaxWidth())
            groups.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("표시할 항목이 없어요", color = Muted)
            }
            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.first.toEpochDay() }) { (date, list) ->
                    if (vm.pickMode) {
                        PickDateCard(date, list, vm.folders) {
                            if (vm.folders.isEmpty()) vm.newFolder() else vm.openDate(date)
                        }
                    } else {
                        DateCard(date, list, decisions, isLast = date == vm.lastDate) { vm.openDate(date) }
                    }
                }
            }
        }

        if (vm.pickMode) {
            val f = vm.activeFolder()
            if (f != null && f.items.isNotEmpty()) {
                BigButton("${f.name} 바구니 ${f.items.size}장 보기", f.bg, f.fg, Modifier.fillMaxWidth().padding(16.dp)) {
                    vm.screen = Screen.Basket(f.id)
                }
            }
        } else if (pending > 0) {
            BigButton("지울 항목 확인 · ${pending}개", Del, OnDel, Modifier.fillMaxWidth().padding(16.dp)) {
                vm.screen = Screen.Review
            }
        }
    }
}

/** 고르기 모드의 날짜 카드: 폴더마다 몇 장 담았는지 색 점으로 보여준다 */
@Composable
private fun PickDateCard(date: LocalDate, list: List<MediaItem>, folders: List<PickFolder>, onOpen: () -> Unit) {
    val ids = list.map { it.id }.toSet()
    val counts = folders.map { f -> f to f.items.count { it in ids } }
    val seenAny = folders.any { f -> f.seen.any { it in ids } }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
            .clickable(onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(date.label(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${list.size}개", color = Muted, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.take(4).forEach {
                MediaView(it, Modifier.weight(1f).height(78.dp).clip(RoundedCornerShape(8.dp)), crop = true)
            }
            repeat(4 - list.take(4).size) { Spacer(Modifier.weight(1f)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val picked = counts.filter { it.second > 0 }
            if (picked.isEmpty()) {
                Text(if (seenAny) "담은 사진 없음" else "아직 안 봄", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            } else {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    picked.forEach { (f, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(f.bg))
                            Text("$n", color = f.fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
            Text("고르기 ›", color = TextSub, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
