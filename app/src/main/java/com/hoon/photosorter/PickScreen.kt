package com.hoon.photosorter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** 고르기: 한 장씩 크게 보면서 오른쪽은 담기, 왼쪽은 넘기기. 넘겨도 지워지지 않는다 */
@Composable
fun PickScreen(vm: AppViewModel, date: LocalDate, onPlay: (MediaItem) -> Unit) {
    val goHome: () -> Unit = { vm.screen = Screen.Home }
    BackHandler(onBack = goHome)
    val f = vm.activeFolder()
    if (f == null) {
        LaunchedEffect(Unit) { goHome() }
        return
    }
    val list = remember(vm.items, vm.kind, vm.filter, date, f.photoOnly) { vm.pickList(date, f) }
    val idx = list.indexOfFirst { it.id !in f.seen }
    val seenCount = list.count { it.id in f.seen }

    Column(Modifier.fillMaxSize()) {
        TopBar(date.label(), "고르기 · $seenCount / ${list.size}", goHome)

        // 몇 장째 담는 중 + 담을 폴더 바꾸기
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(f.bg.copy(alpha = 0.35f))
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${f.items.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = f.fg)
                Text("장째 담는 중", fontSize = 15.sp, color = f.fg, modifier = Modifier.padding(bottom = 3.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.folders.forEach { other ->
                    FolderChip(other, selected = other.id == f.id, showCount = false) { vm.setActive(other.id) }
                }
            }
        }

        if (idx == -1) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val msg = if (list.isEmpty()) {
                    if (f.photoOnly && vm.kind == Kind.VIDEO) "‘${f.name}’은 사진만 담는 폴더예요" else "고를 사진이 없어요"
                } else "이 날짜는 ‘${f.name}’ 기준으로 다 봤어요"
                Text(msg, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                BigButton("${f.name} 바구니 보기 · ${f.items.size}장", f.bg, f.fg, Modifier.fillMaxWidth()) {
                    vm.screen = Screen.Basket(f.id)
                }
                BigButton("목록으로", Surface, TextMain, Modifier.fillMaxWidth(), onClick = goHome)
                if (vm.canUndoPick) BigButton("되돌리기", Surface, TextMain, Modifier.fillMaxWidth()) { vm.undoPick() }
                if (list.isNotEmpty()) {
                    BigButton("처음부터 다시 보기", Surface, TextMain, Modifier.fillMaxWidth()) { vm.resetSeen(date, f.id) }
                    Text("담아 둔 사진은 그대로 있어요", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            val item = list[idx]
            val next = list.subList(idx + 1, list.size).firstOrNull { it.id !in f.seen }

            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                if (next != null) MediaView(next, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
                key(item.id, f.id) {
                    SwipeCard(
                        item,
                        onKeep = { vm.pick(f.id, item.id, add = true) },
                        onDelete = { vm.pick(f.id, item.id, add = false) },
                        right = Triple("${f.name}에 담기", f.bg, f.fg),
                        left = Triple("넘기기", Track, TextMain),
                    )
                }
            }

            // 다른 폴더에 담긴 사진 표시
            val inOthers = vm.folders.filter { it.id != f.id && item.id in it.items }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${timeLabel(item.takenAt)} · ${sizeLabel(item.size)}", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (item.id in f.items) Pill("이미 담김", bg = f.bg, fg = f.fg, bold = true)
                inOthers.forEach { Pill("${it.name}에 담김", bg = it.bg, fg = it.fg, bold = true) }
                if (item.kind == Kind.VIDEO) OutlinedButton(onClick = { onPlay(item) }) { Text("재생") }
            }
            Text(
                "오른쪽으로 밀면 담기 · 왼쪽으로 밀면 넘기기",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("넘기기", Track, TextMain, Modifier.weight(1f)) { vm.pick(f.id, item.id, add = false) }
                BigButton("되돌리기", Surface, TextMain, Modifier.weight(1f), enabled = vm.canUndoPick) { vm.undoPick() }
                BigButton("담기", f.bg, f.fg, Modifier.weight(1f)) { vm.pick(f.id, item.id, add = true) }
            }
        }
    }
}

/** 폴더 칩: 선택된 폴더는 테두리로 표시 */
@Composable
fun FolderChip(f: PickFolder, selected: Boolean, showCount: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    var m = Modifier.height(40.dp).clip(shape).background(f.bg)
    if (selected) m = m.border(2.dp, f.fg, shape)
    Box(m.clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(
            if (showCount) "${f.name} ${f.items.size}" else f.name,
            color = f.fg, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
