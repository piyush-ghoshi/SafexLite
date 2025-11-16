package com.campus.panicbutton.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.campus.panicbutton.models.CampusBlock
import com.google.firebase.firestore.GeoPoint

/**
 * Room entity for caching CampusBlock data locally for offline support
 */
@Entity(tableName = "campus_blocks")
data class CampusBlockEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Convert CampusBlockEntity to CampusBlock model
     */
    fun toCampusBlock(): CampusBlock {
        return CampusBlock(
            id = id,
            name = name,
            description = description,
            coordinates = GeoPoint(latitude, longitude),
            radius = radius
        )
    }

    companion object {
        /**
         * Convert CampusBlock model to CampusBlockEntity
         */
        fun fromCampusBlock(block: CampusBlock): CampusBlockEntity {
            return CampusBlockEntity(
                id = block.id,
                name = block.name,
                description = block.description,
                latitude = block.coordinates.latitude,
                longitude = block.coordinates.longitude,
                radius = block.radius
            )
        }
    }
}