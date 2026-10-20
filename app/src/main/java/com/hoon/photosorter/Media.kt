package com.hoon.photosorter

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Kind { IMAGE, VIDEO }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val kind: Kind,
    val takenAt: Long,
    val date: LocalDate,
    val size: Long,
    val durationMs: Long,
    val isScreenshot: Boolean,
    /** 비슷한 사진 묶음 번호. 묶음이 없으면 -1 */
    val groupId: Long = -1L,
)

/** 이 간격(밀리초) 안에 연달아 찍은 사진을 한 묶음(비슷한 사진/연사)으로 본다 */
const val SIMILAR_GAP_MS = 1_000L

object MediaRepo {

    fun load(context: Context): List<MediaItem> {
        val zone = ZoneId.systemDefault()
        val result = ArrayList<MediaItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection, selection, args, null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val kind = if (c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) Kind.VIDEO else Kind.IMAGE
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)
                val where = ((c.getString(iPath) ?: "") + "/" + (c.getString(iBucket) ?: "")).lowercase()
                val base = if (kind == Kind.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                result += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    kind = kind,
                    takenAt = taken,
                    date = Instant.ofEpochMilli(taken).atZone(zone).toLocalDate(),
                    size = c.getLong(iSize),
                    durationMs = c.getLong(iDur),
                    isScreenshot = "screenshot" in where || "스크린샷" in where ||
                        "screenrecord" in where || "screen record" in where,
                )
            }
        }
        return assignGroups(result)
    }

    /** 촬영 시각만 보고 묶는다: 앞 사진과 SIMILAR_GAP_MS 이내면 같은 묶음 */
    private fun assignGroups(items: List<MediaItem>): List<MediaItem> {
        val images = items.filter { it.kind == Kind.IMAGE }.sortedBy { it.takenAt }
        val groupOf = HashMap<Long, Long>()
        var start = 0
        for (i in 1..images.size) {
            if (i == images.size || images[i].takenAt - images[i - 1].takenAt > SIMILAR_GAP_MS) {
                if (i - start >= 2) {
                    for (j in start until i) groupOf[images[j].id] = images[start].id
                }
                start = i
            }
        }
        return items.map { it.copy(groupId = groupOf[it.id] ?: -1L) }
    }
}
