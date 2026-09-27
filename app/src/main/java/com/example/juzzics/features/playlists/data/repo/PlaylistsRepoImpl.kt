package com.example.juzzics.features.playlists.data.repo

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.data.local.LikedSongsDao
import com.example.juzzics.features.playlists.data.local.PlaylistDao
import com.example.juzzics.features.playlists.data.local.PlaylistEntity
import com.example.juzzics.features.playlists.data.local.PlaylistWithSongs
import com.example.juzzics.features.playlists.data.local.toLikedSong
import com.example.juzzics.features.playlists.domain.model.LIKED_SONGS_PLAYLIST_ID
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.repo.PlaylistsRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlaylistsRepoImpl(
    private val dao: PlaylistDao,
    private val likedDao: LikedSongsDao,
) : PlaylistsRepo {

    /** "Liked songs" first, then your playlists */
    override fun observePlaylists(): Flow<List<PlaylistDomain>> =
        combine(dao.observePlaylists(), likedDao.observeAll()) { playlists, liked ->
            listOf(
                PlaylistDomain(
                    id = LIKED_SONGS_PLAYLIST_ID,
                    name = "Liked songs",
                    songs = liked.map { it.toDomain() }
                )
            ) + playlists.map { it.toDomain() }
        }

    override suspend fun createPlaylist(name: String?): Result<PlaylistDomain> = runCatching {
        val finalName = name?.trim()?.ifEmpty { null } ?: "Playlist #${dao.count() + 1}"
        val id = dao.insertPlaylist(
            PlaylistEntity(name = finalName, createdAt = System.currentTimeMillis())
        )
        PlaylistDomain(id = id, name = finalName)
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) = runCatching {
        if (playlistId == LIKED_SONGS_PLAYLIST_ID) error("Liked songs can't be renamed")
        dao.rename(playlistId, name.trim().ifEmpty { error("Name can't be empty") })
    }

    override suspend fun deletePlaylist(playlistId: Long) = runCatching {
        if (playlistId == LIKED_SONGS_PLAYLIST_ID) error("Liked songs can't be deleted")
        dao.deletePlaylist(playlistId)
    }

    override suspend fun addSongToPlaylist(playlistId: Long, song: MusicFileDomain) = runCatching {
        if (playlistId == LIKED_SONGS_PLAYLIST_ID) likedDao.like(song.toLikedSong(System.currentTimeMillis()))
        else dao.addSong(playlistId, song)
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = runCatching {
        if (playlistId == LIKED_SONGS_PLAYLIST_ID) likedDao.unlike(songId)
        else dao.removeSong(playlistId, songId)
    }

    /** Liked songs are always newest-first, so they aren't reordered */
    override suspend fun reorderSongs(playlistId: Long, songIds: List<Long>) = runCatching {
        if (playlistId != LIKED_SONGS_PLAYLIST_ID) dao.reorder(playlistId, songIds)
    }

    override fun observeLikedSongIds(): Flow<Set<Long>> = likedDao.observeIds().map { it.toSet() }

    override suspend fun toggleLike(song: MusicFileDomain) = runCatching {
        if (likedDao.isLiked(song.id)) likedDao.unlike(song.id)
        else likedDao.like(song.toLikedSong(System.currentTimeMillis()))
    }

    private fun PlaylistWithSongs.toDomain() = PlaylistDomain(
        id = playlist.id,
        name = playlist.name,
        songs = songs.sortedBy { it.position }.map { it.toDomain() }
    )
}
