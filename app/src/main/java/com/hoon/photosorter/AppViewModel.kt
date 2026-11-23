package com.hoon.photosorter

import android.app.Application
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger
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
    data object Trash : Screen
    data class Pick(val date: LocalDate) : Screen
    data class Basket(val folderId: Long) : Screen
    data class Collected(val folderId: Long) : Screen
    data object Folders : Screen
}

/** 시스템 휴지통이 없는 안드로이드 8~10(API 26~29)은 앱 자체 휴지통을 쓴다 */
val USE_APP_TRASH = Build.VERSION.SDK_INT <= 29

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // 고른 결과와 마지막으로 보던 날짜는 휴대폰 안(SharedPreferences)에만 저장
    private val prefs = app.getSharedPreferences("sorter", Context.MODE_PRIVATE)

    var items by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var decisions by mutableStateOf(readDecisions()); private set
    var kind by mutableStateOf(Kind.IMAGE); private set
    var filter by mutableStateOf(Filter.ALL)
    var calendarMode by mutableStateOf(false)
    var screen by mutableStateOf<Screen>(Screen.Home)
    var lastDate by mutableStateOf(prefs.getString(KEY_LAST, null)?.let { LocalDate.parse(it) }); private set
    var canUndo by mutableStateOf(false); private set
    var trash by mutableStateOf<List<TrashEntry>>(emptyList()); private set

    // ── 고르기 모드 ──
    var pickMode by mutableStateOf(false)
    var folders by mutableStateOf(PickRepo.load(app)); private set
    var activeFolderId by mutableStateOf(PickRepo.activeId(app)); private set
    var history by mutableStateOf(PickRepo.history(app)); private set
    var canUndoPick by mutableStateOf(false); private set
    /** 폴더 편집 창. id 가 -1 이면 새 폴더 */
    var editingFolder by mutableStateOf<PickFolder?>(null)
    /** 사본을 만들거나 지우는 동안 보여줄 문구 */
    var busy by mutableStateOf<String?>(null); private set
    private val pickUndo = ArrayDeque<Triple<Long, Long, Boolean>>() // 폴더, 사진, 담았는지

    private val undoStack = ArrayDeque<Pair<Long, Decision?>>()
    private var trashing: Set<Long> = emptySet()

    fun reload() {
        viewModelScope.launch {
            folders = withContext(Dispatchers.IO) {
                PickRepo.runDue(getApplication())
                PickRepo.load(getApplication())
            }
            history = PickRepo.history(getApplication())
            items = withContext(Dispatchers.IO) { MediaRepo.load(getApplication()) }
            if (USE_APP_TRASH) {
                trash = withContext(Dispatchers.IO) {
                    TrashRepo.purgeExpired(getApplication())
                    TrashRepo.list(getApplication())
                }
            }
            loaded = true
        }
    }

    /** 안드로이드 8~10: 지울 항목을 앱 휴지통으로 옮긴다 */
    fun moveToAppTrash() {
        val list = pendingDeletes()
        viewModelScope.launch {
            trashing = withContext(Dispatchers.IO) { TrashRepo.moveIn(getApplication(), list) }
            trash = withContext(Dispatchers.IO) { TrashRepo.list(getApplication()) }
            onTrashed()
        }
    }

    fun restore(entries: List<TrashEntry>) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val paths = withContext(Dispatchers.IO) { entries.mapNotNull { TrashRepo.restore(app, it) } }
            trash = withContext(Dispatchers.IO) { TrashRepo.list(app) }
            if (paths.isEmpty()) return@launch
            // 되돌린 파일을 갤러리에 다시 등록하고, 다 끝나면 목록을 새로 읽는다
            val left = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(app, paths.toTypedArray(), null) { _, _ ->
                if (left.decrementAndGet() == 0) viewModelScope.launch(Dispatchers.Main) { reload() }
            }
        }
    }

    fun emptyTrash(entries: List<TrashEntry>) {
        viewModelScope.launch {
            trash = withContext(Dispatchers.IO) {
                TrashRepo.purge(getApplication(), entries)
                TrashRepo.list(getApplication())
            }
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
        pickUndo.clear()
        canUndoPick = false
        screen = if (pickMode) Screen.Pick(date) else Screen.Sort(date)
    }

    fun activeFolder(): PickFolder? = folders.find { it.id == activeFolderId } ?: folders.firstOrNull()

    fun folder(id: Long) = folders.find { it.id == id }

    fun setActive(id: Long) {
        activeFolderId = id
        PickRepo.setActive(getApplication(), id)
    }

    /** 이 폴더에 담을 수 있는 그날 사진 (사진만 폴더면 동영상 제외) */
    fun pickList(date: LocalDate, f: PickFolder) =
        itemsFor(date).filter { !(f.photoOnly && it.kind == Kind.VIDEO) }

    fun pick(folderId: Long, mediaId: Long, add: Boolean) {
        pickUndo.addLast(Triple(folderId, mediaId, add))
        canUndoPick = true
        changeFolder(folderId) { f ->
            f.copy(seen = f.seen + mediaId, items = if (add && mediaId !in f.items) ordered(f, f.items + mediaId) else f.items)
        }
    }

    fun undoPick() {
        val (folderId, mediaId, added) = pickUndo.removeLastOrNull() ?: return
        canUndoPick = pickUndo.isNotEmpty()
        changeFolder(folderId) { f -> f.copy(seen = f.seen - mediaId, items = if (added) f.items - mediaId else f.items) }
    }

    /** 이 날짜를 이 폴더 기준으로 처음부터 다시 본다 (바구니는 그대로) */
    fun resetSeen(date: LocalDate, folderId: Long) {
        val ids = itemsFor(date).map { it.id }.toSet()
        pickUndo.clear()
        canUndoPick = false
        changeFolder(folderId) { it.copy(seen = it.seen - ids) }
    }

    fun removeFromBasket(folderId: Long, mediaId: Long) = changeFolder(folderId) { it.copy(items = it.items - mediaId) }

    /** 바구니에서 꾹 눌러 끌어 순서를 바꾼다 */
    fun moveInBasket(folderId: Long, fromId: Long, toId: Long) = changeFolder(folderId) { f ->
        val list = f.items.toMutableList()
        val from = list.indexOf(fromId)
        val to = list.indexOf(toId)
        if (from < 0 || to < 0) return@changeFolder f
        list.add(to, list.removeAt(from))
        f.copy(items = list, reordered = true)
    }

    fun saveFolder(id: Long, name: String, color: Int, photoOnly: Boolean, clearHours: Int) {
        val app = getApplication<Application>()
        val old = folder(id)
        if (old == null) {
            if (folders.size >= MAX_FOLDERS) return
            val f = PickFolder(System.currentTimeMillis(), name.trim().ifEmpty { "폴더" }, color, photoOnly, clearHours)
            folders = folders + f
            PickRepo.save(app, folders)
            setActive(f.id)
        } else {
            changeFolder(id) { it.copy(name = name.trim().ifEmpty { it.name }, color = color, photoOnly = photoOnly, clearHours = clearHours) }
            folder(id)?.let { PickRepo.schedule(app, it) }
        }
        editingFolder = null
    }

    fun setClearHours(folderId: Long, hours: Int) {
        changeFolder(folderId) { it.copy(clearHours = hours) }
        folder(folderId)?.let { PickRepo.schedule(getApplication(), it) }
    }

    fun deleteFolder(id: Long) {
        val f = folder(id) ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PickRepo.clear(app, f) }
            folders = folders.filter { it.id != id }
            PickRepo.save(app, folders)
            if (activeFolderId == id) folders.firstOrNull()?.let { setActive(it.id) }
        }
    }

    /** 바구니 사진을 'Swik 이름' 폴더에 사본으로 모은다 */
    fun collect(folderId: Long) {
        val f = folder(folderId) ?: return
        val app = getApplication<Application>()
        val byId = items.associateBy { it.id }
        busy = "${f.items.size}장을 모으는 중이에요"
        viewModelScope.launch {
            val done = withContext(Dispatchers.IO) { PickRepo.collect(app, f, byId) }
            folders = folders.map { if (it.id == folderId) done else it }
            busy = null
            screen = Screen.Collected(folderId)
        }
    }

    /** 사본만 지운다. 원본은 그대로 */
    fun clearFolder(folderId: Long) {
        val f = folder(folderId) ?: return
        val app = getApplication<Application>()
        busy = "비우는 중이에요"
        viewModelScope.launch {
            val done = withContext(Dispatchers.IO) { PickRepo.clear(app, f) }
            folders = folders.map { if (it.id == folderId) done else it }
            history = PickRepo.history(app)
            busy = null
            screen = Screen.Home
        }
    }

    private fun changeFolder(id: Long, change: (PickFolder) -> PickFolder) {
        folders = folders.map { if (it.id == id) change(it) else it }
        PickRepo.save(getApplication(), folders)
    }

    /** 순서를 직접 바꾸지 않았으면 찍은 순서로 맞춘다 */
    private fun ordered(f: PickFolder, ids: List<Long>): List<Long> {
        if (f.reordered) return ids
        val taken = items.associate { it.id to it.takenAt }
        return ids.sortedBy { taken[it] ?: Long.MAX_VALUE }
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
