package com.campus.panicbutton.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.tasks.Task
import java.util.concurrent.ConcurrentHashMap

/**
 * Firestore query optimizer for efficient database operations
 * Implements query caching, batching, and indexing optimization
 */
object FirestoreQueryOptimizer {
    
    private const val TAG = "FirestoreQueryOptimizer"
    private const val CACHE_DURATION_MS = 30000L // 30 seconds
    private const val MAX_CACHE_SIZE = 50
    
    // Query result cache
    private val queryCache = ConcurrentHashMap<String, CachedQueryResult>()
    
    // Active listeners for cleanup
    private val activeListeners = ConcurrentHashMap<String, ListenerRegistration>()
    
    /**
     * Cached query result
     */
    private data class CachedQueryResult(
        val result: QuerySnapshot,
        val timestamp: Long,
        val source: Source
    )
    
    /**
     * Query configuration for optimization
     */
    data class QueryConfig(
        val useCache: Boolean = true,
        val cacheFirst: Boolean = true,
        val maxResults: Int = 100,
        val source: Source = Source.DEFAULT
    )
    
    /**
     * Execute optimized query with caching
     */
    fun executeOptimizedQuery(
        query: Query,
        cacheKey: String,
        config: QueryConfig = QueryConfig()
    ): Task<QuerySnapshot> {
        
        // Check cache first if enabled
        if (config.useCache && config.cacheFirst) {
            val cachedResult = getCachedResult(cacheKey)
            if (cachedResult != null) {
                Log.d(TAG, "Using cached result for key: $cacheKey")
                return com.google.android.gms.tasks.Tasks.forResult(cachedResult.result)
            }
        }
        
        // Apply query optimizations
        val optimizedQuery = optimizeQuery(query, config)
        
        Log.d(TAG, "Executing optimized query: $cacheKey")
        
        return optimizedQuery.get(config.source).continueWith { task ->
            if (task.isSuccessful) {
                val result = task.result
                
                // Cache the result if enabled
                if (config.useCache) {
                    cacheQueryResult(cacheKey, result, config.source)
                }
                
                Log.d(TAG, "Query completed: $cacheKey (${result.size()} documents)")
                result
            } else {
                Log.e(TAG, "Query failed: $cacheKey", task.exception)
                throw task.exception ?: Exception("Query failed")
            }
        }
    }
    
