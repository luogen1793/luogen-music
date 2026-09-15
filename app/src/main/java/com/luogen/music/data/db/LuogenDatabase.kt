package com.luogen.music.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** 播放历史（含播放次数统计，驱动 AI 推荐） */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val songId: Long,
    val songJson: String,
    val playCount: Int,
    val lastPlayedAt: Long,
)

/** 自定义封面覆盖（本地歌曲手动上传封面） */
@Entity(tableName = "custom_covers")
data class CustomCoverEntity(
    @PrimaryKey val songId: Long,
    val imagePath: String,
)

/** 已下载歌曲记录 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val songId: Long,
    val filePath: String,
    val songJson: String,
    val downloadedAt: Long,
)

/** 我的歌单（v3 起支持自定义头像 coverPath） */
@Entity(tableName = "my_playlists")
data class MyPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val songsJson: String,
    val createdAt: Long,
    val coverPath: String = "",
)

/** 我喜欢的歌曲（歌曲序列化快照，独立于第三方接口可用性） */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val songJson: String,
    val favoritedAt: Long,
)

@Dao
interface LuogenDao {
    @Query("SELECT * FROM play_history ORDER BY lastPlayedAt DESC")
    suspend fun history(): List<PlayHistoryEntity>

    @Query("SELECT * FROM play_history ORDER BY playCount DESC LIMIT :limit")
    suspend fun topHistory(limit: Int): List<PlayHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(e: PlayHistoryEntity)

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM custom_covers")
    suspend fun customCovers(): List<CustomCoverEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCover(e: CustomCoverEntity)

    @Query("DELETE FROM custom_covers WHERE songId=:id")
    suspend fun deleteCover(id: Long)

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    suspend fun downloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE songId=:id")
    suspend fun download(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(e: DownloadEntity)

    @Query("DELETE FROM downloads WHERE songId=:id")
    suspend fun deleteDownload(id: Long)

    @Query("SELECT * FROM my_playlists ORDER BY createdAt DESC")
    suspend fun playlists(): List<MyPlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaylist(e: MyPlaylistEntity): Long

    @Query("DELETE FROM my_playlists WHERE id=:id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM favorites ORDER BY favoritedAt DESC")
    suspend fun favorites(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE songId=:id")
    suspend fun favoriteById(id: Long): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(e: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId=:id")
    suspend fun deleteFavorite(id: Long)

    @Query("DELETE FROM favorites")
    suspend fun clearFavorites()
}

@Database(
    entities = [PlayHistoryEntity::class, CustomCoverEntity::class, DownloadEntity::class, MyPlaylistEntity::class, FavoriteEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class LuogenDatabase : RoomDatabase() {
    abstract fun dao(): LuogenDao

    companion object {
        @Volatile
        private var inst: LuogenDatabase? = null

        /** v1 → v2：新增 favorites 表，其余表结构不变，保留用户历史/下载/歌单数据 */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (" +
                        "`songId` INTEGER NOT NULL, " +
                        "`songJson` TEXT NOT NULL, " +
                        "`favoritedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))"
                )
            }
        }

        /** v2 → v3：my_playlists 新增歌单封面字段（自定义头像，空串=默认头像） */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `my_playlists` ADD COLUMN `coverPath` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): LuogenDatabase =
            inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(context.applicationContext, LuogenDatabase::class.java, "luogen.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { inst = it }
            }
    }
}