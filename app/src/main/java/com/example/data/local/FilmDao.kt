package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmDao {

    @Query("SELECT * FROM film_rolls WHERE isActive = 1 LIMIT 1")
    fun observeActiveRoll(): Flow<FilmRollEntity?>

    @Query("SELECT * FROM film_rolls WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveRollOnce(): FilmRollEntity?

    @Query("SELECT * FROM film_rolls WHERE isDeveloped = 0 AND shotsTaken > 0 ORDER BY isActive DESC, id DESC")
    fun observeInProgressRolls(): Flow<List<FilmRollEntity>>

    @Query("SELECT * FROM film_rolls WHERE isDeveloped = 0 AND bodyId = :bodyId ORDER BY shotsTaken DESC, id DESC LIMIT 1")
    suspend fun getUndevelopedRollForBody(bodyId: String): FilmRollEntity?

    @Query("DELETE FROM film_rolls WHERE isDeveloped = 0 AND shotsTaken = 0 AND id != :keepRollId")
    suspend fun deleteEmptyDraftRollsExcept(keepRollId: Long)

    @Query("SELECT * FROM film_rolls WHERE isDeveloped = 1 ORDER BY developedAt DESC")
    fun observeDevelopedRolls(): Flow<List<FilmRollEntity>>

    @Query("SELECT * FROM film_rolls ORDER BY id DESC")
    fun observeAllRolls(): Flow<List<FilmRollEntity>>

    @Query("SELECT * FROM film_rolls WHERE id = :rollId LIMIT 1")
    suspend fun getRollById(rollId: Long): FilmRollEntity?

    @Query("SELECT MAX(rollNumber) FROM film_rolls")
    suspend fun getMaxRollNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoll(roll: FilmRollEntity): Long

    @Update
    suspend fun updateRoll(roll: FilmRollEntity)

    @Query("UPDATE film_rolls SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllRolls()

    @Query("DELETE FROM film_rolls WHERE id = :rollId")
    suspend fun deleteRollById(rollId: Long)

    @Query("UPDATE film_rolls SET customTitle = :title WHERE id = :rollId")
    suspend fun updateRollTitle(rollId: Long, title: String)

    // Frames
    @Query("SELECT * FROM film_frames WHERE rollId = :rollId ORDER BY frameNumber ASC")
    fun observeFramesForRoll(rollId: Long): Flow<List<FilmFrameEntity>>

    @Query("SELECT * FROM film_frames WHERE rollId = :rollId ORDER BY frameNumber ASC")
    suspend fun getFramesForRollOnce(rollId: Long): List<FilmFrameEntity>

    @Query("SELECT * FROM film_frames WHERE developedPath IS NOT NULL ORDER BY rollId DESC, frameNumber ASC")
    fun observeAllDevelopedFrames(): Flow<List<FilmFrameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrame(frame: FilmFrameEntity): Long

    @Update
    suspend fun updateFrame(frame: FilmFrameEntity)

    @Query("UPDATE film_frames SET caption = :caption WHERE id = :frameId")
    suspend fun updateFrameCaption(frameId: Long, caption: String)
}
