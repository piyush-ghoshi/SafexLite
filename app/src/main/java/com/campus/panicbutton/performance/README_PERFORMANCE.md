# Performance Optimization Implementation

This document describes the performance optimizations implemented for the Campus Panic Button Android app to improve battery usage, memory management, and overall app performance.

## Overview

The performance optimization implementation includes:

1. **Efficient Firestore queries with proper indexing**
2. **Battery-optimized location services**
3. **Image compression utilities for future photo uploads**
4. **Comprehensive lifecycle management**
5. **Memory leak detection and prevention**

## Components

### 1. PerformanceManager (`utils/PerformanceManager.kt`)

**Purpose**: Central performance monitoring and memory management

**Features**:
- Memory usage monitoring and cleanup
- Firestore optimization configuration
- Reference tracking for memory leak prevention
- Automatic garbage collection triggers
- Performance statistics collection

**Usage**:
```kotlin
val performanceManager = PerformanceManager.getInstance(context)
performanceManager.registerReference("my_object", myObject)
val stats = performanceManager.getPerformanceStats()
```

### 2. BatteryOptimizer (`utils/BatteryOptimizer.kt`)

**Purpose**: Minimize battery drain from location services

**Features**:
- Intelligent location caching (30-second cache duration)
- Request batching to reduce GPS usage
- Minimum interval enforcement (10 seconds between requests)
- Accuracy-based caching decisions
- Automatic location update cleanup

**Usage**:
```kotlin
val batteryOptimizer = BatteryOptimizer.getInstance(context)
batteryOptimizer.getOptimizedLocation(
    priority = BatteryOptimizer.LocationPriority.BALANCED,
    onSuccess = { location -> /* handle location */ },
    onError = { error -> /* handle error */ }
)
```

### 3. FirestoreQueryOptimizer (`utils/FirestoreQueryOptimizer.kt`)

**Purpose**: Optimize Firestore database operations

**Features**:
- Query result caching (30-second cache duration)
- Optimized query construction with limits
- Batch write operations
- Listener management for cleanup
- Pre-configured queries for common operations

**Usage**:
```kotlin
val query = FirestoreQueryOptimizer.getOptimizedAlertsQuery(firestore, limit = 50)
FirestoreQueryOptimizer.executeOptimizedQuery(
    query = query,
    cacheKey = "alerts_list",
    config = FirestoreQueryOptimizer.QueryConfig(useCache = true)
)
```

### 4. ImageCompressionUtils (`utils/ImageCompressionUtils.kt`)

**Purpose**: Efficient image processing for future photo upload features

**Features**:
- Automatic image resizing (default: 1024x1024 max)
- Quality-based compression (default: 80% quality)
- File size optimization (default: 500KB max)
- EXIF orientation handling
- Memory-efficient bitmap processing
- Async compression with coroutines

**Usage**:
```kotlin
val result = ImageCompressionUtils.compressImage(
    context = context,
    imageUri = imageUri,
    config = ImageCompressionUtils.CompressionConfig(
        maxWidth = 800,
        maxHeight = 600,
        quality = 75
    )
)
```

### 5. LifecycleManager (`utils/LifecycleManager.kt`)

**Purpose**: Proper lifecycle management and resource cleanup

**Features**:
- Activity lifecycle tracking
- Firestore listener management
- Automatic cleanup on memory pressure
- Background/foreground state monitoring
- Component callbacks for memory management

**Usage**:
```kotlin
val lifecycleManager = LifecycleManager.getInstance()
lifecycleManager.registerFirestoreListener("alerts_listener", listener)
// Automatic cleanup when activity is destroyed
```

### 6. MemoryLeakDetector (`utils/MemoryLeakDetector.kt`)

**Purpose**: Detect and prevent memory leaks (Debug builds only)

**Features**:
- Automatic activity and fragment tracking
- Weak reference monitoring
- Leak detection with configurable delays
- Comprehensive leak reporting
- Automatic cleanup of tracked objects

**Usage**:
```kotlin
// Automatic tracking in debug builds
MemoryLeakDetector.trackActivity(activity)
MemoryLeakDetector.trackFragment(fragment)

// Manual object tracking
MemoryLeakDetector.trackObject("my_key", myObject)

// Leak detection
val result = MemoryLeakDetector.detectLeaks()
```

## Firestore Indexes

The following indexes are configured in `firestore.indexes.json` for optimal query performance:

```json
{
  "collectionGroup": "alerts",
  "fields": [
    { "fieldPath": "status", "order": "ASCENDING" },
    { "fieldPath": "timestamp", "order": "DESCENDING" }
  ]
}
```

Additional indexes for:
- User alerts by guardId + timestamp
- Alert acceptance tracking
- User role and status queries

## Integration

