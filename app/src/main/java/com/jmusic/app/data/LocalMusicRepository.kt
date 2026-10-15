package com.jmusic.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.jmusic.app.model.MediaTrack
import com.jmusic.app.model.TrackSource

class LocalMusicRepository(private val contentResolver: ContentResolver) {

    fun getLocalTracks(): List<MediaTrack> {
        val list = mutableListOf<MediaTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor: Cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val artworkUri = if (albumId > 0) Uri.parse("content://media/external/audio/albumart/$albumId") else null
                    val uri = ContentUris.withAppendedId(collection, id)

                    list.add(
                        MediaTrack(
                            id = id.toString(),
                            title = cursor.getString(titleColumn) ?: "Unknown track",
                            artist = cursor.getString(artistColumn)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                            album = cursor.getString(albumColumn) ?: "",
                            durationMs = cursor.getLong(durationColumn),
                            artworkUrl = artworkUri?.toString(),
                            localUri = uri,
                            source = TrackSource.LOCAL
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }
}
