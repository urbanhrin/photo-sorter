package com.hoon.photosorter

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** 고르기 폴더 색: (바탕, 글자) */
val FolderColors = listOf(
    Color(0xFFF7D774) to Color(0xFF4A3A05), // 노랑
    Color(0xFFA8DCC0) to Color(0xFF1F4D36), // 민트
    Color(0xFFCDBDEB) to Color(0xFF3B2A66), // 라벤더
    Color(0xFFA7C7F2) to Color(0xFF1C3354), // 하늘
    Color(0xFFF6B3A4) to Color(0xFF5A2117), // 살구
)

/** 자동 비우기 선택지(시간). 0 = 안 함 */
val ClearOptions = listOf(1 to "1시간", 12 to "12시간", 24 to "1일", 168 to "1주일", 0 to "안 함")

const val MAX_FOLDERS = 3

/** 다른 앱에서 보이는 사본 폴더: Pictures/Swik <이름> */
const val SWIK_DIR_PREFIX = "Swik "

data class PickFolder(
    val id: Long,
    val name: String,
    val color: Int,
    val photoOnly: Boolean,
    /** 모은 뒤 자동으로 비우기까지의 시간. 0 = 안 함 */
    val clearHours: Int,
    /** 바구니: 올릴 순서대로 */
    val items: List<Long> = emptyList(),
    /** 이 폴더 기준으로 이미 본(담았거나 넘긴) 사진 */
    val seen: Set<Long> = emptySet(),
    /** 모으기로 만든 사본의 주소 */
    val copies: List<String> = emptyList(),
    val collectedAt: Long = 0L,
    /** 사용자가 순서를 직접 바꿨는지. 아니면 찍은 순서를 유지한다 */
    val reordered: Boolean = false,
) {
    val bg get() = FolderColors[color % FolderColors.size].first
    val fg get() = FolderColors[color % FolderColors.size].second
    val clearAt get() = if (copies.isEmpty() || clearHours == 0) 0L else collectedAt + clearHours * 3_600_000L
}

/**
 * 고르기 폴더 저장소. 전부 휴대폰 안(SharedPreferences)에만 저장한다.
 * 모으기는 원본을 건드리지 않고 Pictures/Swik <이름> 에 사본을 만들고, 비우기는 그 사본만 지운다.
 */
object PickRepo {

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pick", Context.MODE_PRIVATE)

    @Synchronized
    fun load(ctx: Context): List<PickFolder> {
        val raw = prefs(ctx).getString("folders", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PickFolder(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    color = o.optInt("color"),
                    photoOnly = o.optBoolean("photoOnly"),
                    clearHours = o.optInt("clearHours", 24),
                    items = o.optJSONArray("items").longs(),
                    seen = o.optJSONArray("seen").longs().toSet(),
                    copies = o.optJSONArray("copies").strings(),
                    collectedAt = o.optLong("collectedAt"),
                    reordered = o.optBoolean("reordered"),
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(ctx: Context, list: List<PickFolder>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id).put("name", f.name).put("color", f.color)
                    .put("photoOnly", f.photoOnly).put("clearHours", f.clearHours)
                    .put("items", JSONArray(f.items)).put("seen", JSONArray(f.seen.toList()))
                    .put("copies", JSONArray(f.copies)).put("collectedAt", f.collectedAt)
                    .put("reordered", f.reordered),
            )
        }
        prefs(ctx).edit().putString("folders", arr.toString()).apply()
    }

    fun activeId(ctx: Context) = prefs(ctx).getLong("active", -1L)
    fun setActive(ctx: Context, id: Long) = prefs(ctx).edit().putLong("active", id).apply()

