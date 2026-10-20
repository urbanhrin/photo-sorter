package com.hoon.photosorter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SortScreen(vm: AppViewModel, date: LocalDate, onPlay: (MediaItem) -> Unit) {
    val list = remember(vm.items, vm.kind, vm.filter, date) { vm.itemsFor(date) }
    val decisions = vm.decisions
    // 아직 안 고른 첫 장 = 정리하던 위치
    val idx = list.indexOfFirst { decisions[it.id] == null }
    val done = list.count { decisions[it.id] != null }
    val goHome: () -> Unit = { vm.screen = Screen.Home }
    BackHandler(onBack = goHome)

    Column(Modifier.fillMaxSize()) {
        TopBar(date.label(), "${if (vm.kind == Kind.IMAGE) "사진" else "동영상"} · $done / ${list.size}", goHome)
        LinearProgressIndicator(
            progress = { if (list.isEmpty()) 1f else done.toFloat() / list.size },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp),
            color = Keep,
            trackColor = Track,
        )

        if (idx == -1) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("이 날짜는 정리를 마쳤어요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                BigButton("목록으로", Keep, OnKeep, Modifier.fillMaxWidth(), onClick = goHome)
                if (vm.canUndo) BigButton("되돌리기", Surface, TextMain, Modifier.fillMaxWidth()) { vm.undo() }
                BigButton("처음부터 다시 정리", Surface, TextMain, Modifier.fillMaxWidth()) { vm.resetDate(date) }
                Text("이 날짜에서 고른 남기기·지우기 표시가 모두 초기화돼요", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        } else {
            val item = list[idx]
            val next = list.subList(idx + 1, list.size).firstOrNull { decisions[it.id] == null }

            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                // 다음 장을 뒤에 미리 깔아 두어 넘길 때 바로 보이게 한다
                if (next != null) MediaView(next, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
                key(item.id) {
                    SwipeCard(
                        item,
                        onKeep = { vm.decide(item.id, Decision.KEEP) },
                        onDelete = { vm.decide(item.id, Decision.DELETE) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${timeLabel(item.takenAt)} · ${sizeLabel(item.size)}", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (item.kind == Kind.VIDEO) {
                    OutlinedButton(onClick = { onPlay(item) }) { Text("재생") }
                }
                if (item.groupId != -1L) {
                    Button(
                        onClick = { vm.screen = Screen.Compare(item.groupId, date) },
                        colors = ButtonDefaults.buttonColors(containerColor = TextMain, contentColor = Bg),
                    ) {
                        Text("비슷한 사진 ${vm.groupItems(item.groupId).size}장 비교", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                "왼쪽으로 밀면 지우기 · 오른쪽으로 밀면 남기기",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("지우기", Del, OnDel, Modifier.weight(1f)) { vm.decide(item.id, Decision.DELETE) }
                BigButton("되돌리기", Surface, TextMain, Modifier.weight(1f), enabled = vm.canUndo) { vm.undo() }
                BigButton("남기기", Keep, OnKeep, Modifier.weight(1f)) { vm.decide(item.id, Decision.KEEP) }
            }
        }
    }
}

@Composable
private fun SwipeCard(item: MediaItem, onKeep: () -> Unit, onDelete: () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    val keep by rememberUpdatedState(onKeep)
    val delete by rememberUpdatedState(onDelete)

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / width * 8f
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = offset.value
                            when {
                                v > width * 0.25f -> { offset.animateTo(width * 1.3f, tween(150)); keep() }
                                v < -width * 0.25f -> { offset.animateTo(-width * 1.3f, tween(150)); delete() }
                                else -> offset.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + dx) }
                }
            }
            .clip(RoundedCornerShape(18.dp)),
    ) {
        MediaView(item, Modifier.fillMaxSize())
        val p = (offset.value / (width * 0.25f)).coerceIn(-1f, 1f)
        if (p > 0f) Stamp("남기기", Keep, OnKeep, Modifier.align(Alignment.TopStart).padding(20.dp).alpha(p))
        if (p < 0f) Stamp("지우기", Del, OnDel, Modifier.align(Alignment.TopEnd).padding(20.dp).alpha(-p))
    }
}

@Composable
private fun Stamp(text: String, bg: Color, fg: Color, modifier: Modifier) {
    Text(
        text,
        modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold,
    )
}
