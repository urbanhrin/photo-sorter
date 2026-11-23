package com.hoon.photosorter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 새 폴더 편집 창을 연다. 안 쓴 색부터 고른다 */
fun AppViewModel.newFolder() {
    val used = folders.map { it.color }.toSet()
    val color = FolderColors.indices.firstOrNull { it !in used } ?: 0
    editingFolder = PickFolder(-1L, "", color, photoOnly = false, clearHours = 24)
}

@Composable
fun FoldersScreen(vm: AppViewModel) {
    val back: () -> Unit = { vm.screen = Screen.Home }
    BackHandler(onBack = back)
    var deleting by remember { mutableStateOf<PickFolder?>(null) }
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize()) {
        TopBar("폴더 관리", "${MAX_FOLDERS}개까지 만들 수 있어요 · 같은 사진을 여러 폴더에 담아도 돼요", back)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            vm.folders.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(f.bg))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val status = when {
                            f.copies.isNotEmpty() && f.clearAt > 0 -> "${f.copies.size}장 모아둠 · ${remainLabel(f.clearAt - now)} 후 비움"
                            f.copies.isNotEmpty() -> "${f.copies.size}장 모아둠 · 자동으로 비우지 않음"
                            f.items.isNotEmpty() -> "${f.items.size}장 담는 중"
                            else -> "비어 있음"
                        } + if (f.photoOnly) " · 사진만" else ""
                        Text(status, fontSize = 13.sp, color = Muted)
                    }
                    IconButton(onClick = { vm.editingFolder = f }) {
                        Icon(Icons.Filled.Edit, contentDescription = "${f.name} 편집", tint = TextSub)
                    }
                    IconButton(onClick = { deleting = f }) {
                        Icon(Icons.Filled.Delete, contentDescription = "${f.name} 삭제", tint = TextSub)
                    }
                }
            }
            if (vm.folders.size < MAX_FOLDERS) {
                OutlinedButton(
                    onClick = { vm.newFolder() },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Track),
                ) {
                    Text("+ 새 폴더 만들기 (${MAX_FOLDERS - vm.folders.size}개 더 만들 수 있어요)", color = TextSub, fontSize = 15.sp)
                }
            }
        }
    }

    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("‘${f.name}’ 폴더를 지울까요?") },
            text = { Text("바구니와 모아 둔 사본이 함께 지워져요. 원본 사진은 그대로 있어요.") },
            confirmButton = {
                TextButton(onClick = { deleting = null; vm.deleteFolder(f.id) }) {
                    Text("지우기", color = OnDel, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소", color = TextSub) } },
        )
    }
}

/** 폴더 만들기/편집 창: 이름, 색, 사진만 담기, 자동 비우기 */
@Composable
fun FolderEditorDialog(vm: AppViewModel, f: PickFolder) {
    val isNew = f.id == -1L
    var name by remember(f.id) { mutableStateOf(f.name) }
    var color by remember(f.id) { mutableIntStateOf(f.color) }
    var photoOnly by remember(f.id) { mutableStateOf(f.photoOnly) }
    var hours by remember(f.id) { mutableIntStateOf(f.clearHours) }

    AlertDialog(
        onDismissRequest = { vm.editingFolder = null },
        containerColor = Surface,
        title = { Text(if (isNew) "새 폴더" else "폴더 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("이름") },
                    placeholder = { Text("예: 인스타, 인화") },
                    supportingText = { Text("다른 앱에서 ‘Swik ${name.ifBlank { "이름" }}’ 앨범으로 보여요") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("색", fontSize = 13.sp, color = TextSub)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FolderColors.forEachIndexed { i, (bg, fg) ->
                            val shape = RoundedCornerShape(12.dp)
                            var m = Modifier.size(40.dp).clip(shape).background(bg)
                            if (i == color) m = m.border(3.dp, fg, shape)
                            Box(m.clickable { color = i })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("사진만 담기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("동영상은 이 폴더에 담기지 않아요 (인화용)", fontSize = 13.sp, color = Muted)
                    }
                    Switch(
                        checked = photoOnly, onCheckedChange = { photoOnly = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TextMain, checkedThumbColor = Bg),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("모은 뒤 자동으로 비우기", fontSize = 13.sp, color = TextSub)
                    ClearOptionRow(hours) { hours = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.saveFolder(f.id, name, color, photoOnly, hours) },
                enabled = name.isNotBlank(),
            ) { Text(if (isNew) "만들기" else "저장", fontWeight = FontWeight.Bold, color = TextMain) }
        },
        dismissButton = { TextButton(onClick = { vm.editingFolder = null }) { Text("취소", color = TextSub) } },
    )
}