    /**
     * Execute optimized real-time query with listener management
     */
    fun executeOptimizedListener(
        query: Query,
        listenerKey: String,
        config: QueryConfig = QueryConfig(),
        onResult: (QuerySnapshot) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        
        // Remove existing listener if any
        removeListener(listenerKey)
        
        // Apply query optimizations
        val optimizedQuery = optimizeQuery(query, config)
        
        Log.d(TAG, "Setting up optimized listener: $listenerKey")
        
        val listener = optimizedQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Listener error: $listenerKey", error)
                onError(error)
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                // Cache the result
                if (config.useCache) {
                    cacheQueryResult(listenerKey, snapshot, Source.SERVER)
                }
                
                Log.d(TAG, "Listener update: $listenerKey (${snapshot.size()} documents)")
                onResult(snapshot)
            }
        }
        
        // Track the listener for cleanup
        activeListeners[listenerKey] = listener
        
        return listener
    }
    
    /**
     * Optimize query based on configuration
     */
    private fun optimizeQuery(query: Query, config: QueryConfig): Query {
        var optimizedQuery = query
        
        // Limit results to prevent large data transfers
        if (config.maxResults > 0) {
            optimizedQuery = optimizedQuery.limit(config.maxResults.toLong())
        }
        
        return optimizedQuery
    }
    
    /**
     * Cache query result
     */
    private fun cacheQueryResult(key: String, result: QuerySnapshot, source: Source) {
        // Clean cache if it's getting too large
        if (queryCache.size >= MAX_CACHE_SIZE) {
            cleanOldCacheEntries()
        }
        
        queryCache[key] = CachedQueryResult(
            result = result,
            timestamp = System.currentTimeMillis(),
            source = source
        )
        
        Log.d(TAG, "Cached query result: $key")
    }
    
    /**
     * Get cached result if valid
     */
    private fun getCachedResult(key: String): CachedQueryResult? {
        val cached = queryCache[key] ?: return null
        
        val age = System.currentTimeMillis() - cached.timestamp
        if (age > CACHE_DURATION_MS) {
            queryCache.remove(key)
            Log.d(TAG, "Cache expired for key: $key")
            return null
        }
        
        return cached
    }
    
    /**
     * Clean old cache entries
     */
    private fun cleanOldCacheEntries() {
        val currentTime = System.currentTimeMillis()
        val iterator = queryCache.iterator()
        var cleanedCount = 0
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val age = currentTime - entry.value.timestamp
            if (age > CACHE_DURATION_MS) {
                iterator.remove()
                cleanedCount++
            }
        }
        
        Log.d(TAG, "Cleaned $cleanedCount old cache entries")
    }
    
    /**
     * Remove specific listener
     */
    fun removeListener(listenerKey: String) {
        activeListeners.remove(listenerKey)?.let { listener ->
            listener.remove()
            Log.d(TAG, "Removed listener: $listenerKey")
        }
    }
    
    /**
     * Remove all listeners
     */
    fun removeAllListeners() {
        val listenerCount = activeListeners.size
        activeListeners.values.forEach { it.remove() }
        activeListeners.clear()
        Log.d(TAG, "Removed $listenerCount listeners")
    }
    
    /**
     * Clear query cache
     */
    fun clearCache() {
        val cacheSize = queryCache.size
        queryCache.clear()
        Log.d(TAG, "Cleared $cacheSize cached queries")
    }
    
    /**
     * Get optimized alerts query
     */
    fun getOptimizedAlertsQuery(
        firestore: FirebaseFirestore,
        limit: Int = 50,
        includeResolved: Boolean = false
    ): Query {
        var query = firestore.collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
        
        if (!includeResolved) {
            query = query.whereIn("status", listOf("ACTIVE", "IN_PROGRESS"))
        }
        
        return query.limit(limit.toLong())
    }
    
    /**
     * Get optimized active alerts query
     */
    fun getOptimizedActiveAlertsQuery(
        firestore: FirebaseFirestore,
        limit: Int = 20
    ): Query {
        return firestore.collection("alerts")
            .whereEqualTo("status", "ACTIVE")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
    }
    
    /**
     * Get optimized user alerts query
     */
    fun getOptimizedUserAlertsQuery(
        firestore: FirebaseFirestore,
        userId: String,
        limit: Int = 30
    ): Query {
        return firestore.collection("alerts")
            .whereEqualTo("guardId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
    }
    
    /**
     * Get optimized campus blocks query
     */
    fun getOptimizedCampusBlocksQuery(
        firestore: FirebaseFirestore
    ): Query {
        return firestore.collection("campus_blocks")
            .orderBy("name")
    }
    
    /**
     * Batch write operations for better performance
     */
    fun executeBatchWrite(
        firestore: FirebaseFirestore,
        operations: List<BatchOperation>
    ): Task<Void> {
        val batch = firestore.batch()
        
        operations.forEach { operation ->
            when (operation.type) {
                BatchOperationType.SET -> {
                    batch.set(operation.documentRef, operation.data!!)
                }
                BatchOperationType.UPDATE -> {
                    batch.update(operation.documentRef, operation.data!!)
                }
                BatchOperationType.DELETE -> {
                    batch.delete(operation.documentRef)
                }
            }
        }
        
        Log.d(TAG, "Executing batch write with ${operations.size} operations")
        return batch.commit()
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val currentTime = System.currentTimeMillis()
        val validEntries = queryCache.values.count { 
            currentTime - it.timestamp <= CACHE_DURATION_MS 
        }
        
        return CacheStats(
            totalCacheEntries = queryCache.size,
            validCacheEntries = validEntries,
            activeListeners = activeListeners.size,
            cacheHitRate = calculateCacheHitRate()
        )
    }
    
    private fun calculateCacheHitRate(): Float {
        // This would need to be tracked over time in a real implementation
        return 0.0f // Placeholder
    }
    
    /**
     * Batch operation data class
     */
    data class BatchOperation(
        val type: BatchOperationType,
        val documentRef: com.google.firebase.firestore.DocumentReference,
        val data: Map<String, Any>? = null
    )
    
    /**
     * Batch operation types
     */
    enum class BatchOperationType {
        SET, UPDATE, DELETE
    }
    
    /**
     * Cache statistics data class
     */
    data class CacheStats(
        val totalCacheEntries: Int,
        val validCacheEntries: Int,
        val activeListeners: Int,
        val cacheHitRate: Float
    )
    
    /**
     * Recommended Firestore indexes for optimal performance
     * These should be added to firestore.indexes.json
     */
    fun getRecommendedIndexes(): List<String> {
        return listOf(
            """
            {
              "collectionGroup": "alerts",
              "queryScope": "COLLECTION",
              "fields": [
                { "fieldPath": "status", "order": "ASCENDING" },
                { "fieldPath": "timestamp", "order": "DESCENDING" }
              ]
            }
            """.trimIndent(),
            """
            {
              "collectionGroup": "alerts",
              "queryScope": "COLLECTION",
              "fields": [
                { "fieldPath": "guardId", "order": "ASCENDING" },
                { "fieldPath": "timestamp", "order": "DESCENDING" }
              ]
            }
            """.trimIndent(),
            """
            {
              "collectionGroup": "alerts",
              "queryScope": "COLLECTION",
              "fields": [
                { "fieldPath": "acceptedBy", "order": "ASCENDING" },
                { "fieldPath": "timestamp", "order": "DESCENDING" }
              ]
            }
            """.trimIndent(),
            """
            {
              "collectionGroup": "users",
              "queryScope": "COLLECTION",
              "fields": [
                { "fieldPath": "role", "order": "ASCENDING" },
                { "fieldPath": "isActive", "order": "ASCENDING" }
              ]
            }
            """.trimIndent()
        )
    }
}