### Application Class (`PanicButtonApplication.kt`)

The custom Application class initializes all performance optimizations:

```kotlin
class PanicButtonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize performance managers
        PerformanceManager.getInstance(this)
        LifecycleManager.getInstance().initialize(this)
        BatteryOptimizer.getInstance(this)
        
        // Set up memory leak detection (debug only)
        if (BuildConfig.DEBUG) {
            setupMemoryLeakDetection()
        }
    }
}
```

### Service Optimizations

#### FirebaseService Enhancements
- Optimized query methods with caching
- Batch operations for multiple updates
- Performance statistics collection
- Automatic cleanup on service destruction

#### LocationService Enhancements
- Battery-optimized location requests
- Intelligent caching and batching
- Performance monitoring integration
- Automatic resource cleanup

## Performance Metrics

### Memory Management
- **Memory threshold**: 50MB (triggers cleanup)
- **GC interval**: 30 seconds
- **Reference tracking**: Automatic cleanup of null references

### Battery Optimization
- **Location cache duration**: 30 seconds
- **Minimum request interval**: 10 seconds
- **Accuracy threshold**: 50 meters
- **Maximum location age**: 1 minute

### Query Optimization
- **Cache duration**: 30 seconds
- **Maximum cache size**: 50 entries
- **Default query limits**: 20-100 results
- **Batch operation support**: Multiple document updates

### Image Compression
- **Default max dimensions**: 1024x1024 pixels
- **Default quality**: 80%
- **Maximum file size**: 500KB
- **Format**: JPEG with EXIF preservation

## Testing

Comprehensive test suite in `performance/PerformanceOptimizationTest.kt`:

- Performance manager initialization and memory tracking
- Battery optimizer caching and statistics
- Image compression configuration
- Memory leak detection functionality
- Firestore query optimization
- Cleanup operations verification

## Best Practices

### For Developers

1. **Always use optimized services**:
   ```kotlin
   // Use optimized location service
   locationService.getOptimizedLocation(priority, onSuccess, onError)
   
   // Use optimized Firestore queries
   firebaseService.getOptimizedActiveAlertsWithListener(key, onResult, onError)
   ```

2. **Register objects for memory tracking**:
   ```kotlin
   performanceManager.registerReference("activity_${activity.hashCode()}", activity)
   ```

3. **Clean up resources properly**:
   ```kotlin
   override fun onDestroy() {
       super.onDestroy()
       performanceManager.unregisterReference("my_object")
       lifecycleManager.removeListener("my_listener")
   }
   ```

4. **Use batch operations for multiple updates**:
   ```kotlin
   val updates = listOf(
       "alert1" to mapOf("status" to "RESOLVED"),
       "alert2" to mapOf("status" to "CLOSED")
   )
   firebaseService.batchUpdateAlerts(updates)
   ```

### Performance Monitoring

Monitor performance using built-in statistics:

```kotlin
// Get comprehensive performance stats
val perfStats = performanceManager.getPerformanceStats()
val batteryStats = batteryOptimizer.getBatteryStats()
val cacheStats = FirestoreQueryOptimizer.getCacheStats()
val leakStats = MemoryLeakDetector.getLeakStats()
```

## Configuration

### Memory Management
- Adjust `MEMORY_THRESHOLD_MB` in PerformanceManager for different devices
- Modify `GC_INTERVAL_MS` based on app usage patterns

### Battery Optimization
- Configure `LOCATION_CACHE_DURATION_MS` based on accuracy requirements
- Adjust `MIN_LOCATION_INTERVAL_MS` for different update frequencies

### Query Optimization
- Modify `CACHE_DURATION_MS` based on data freshness requirements
- Adjust `MAX_CACHE_SIZE` based on available memory

## Troubleshooting

### High Memory Usage
1. Check `PerformanceManager.getPerformanceStats()` for memory info
2. Verify proper cleanup in activity `onDestroy()` methods
3. Use `MemoryLeakDetector.detectLeaks()` to identify leaks

### Poor Battery Life
1. Monitor `BatteryOptimizer.getBatteryStats()` for cache hit rates
2. Ensure location updates are stopped when not needed
3. Check for excessive location requests in logs

### Slow Firestore Queries
1. Verify indexes are properly configured
2. Check `FirestoreQueryOptimizer.getCacheStats()` for cache performance
3. Ensure query limits are appropriate for use case

## Future Enhancements

1. **Adaptive Performance**: Adjust optimization parameters based on device capabilities
2. **Network Optimization**: Implement request deduplication and offline queuing
3. **Advanced Caching**: Implement LRU cache with size-based eviction
4. **Performance Analytics**: Integration with Firebase Performance Monitoring
5. **Background Processing**: Optimize background task scheduling and execution