package com.hoon.photosorter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

private val DoneBg = Color(0xFFDFF1E7)
private val PartBg = Color(0xFFDCE8F8)
private val PartBar = Color(0xFF6F8FBF)
private val PartTrack = Color(0xFFC3D6EF)
private val TodoBorder = Color(0xFFD9D1E2)
private val TodoBg = Color(0xFFF4F0F7)
private val SundayColor = Color(0xFFB4533F)
private val EmptyDay = Color(0xFF9C95A6)

private enum class DayState { NONE, TODO, PART, DONE }

/** 달력: 날짜마다 그날 찍은 장수와 정리 상태(완료/진행 중/시작 전)를 보여준다 */
@Composable
fun CalendarView(
    vm: AppViewModel,
    byDate: Map<LocalDate, List<MediaItem>>,
    decisions: Map<Long, Decision>,
    modifier: Modifier = Modifier,
) {
    val start = vm.lastDate ?: byDate.keys.maxOrNull() ?: LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(start)) }
    val today = LocalDate.now()

    fun stateOf(list: List<MediaItem>?): DayState {
        if (list.isNullOrEmpty()) return DayState.NONE
        val done = list.count { decisions[it.id] != null }
        return when (done) {
            0 -> DayState.TODO
            list.size -> DayState.DONE
            else -> DayState.PART
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = TextMain)
            }
            Text("${month.year}년 ${month.monthValue}월", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = TextMain)
            }
        }

        // 요일 (일요일 시작)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, name ->
                Text(
                    name, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp,
                    color = if (i == 0) SundayColor else Muted,
                )
            }
        }

        val first = month.atDay(1)
        val offset = first.dayOfWeek.value % 7 // 일요일 = 0
        val rows = (offset + month.lengthOfMonth() + 6) / 7
        Column(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - offset + 1
                        if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f).height(62.dp))
                        } else {
                            val date = month.atDay(dayNum)
                            val list = byDate[date]
                            DayCell(
                                dayNum, list, stateOf(list), decisions,
                                isToday = date == today, isSunday = c == 0,
                                modifier = Modifier.weight(1f),
                            ) { vm.openDate(date) }
                        }
                    }
                }
            }
        }

        // 범례
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Legend("완료", DoneBg)
            Legend("진행 중", PartBg)
            Legend("시작 전", Surface, border = TodoBorder)
            Legend("오늘", Color.Transparent, border = TextMain)
        }

        // 이번 달 요약
        val inMonth = byDate.filterKeys { YearMonth.from(it) == month }
        val states = inMonth.values.map { stateOf(it) }
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                val what = if (vm.kind == Kind.IMAGE) "사진" else "동영상"
                Text("${month.monthValue}월 $what ${inMonth.values.sumOf { it.size }}개", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("찍은 날 ${inMonth.size}일", fontSize = 13.sp, color = Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("완료", states.count { it == DayState.DONE }, DoneBg, Done, Modifier.weight(1f))
                Stat("진행 중", states.count { it == DayState.PART }, PartBg, OnKeep, Modifier.weight(1f))
                Stat("시작 전", states.count { it == DayState.TODO }, TodoBg, TextMain, Modifier.weight(1f))
            }
        }
        Text(
            "날짜를 누르면 그날 사진부터 바로 정리해요",
            Modifier.fillMaxWidth().padding(top = 14.dp),
            textAlign = TextAlign.Center, fontSize = 12.sp, color = Muted,
        )
    }
}

@Composable
private fun DayCell(
    day: Int,
    list: List<MediaItem>?,
    state: DayState,
    decisions: Map<Long, Decision>,
    isToday: Boolean,
    isSunday: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    var m = modifier.height(62.dp).clip(shape)
    m = when (state) {
        DayState.DONE -> m.background(DoneBg)
        DayState.PART -> m.background(PartBg)
        DayState.TODO -> m.background(Surface).border(1.dp, TodoBorder, shape)
        DayState.NONE -> m
    }
    if (isToday) m = m.border(2.dp, TextMain, shape)
    if (!list.isNullOrEmpty()) m = m.clickable(onClick = onOpen)

    Column(m.padding(horizontal = 6.dp, vertical = 5.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(
            "$day", fontSize = 12.sp,
            color = when {
                state == DayState.NONE -> EmptyDay
                isSunday -> SundayColor
                else -> TextSub
            },
        )
        if (!list.isNullOrEmpty()) {
            Text(
                "${list.size}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = when (state) {
                    DayState.DONE -> Done
                    DayState.PART -> OnKeep
                    else -> TextMain
                },
            )
        } else {
            Spacer(Modifier.height(1.dp))
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (state == DayState.PART) PartTrack else Color.Transparent)) {
            if (state == DayState.PART && list != null) {
                val ratio = list.count { decisions[it.id] != null }.toFloat() / list.size
                Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(PartBar))
            }
        }
    }
}

@Composable
private fun Legend(label: String, fill: Color, border: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val shape = RoundedCornerShape(4.dp)
        var m = Modifier.size(12.dp).clip(shape).background(fill)
        if (border != null) m = m.border(if (fill == Color.Transparent) 2.dp else 1.dp, border, shape)
        Box(m)
        Text(label, fontSize = 12.sp, color = TextSub)
    }
}

@Composable
private fun Stat(label: String, days: Int, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, color = fg)
        Text("${days}일", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
