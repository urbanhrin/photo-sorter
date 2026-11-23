package com.hoon.photosorter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/** 폴더별 바구니. 꾹 눌러 끌면 순서를 바꾸고, 누르면 뺀다 */
@Composable
fun BasketScreen(vm: AppViewModel, folderId: Long) {
    val goHome: () -> Unit = { vm.screen = Screen.Home }
    BackHandler(onBack = goHome)
    val f = vm.folder(folderId)
    if (f == null) {
        LaunchedEffect(Unit) { goHome() }
        return
    }
    val byId = remember(vm.items) { vm.items.associateBy { it.id } }
    val list = f.items.mapNotNull { byId[it] }

    Column(Modifier.fillMaxSize()) {
        TopBar("바구니", null, goHome)

        // 폴더 탭
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            vm.folders.forEach { o ->
                val selected = o.id == f.id
                Column(
                    Modifier.weight(1f).clickable { vm.screen = Screen.Basket(o.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier.height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(o.bg))
                        Text(
                            "${o.name} ${o.items.size}", fontSize = 15.sp,
                            color = if (selected) TextMain else Muted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (selected) o.fg else Track))
                }
            }
        }

        if (f.copies.isNotEmpty()) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(f.bg.copy(alpha = 0.35f))
                    .clickable { vm.screen = Screen.Collected(f.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‘Swik ${f.name}’에 ${f.copies.size}장 모아둠", color = f.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("보기 ›", color = f.fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        val range = if (list.isEmpty()) "" else {
            val first = list.minOf { it.date }
            val last = list.maxOf { it.date }
            (if (first == last) shortDate(first) else "${shortDate(first)} ~ ${shortDate(last)}") + " · ${sizeLabel(list.sumOf { it.size })} · "
        }
        Text(
            "$range${if (f.reordered) "정한 순서대로예요" else "찍은 순서대로 담겨요"}. 순서를 바꾸려면 꾹 눌러 끌고, 누르면 빼요.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("아직 담은 사진이 없어요", color = Muted)
            }
        } else {
            ReorderGrid(vm, f, list, Modifier.weight(1f))
        }

        BigButton(
            if (f.copies.isEmpty()) "‘Swik ${f.name}’ 폴더로 모으기" else "다시 모으기 (사본 새로 만들기)",
            TextMain, Bg, Modifier.fillMaxWidth().padding(16.dp),
            enabled = list.isNotEmpty(),
        ) { vm.collect(f.id) }
    }
}

@Composable
private fun ReorderGrid(vm: AppViewModel, f: PickFolder, list: List<MediaItem>, modifier: Modifier) {
    val state = rememberLazyGridState()
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun at(p: Offset, skip: Long? = null): LazyGridItemInfo? = state.layoutInfo.visibleItemsInfo.firstOrNull {
        it.key != skip && p.x >= it.offset.x && p.x < it.offset.x + it.size.width &&
            p.y >= it.offset.y && p.y < it.offset.y + it.size.height
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = modifier
            .pointerInput(f.id) {
                detectTapGestures(
                    onLongPress = { },
                    onTap = { p -> (at(p)?.key as? Long)?.let { vm.removeFromBasket(f.id, it) } },
                )
            }
            .pointerInput(f.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { p ->
                        dragId = at(p)?.key as? Long
                        dragOffset = Offset.Zero
                    },
                    onDragEnd = { dragId = null; dragOffset = Offset.Zero },
                    onDragCancel = { dragId = null; dragOffset = Offset.Zero },
                ) { change, amount ->
                    change.consume()
                    val id = dragId
                    val cur = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                    if (id != null && cur != null) {
                        dragOffset += amount
                        val center = Offset(
                            cur.offset.x + cur.size.width / 2f + dragOffset.x,
                            cur.offset.y + cur.size.height / 2f + dragOffset.y,
                        )
                        val target = at(center, skip = id)
                        if (target != null) {
                            // 자리를 바꾼 만큼 끌던 위치를 보정해서 손가락 밑에 그대로 둔다
                            dragOffset += Offset(
                                (cur.offset.x - target.offset.x).toFloat(),
                                (cur.offset.y - target.offset.y).toFloat(),
                            )
                            vm.moveInBasket(f.id, id, target.key as Long)
                        }
                    }
                }
            },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(list.size, key = { list[it].id }) { i ->
            val item = list[i]
            val dragging = item.id == dragId
            Box(
                Modifier.aspectRatio(1f)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        if (dragging) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 1.06f
                            scaleY = 1.06f
                        }
                    }
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                MediaView(item, Modifier.fillMaxSize(), crop = true)
                Box(
                    Modifier.padding(6.dp).size(24.dp).clip(CircleShape).background(f.bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = f.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (dragging) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.15f)))
            }
        }
    }
}

private fun shortDate(d: java.time.LocalDate) = "${d.monthValue}월 ${d.dayOfMonth}일"
