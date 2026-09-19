package lazisnu.sidokerto.data.repository

import lazisnu.sidokerto.data.model.Song

class MovieRepository(
    private val api: ApiSongRepository = ApiSongRepository()
) {
    suspend fun fetchMovies(): Result<List<Song>> = api.fetchSongs()
}

