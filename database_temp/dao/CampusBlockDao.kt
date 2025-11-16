package com.campus.panicbutton.database.dao

import androidx.room.*
import com.campus.panicbutton.database.entities.CampusBlockEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for CampusBlock operations in Room database
 */
@Dao
interface CampusBlockDao {
    
    /**
     * Get all campus blocks
     */
    @Query("SELECT * FROM campus_blocks ORDER BY name ASC")
    fun getAllCampusBlocks(): Flow<List<CampusBlockEntity>>
    
    /**
     * Get all campus blocks as a list (for one-time queries)
     */
    @Query("SELECT * FROM campus_blocks ORDER BY name ASC")
    suspend fun getAllCampusBlocksOnce(): List<CampusBlockEntity>
    
    /**
     * Get a specific campus block by ID
     */
    @Query("SELECT * FROM campus_blocks WHERE id = :blockId")
    suspend fun getCampusBlockById(blockId: String): CampusBlockEntity?
    
    /**
     * Insert or replace a campus block
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampusBlock(block: CampusBlockEntity)
    
    /**
     * Insert or replace multiple campus blocks
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampusBlocks(blocks: List<CampusBlockEntity>)
    
    /**
     * Update a campus block
     */
    @Update
    suspend fun updateCampusBlock(block: CampusBlockEntity)
    
    /**
     * Delete a campus block
     */
    @Delete
    suspend fun deleteCampusBlock(block: CampusBlockEntity)
    
    /**
     * Delete campus block by ID
     */
    @Query("DELETE FROM campus_blocks WHERE id = :blockId")
    suspend fun deleteCampusBlockById(blockId: String)
    
    /**
     * Delete all campus blocks
     */
    @Query("DELETE FROM campus_blocks")
    suspend fun deleteAllCampusBlocks()
    
    /**
     * Get count of campus blocks
     */
    @Query("SELECT COUNT(*) FROM campus_blocks")
    suspend fun getCampusBlockCount(): Int
    
    /**
     * Update last updated timestamp for a block
     */
    @Query("UPDATE campus_blocks SET lastUpdated = :timestamp WHERE id = :blockId")
    suspend fun updateLastUpdated(blockId: String, timestamp: Long)
}