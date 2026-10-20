package com.hoon.photosorter

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class Decision { KEEP, DELETE }
enum class Filter { ALL, SCREENSHOT, BURST }

sealed interface Screen {
    data object Home : Screen
    data class Sort(val date: LocalDate) : Screen
    data class Compare(val groupId: Long, val from: LocalDate) : Screen
    data object Review : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // 고른 결과와 마지막으로 보던 날짜는 휴대폰 안(SharedPreferences)에만 저장
    private val prefs = app.getSharedPreferences("sorter", Context.MODE_PRIVATE)

    var items by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var decisions by mutableStateOf(readDecisions()); private set
    var kind by mutableStateOf(Kind.IMAGE); private set
    var filter by mutableStateOf(Filter.ALL)
    var screen by mutableStateOf<Screen>(Screen.Home)
    var lastDate by mutableStateOf(prefs.getString(KEY_LAST, null)?.let { LocalDate.parse(it) }); private set
    var canUndo by mutableStateOf(false); private set

    private val undoStack = ArrayDeque<Pair<Long, Decision?>>()
    private var trashing: Set<Long> = emptySet()

    fun reload() {
        viewModelScope.launch {
            items = withContext(Dispatchers.IO) { MediaRepo.load(getApplication()) }
            loaded = true
        }
    }

    fun selectKind(k: Kind) {
        kind = k
        if (k == Kind.VIDEO && filter == Filter.BURST) filter = Filter.ALL
    }

    fun visible(): List<MediaItem> = items.filter {
        it.kind == kind && when (filter) {
            Filter.ALL -> true
            Filter.SCREENSHOT -> it.isScreenshot
            Filter.BURST -> it.groupId != -1L
        }
    }

    fun itemsFor(date: LocalDate) = visible().filter { it.date == date }.sortedBy { it.takenAt }

    fun groupItems(groupId: Long) = items.filter { it.groupId == groupId }.sortedBy { it.takenAt }

    fun pendingDeletes() = items.filter { decisions[it.id] == Decision.DELETE }

    fun openDate(date: LocalDate) {
        lastDate = date
        prefs.edit().putString(KEY_LAST, date.toString()).apply()
        undoStack.clear()
        canUndo = false
        screen = Screen.Sort(date)
    }

    fun decide(id: Long, d: Decision) {
        undoStack.addLast(id to decisions[id])
        canUndo = true
        update(mapOf(id to d))
    }

    /** 이미 정리한 날짜를 처음부터 다시 고르도록 그 날짜의 선택을 모두 지운다 */
    fun resetDate(date: LocalDate) {
        undoStack.clear()
        canUndo = false
        update(itemsFor(date).associate { it.id to null })
    }

    fun undo() {
        val (id, prev) = undoStack.removeLastOrNull() ?: return
        canUndo = undoStack.isNotEmpty()
        update(mapOf(id to prev))
    }

    /** null 은 '아직 안 고름'으로 되돌린다 */
    fun update(changes: Map<Long, Decision?>) {
        val m = decisions.toMutableMap()
        changes.forEach { (id, d) -> if (d == null) m.remove(id) else m[id] = d }
        decisions = m
        prefs.edit()
            .putStringSet(KEY_KEEP, m.filterValues { it == Decision.KEEP }.keys.map { it.toString() }.toSet())
            .putStringSet(KEY_DEL, m.filterValues { it == Decision.DELETE }.keys.map { it.toString() }.toSet())
            .apply()
    }

    fun startTrash(): List<Uri> {
        val list = pendingDeletes()
        trashing = list.map { it.id }.toSet()
        return list.map { it.uri }
    }

    fun onTrashed() {
        items = items.filter { it.id !in trashing }
        update(trashing.associateWith { null })
        trashing = emptySet()
        screen = Screen.Home
    }

    private fun readDecisions(): Map<Long, Decision> {
        val m = HashMap<Long, Decision>()
        prefs.getStringSet(KEY_KEEP, emptySet())?.forEach { s -> s.toLongOrNull()?.let { m[it] = Decision.KEEP } }
        prefs.getStringSet(KEY_DEL, emptySet())?.forEach { s -> s.toLongOrNull()?.let { m[it] = Decision.DELETE } }
        return m
    }

    private companion object {
        const val KEY_KEEP = "keep"
        const val KEY_DEL = "delete"
        const val KEY_LAST = "lastDate"
    }
}