    /** 이미 올린 사진 기록: 사진 id → "폴더이름|날짜" (사본을 비울 때 남긴다) */
    @Synchronized
    fun history(ctx: Context): Map<Long, String> {
        val raw = prefs(ctx).getString("history", null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associate { it.toLong() to o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    private fun addHistory(ctx: Context, ids: List<Long>, folderName: String) {
        val o = runCatching { JSONObject(prefs(ctx).getString("history", null) ?: "{}") }.getOrDefault(JSONObject())
        val tag = "$folderName|${LocalDate.now()}"
        ids.forEach { o.put(it.toString(), tag) }
        prefs(ctx).edit().putString("history", o.toString()).apply()
    }

    /** 바구니 사진을 순서대로 사본으로 만든다. 예전 사본이 있으면 먼저 지운다 */
    fun collect(ctx: Context, folder: PickFolder, byId: Map<Long, MediaItem>): PickFolder {
        deleteCopies(ctx, folder.copies)
        val dir = SWIK_DIR_PREFIX + safeName(folder.name)
        val copies = folder.items.mapNotNull { byId[it] }
            .mapIndexedNotNull { i, item -> copyOne(ctx, item, dir, i + 1)?.toString() }
        val done = folder.withCopies(copies, System.currentTimeMillis())
        update(ctx, done)
        schedule(ctx, done)
        return done
    }

    /** 사본만 지운다. clearBasket 이면 바구니도 비우고, 올린 기록을 남긴다 */
    fun clear(ctx: Context, folder: PickFolder, clearBasket: Boolean = true): PickFolder {
        deleteCopies(ctx, folder.copies)
        if (folder.copies.isNotEmpty()) addHistory(ctx, folder.items, folder.name)
        val done = folder.copy(
            copies = emptyList(), collectedAt = 0L,
            items = if (clearBasket) emptyList() else folder.items,
            reordered = if (clearBasket) false else folder.reordered,
        )
        update(ctx, done)
        schedule(ctx, done)
        return done
    }

    /** 자동 비우기 시간이 지난 폴더를 비운다 (앱을 열 때와 예약 작업에서 부른다) */
    fun runDue(ctx: Context) {
        val now = System.currentTimeMillis()
        load(ctx).filter { it.clearAt in 1..now }.forEach { clear(ctx, it) }
    }

    fun schedule(ctx: Context, folder: PickFolder) {
        val wm = WorkManager.getInstance(ctx)
        val name = "swik-clear-${folder.id}"
        if (folder.clearAt == 0L) {
            wm.cancelUniqueWork(name)
            return
        }
        val delay = (folder.clearAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val req = OneTimeWorkRequestBuilder<ClearWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
    }

    @Synchronized
    private fun update(ctx: Context, folder: PickFolder) {
        save(ctx, load(ctx).map { if (it.id == folder.id) folder else it })
    }

    private fun PickFolder.withCopies(list: List<String>, at: Long) = copy(copies = list, collectedAt = if (list.isEmpty()) 0L else at)

    private fun safeName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "폴더" }

    private fun deleteCopies(ctx: Context, copies: List<String>) {
        copies.forEach { s -> runCatching { ctx.contentResolver.delete(Uri.parse(s), null, null) } }
    }

    @Suppress("DEPRECATION")
    private fun copyOne(ctx: Context, item: MediaItem, dir: String, index: Int): Uri? {
        val cr = ctx.contentResolver
        val video = item.kind == Kind.VIDEO
        var name = if (video) "video.mp4" else "photo.jpg"
        var mime = if (video) "video/mp4" else "image/jpeg"
        runCatching {
            cr.query(item.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.let { n -> name = n }
                    it.getString(1)?.let { m -> mime = m }
                }
            }
        }
        // 이름 앞 번호로 올릴 순서를 지킨다 (이름순으로 보여주는 앱에서)
        val fileName = "%03d_%s".format(index, name)
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.Images.ImageColumns.DATE_TAKEN, item.takenAt)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$dir")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), dir)
                folder.mkdirs()
                put(MediaStore.MediaColumns.DATA, File(folder, fileName).path)
            }
        }
        val uri = runCatching { cr.insert(collection, values) }.getOrNull() ?: return null
        return try {
            cr.openInputStream(item.uri)!!.use { input ->
                cr.openOutputStream(uri)!!.use { output -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            uri
        } catch (e: Exception) {
            runCatching { cr.delete(uri, null, null) }
            null
        }
    }

    private fun JSONArray?.longs(): List<Long> = if (this == null) emptyList() else (0 until length()).map { getLong(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
}

/** 정해 둔 시간이 되면 사본 폴더를 비우는 예약 작업 (인터넷 없이 휴대폰 안에서만 돈다) */
class ClearWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        PickRepo.runDue(applicationContext)
        return Result.success()
    }
}
