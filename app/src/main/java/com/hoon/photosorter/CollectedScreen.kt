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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 모으기 완료: 다른 앱에서 'Swik 이름' 앨범을 열면 된다는 안내와 자동 비우기 */
@Composable
fun CollectedScreen(vm: AppViewModel, folderId: Long) {
    val back: () -> Unit = { vm.screen = Screen.Basket(folderId) }
    BackHandler(onBack = back)
    val f = vm.folder(folderId)
    if (f == null) {
        LaunchedEffect(Unit) { vm.screen = Screen.Home }
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Swik ${f.name}", null, back)

        Column(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(f.bg.copy(alpha = 0.35f)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (f.copies.isNotEmpty()) {
                    Text("${f.copies.size}장을 모았어요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = f.fg)
                    Text(
                        "인스타그램, 카카오톡, 인화 앱 같은 곳에서 사진을 고를 때 ‘Swik ${f.name}’ 앨범을 열면 순서대로 들어 있어요.",
                        fontSize = 15.sp, color = TextSub, lineHeight = 23.sp,
                    )
                    Text("원본 사진은 그대로 있어요. 이 앨범엔 사본만 있어요.", fontSize = 13.sp, color = Muted)
                } else {
                    Text("모아 둔 사진이 없어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = f.fg)
                    Text("바구니에서 ‘폴더로 모으기’를 누르면 여기에 모여요.", fontSize = 14.sp, color = TextSub)
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("이 폴더 자동으로 비우기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("사본만 지워져요", fontSize = 13.sp, color = Muted)
                }
                ClearOptionRow(f.clearHours) { vm.setClearHours(f.id, it) }
                val note = when {
                    f.clearHours == 0 -> "자동으로 비우지 않아요. 다 올리면 아래 버튼으로 비워 주세요."
                    f.clearAt == 0L -> "모으고 나서 ${ClearOptions.first { it.first == f.clearHours }.second} 뒤에 비워져요."
                    else -> "${whenLabel(f.clearAt)}에 비워져요 · ${remainLabel(f.clearAt - now)} 남음"
                }
                Text(
                    note, fontSize = 14.sp, color = TextMain,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Track.copy(alpha = 0.6f)).padding(12.dp),
                )
            }

            Text(
                "구글 포토·삼성 클라우드가 Swik 폴더까지 백업하지 않도록, 처음 한 번만 백업 설정을 확인해 주세요.",
                fontSize = 12.sp, color = Muted, lineHeight = 18.sp,
            )
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("다 올렸어요, 지금 비우기", Del, OnDel, Modifier.fillMaxWidth(), enabled = f.copies.isNotEmpty()) { confirm = true }
            BigButton("더 골라서 담기", Surface, TextMain, Modifier.fillMaxWidth()) {
                vm.pickMode = true
                vm.screen = Screen.Home
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("‘Swik ${f.name}’을 비울까요?") },
            text = { Text("사본 ${f.copies.size}장을 지우고 바구니도 비워요. 원본 사진은 그대로 있어요.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.clearFolder(f.id) }) {
                    Text("비우기", color = OnDel, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소", color = TextSub) } },
        )
    }
}

/** 자동 비우기 시간 고르기: 1시간 / 12시간 / 1일 / 1주일 / 안 함 */
@Composable
fun ClearOptionRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClearOptions.forEach { (hours, label) ->
            val on = hours == selected
            val shape = RoundedCornerShape(12.dp)
            var m = Modifier.height(44.dp).clip(shape).background(if (on) TextMain else Surface)
            if (!on) m = m.border(1.dp, Track, shape)
            Box(m.clickable { onSelect(hours) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 14.sp, color = if (on) Bg else TextMain, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

private val whenFmt = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN)

fun whenLabel(ms: Long): String = whenFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun remainLabel(ms: Long): String {
    if (ms <= 60_000) return "곧"
    val h = ms / 3_600_000
    val m = ms % 3_600_000 / 60_000
    return when {
        h >= 24 -> "${h / 24}일 ${h % 24}시간"
        h > 0 -> "${h}시간 ${m}분"
        else -> "${m}분"
    }
}
