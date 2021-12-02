package moe.mmf.anni_amp.repo

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import moe.mmf.anni_amp.repo.entities.Album
import moe.mmf.anni_amp.repo.entities.Track
import org.eclipse.jgit.api.Git
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.io.File
import java.time.LocalDate
import kotlin.io.path.Path

class RepoHelper(private var name: String, private var repo: String, root: File) {
    private var root: File = File(root, name)

    fun needInitialize(): Boolean {
        return !root.exists()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun initialize(db: RepoDatabase) {
        Git.cloneRepository()
            .setURI(repo)
            .setDirectory(root)
            .call()

        val albumRoot = File(root, "album")
        val albumList = mutableListOf<Album>()
        val tracksList = mutableListOf<Track>()
        albumRoot.walkTopDown()
            .maxDepth(1)
            .forEach {
                // ignore root
                if (it != albumRoot) {
                    val result: TomlParseResult = Toml.parse(Path(it.path))
                    if (!result.hasErrors()) {
                        // only apply albums with no error
                        val albumTitle = result.getString("album.title")!!
                        val albumArtist = result.getString("album.artist")!!
                        val albumType = result.getString("album.type")!!
                        val albumCatalog = result.getString("album.catalog")!!

                        var albumReleaseDate = result.get("album.date")!!
                        when (albumReleaseDate) {
                            is String -> {}
                            is TomlTable -> {
                                val year = albumReleaseDate.getLong("year")!!
                                var month = albumReleaseDate.getLong("month")
                                if (month == null) {
                                    month = 0
                                }
                                var day = albumReleaseDate.getLong("day")
                                if (day == null) {
                                    day = 0
                                }
                                albumReleaseDate = "${year}-${month}-${day}"
                            }
                            is LocalDate -> {
                                albumReleaseDate = albumReleaseDate.toString()
                            }
                            else -> {
                                // fallback, should not happen
                                albumReleaseDate = "2021"
                            }
                        }

                        // insert album
                        albumList.add(
                            Album(
                                0,
                                albumTitle,
                                albumCatalog,
                                albumArtist,
                                albumReleaseDate.toString(),
                                null
                            )
                        )
                        val discs = result.getArray("discs")!!
                        for (i in 0 until discs.size()) {
                            val disc = discs.getTable(0)
                            val discCatalog = disc.getString("catalog")!!
                            val discTitle = disc.getString("title") { albumTitle }
                            val discArtist = disc.getString("artist") { albumArtist }
                            val discType = disc.getString("type") { albumType }
                            if (discs.size() > 1) {
                                albumList.add(
                                    Album(
                                        0,
                                        discTitle,
                                        discCatalog,
                                        discArtist,
                                        albumReleaseDate.toString(),
                                        albumCatalog
                                    )
                                )
                            }

                            val tracks = disc.getArray("tracks")!!
                            for (j in 0 until tracks.size()) {
                                val track = tracks.getTable(j)
                                val trackTitle = track.getString("title")!!
                                val trackArtist = track.getString("artist") { discArtist }
                                val trackType = track.getString("type") { discType }
                                tracksList.add(
                                    Track(
                                        0,
                                        discCatalog,
                                        j + 1,
                                        trackTitle,
                                        trackArtist,
                                        trackType
                                    )
                                )
                            }
                        }
                    }
                }
            }
        db.albumDao().insertAll(albumList)
        db.trackDao().insertAll(tracksList)
        Log.d("anni", "database construction finished")
    }

    fun pull(db: RepoDatabase) {
        var git = Git.open(root)
        git.pull()
    }
}
