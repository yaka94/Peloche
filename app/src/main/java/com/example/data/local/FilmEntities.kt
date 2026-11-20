package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.DevelopmentMethod
import com.example.domain.model.FilmExpirationDate

@Entity(tableName = "film_rolls")
data class FilmRollEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rollNumber: Int,
    val bodyId: String,
    val lensId: String,
    val filmId: String,
    val expirationSerialized: String, // "MM/YYYY"
    val maxExposures: Int,
    val shotsTaken: Int = 0,
    val isDeveloped: Boolean = false,
    val developmentMethodName: String = DevelopmentMethod.STANDARD_C41.name,
    val isActive: Boolean = true,
    val isDiptychMode: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val developedAt: Long? = null
) {
    val expirationDate: FilmExpirationDate
        get() = FilmExpirationDate.parse(expirationSerialized)

    val developmentMethod: DevelopmentMethod
        get() = DevelopmentMethod.entries.firstOrNull { it.name == developmentMethodName }
            ?: DevelopmentMethod.STANDARD_C41

    val isFull: Boolean get() = shotsTaken >= maxExposures
}

@Entity(
    tableName = "film_frames",
    foreignKeys = [
        ForeignKey(
            entity = FilmRollEntity::class,
            parentColumns = ["id"],
            childColumns = ["rollId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["rollId"])]
)
data class FilmFrameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rollId: Long,
    val frameNumber: Int,
    val rawLatentPath: String,        // Stored privately until developed (unviewable!)
    val developedPath: String? = null,// Rendered after lab chemical development
    val exportedMediaUri: String? = null,
    val bodyId: String,
    val lensId: String,
    val filmId: String,
    val expirationSerialized: String,
    val evCompensation: Float,
    val zoomMultiplier: Float,
    val lightLeakSeed: Float,
    val dateStampText: String?,       // null if date stamp was OFF at shutter release
    val isFrontCamera: Boolean = false,
    val isDiptychPaired: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis()
)
