package com.hoon.photosorter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 앱 휴지통에 들어간 항목. 실제 파일은 앱 전용 폴더의 trash/<name> 에 있다 */
data class TrashEntry(
    val name: String,
    val originalPath: String,
    val deletedAt: Long,
    val isVideo: Boolean,
    val size: Long,
)

const val TRASH_KEEP_MS = 30L * 24 * 60 * 60 * 1000

/**
 * 안드로이드 8 전용 앱 휴지통.
 * 지울 때 원본 파일을 앱 전용 폴더로 옮기고(같은 저장소라 복사 없이 이동), 되살릴 때 원래 위치로 되돌린다.
 * 앱을 삭제하면 이 폴더도 함께 지워진다.
 */
object TrashRepo {

    fun dir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "trash").apply { mkdirs() }

    fun file(ctx: Context, e: TrashEntry) = File(dir(ctx), e.name)

    private fun index(ctx: Context) = File(dir(ctx), "index.json")

    @Synchronized
    fun list(ctx: Context): List<TrashEntry> {
        val f = index(ctx)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TrashEntry(
                    o.getString("name"), o.getString("path"), o.getLong("at"),
                    o.getBoolean("video"), o.getLong("size"),
                )
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.deletedAt }
    }

    private fun save(ctx: Context, list: List<TrashEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("path", it.originalPath).put("at", it.deletedAt)
                    .put("video", it.isVideo).put("size", it.size),
            )
        }
        index(ctx).writeText(arr.toString())
    }

    /** 휴지통으로 옮기는 데 성공한 항목의 id 를 돌려준다 */
    @Synchronized
    fun moveIn(ctx: Context, items: List<MediaItem>): Set<Long> {
        val entries = list(ctx).toMutableList()
        val moved = HashSet<Long>()
        val now = System.currentTimeMillis()
        for (item in items) {
            val src = File(item.path ?: continue)
            if (!src.exists()) continue
            val dst = File(dir(ctx), "${item.id}_${src.name}")
            if (!move(src, dst)) continue
            // 파일은 이미 옮겼으니 갤러리 목록에서만 지운다
            runCatching { ctx.contentResolver.delete(item.uri, null, null) }
            entries += TrashEntry(dst.name, src.path, now, item.kind == Kind.VIDEO, item.size)
            moved += item.id
        }
        save(ctx, entries)
        return moved
    }

    /** 원래 자리로 되돌리고 되돌린 파일 경로를 돌려준다. 같은 이름이 있으면 이름 뒤에 숫자를 붙인다 */
    @Synchronized
    fun restore(ctx: Context, e: TrashEntry): String? {
        val src = file(ctx, e)
        var target = File(e.originalPath)
        var n = 1
        while (target.exists()) {
            target = File(target.parentFile, "${File(e.originalPath).nameWithoutExtension} ($n).${File(e.originalPath).extension}")
            n++
        }
        target.parentFile?.mkdirs()
        val ok = src.exists() && move(src, target)
        save(ctx, list(ctx).filter { it.name != e.name })
        return if (ok) target.path else null
    }

    @Synchronized
    fun purge(ctx: Context, entries: List<TrashEntry>) {
        val names = entries.map { it.name }.toSet()
        entries.forEach { file(ctx, it).delete() }
        save(ctx, list(ctx).filter { it.name !in names })
    }

    fun purgeExpired(ctx: Context) {
        val cutoff = System.currentTimeMillis() - TRASH_KEEP_MS
        val old = list(ctx).filter { it.deletedAt < cutoff }
        if (old.isNotEmpty()) purge(ctx, old)
    }

    private fun move(src: File, dst: File): Boolean {
        if (src.renameTo(dst)) return true
        return runCatching {
            src.copyTo(dst, overwrite = true)
            if (!src.delete()) {
                dst.delete()
                false
            } else true
        }.getOrDefault(false)
    }
}
