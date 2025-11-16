package com.campus.panicbutton.services

import android.content.Context
import android.location.Location
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.utils.LocationUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.GeoPoint
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocationServiceMockTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockFusedLocationClient: FusedLocationProviderClient
    
    @Mock
    private lateinit var mockLocation: Location
    
    @Mock
    private lateinit var mockLocationResult: LocationResult
    
    private lateinit var locationService: LocationService
    private lateinit var testCampusBlocks: List<CampusBlock>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        locationService = LocationService(mockContext)
        
        // Set up test campus blocks with realistic coordinates
        testCampusBlocks = listOf(
            CampusBlock(
                id = "main_building",
                name = "Main Building",
                description = "Primary academic building",
                coordinates = GeoPoint(40.7128, -74.0060), // NYC coordinates
                radius = 50.0
            ),
            CampusBlock(
                id = "library",
                name = "University Library",
                description = "Central library",
                coordinates = GeoPoint(40.7130, -74.0062),
                radius = 30.0
            ),
            CampusBlock(
                id = "student_center",
                name = "Student Center",
                description = "Student activities building",
                coordinates = GeoPoint(40.7125, -74.0058),
                radius = 40.0
            ),
            CampusBlock(
                id = "parking_lot_a",
                name = "Parking Lot A",
                description = "Main parking area",
                coordinates = GeoPoint(40.7120, -74.0055),
                radius = 75.0
            )
        )
        
        // Use reflection to inject mock
        val field = LocationService::class.java.getDeclaredField("fusedLocationClient")
        field.isAccessible = true
        field.set(locationService, mockFusedLocationClient)
    }

    @Test
    fun `getCurrentLocation with high accuracy GPS`() {
        // Mock high accuracy location
        whenever(mockLocation.latitude).thenReturn(40.7128)
        whenever(mockLocation.longitude).thenReturn(-74.0060)
        whenever(mockLocation.accuracy).thenReturn(5.0f)
        whenever(mockLocation.time).thenReturn(System.currentTimeMillis())
        whenever(mockFusedLocationClient.lastLocation).thenReturn(Tasks.forResult(mockLocation))

        val result = locationService.getCurrentLocation()
        
        assertTrue("Should successfully get location", result.isSuccessful)
        assertEquals("Should return mock location", mockLocation, result.result)
        assertTrue("Should be high accuracy", locationService.isLocationAccurate(result.result!!))
    }

    @Test
    fun `getCurrentLocation with low accuracy GPS`() {
        // Mock low accuracy location
        whenever(mockLocation.latitude).thenReturn(40.7128)
        whenever(mockLocation.longitude).thenReturn(-74.0060)
        whenever(mockLocation.accuracy).thenReturn(50.0f) // Low accuracy
        whenever(mockFusedLocationClient.lastLocation).thenReturn(Tasks.forResult(mockLocation))

        val result = locationService.getCurrentLocation()
        
        assertTrue("Should get location even with low accuracy", result.isSuccessful)
        assertFalse("Should not be considered accurate", locationService.isLocationAccurate(result.result!!))
    }

    @Test
    fun `getCurrentLocation GPS unavailable`() {
        // Mock GPS unavailable
        whenever(mockFusedLocationClient.lastLocation).thenReturn(Tasks.forResult(null))

        val result = locationService.getCurrentLocation()
        
        assertFalse("Should fail when GPS unavailable", result.isSuccessful)
        assertNotNull("Should have exception", result.exception)
    }

    @Test
    fun `getCurrentLocation network error`() {
        val networkException = Exception("Network unavailable")
        whenever(mockFusedLocationClient.lastLocation).thenReturn(Tasks.forException(networkException))

        val result = locationService.getCurrentLocation()
        
        assertFalse("Should fail on network error", result.isSuccessful)
        assertEquals("Should return network exception", networkException, result.exception)
    }

    @Test
    fun `mapLocationToBlock exact coordinate match`() {
        // Mock location exactly at main building coordinates
        whenever(mockLocation.latitude).thenReturn(40.7128)
        whenever(mockLocation.longitude).thenReturn(-74.0060)

        val result = locationService.mapLocationToBlock(mockLocation, testCampusBlocks)
        
        assertNotNull("Should find matching block", result)
        assertEquals("Should match main building", "main_building", result?.id)
        assertEquals("Should match main building name", "Main Building", result?.name)
    }

    @Test
    fun `mapLocationToBlock within radius`() {
        // Mock location near main building but within radius
        whenever(mockLocation.latitude).thenReturn(40.7129) // Slightly offset
        whenever(mockLocation.longitude).thenReturn(-74.0061)

        val result = locationService.mapLocationToBlock(mockLocation, testCampusBlocks)
        
        assertNotNull("Should find block within radius", result)
        assertEquals("Should match main building", "main_building", result?.id)
    }

    @Test
    fun `mapLocationToBlock outside all radii`() {
        // Mock location far from any campus block
        whenever(mockLocation.latitude).thenReturn(41.0000) // Far away
        whenever(mockLocation.longitude).thenReturn(-75.0000)

        val result = locationService.mapLocationToBlock(mockLocation, testCampusBlocks)
        
        assertNull("Should not find any matching block", result)
    }

    @Test
    fun `mapLocationToBlock closest match when multiple in range`() {
        // Mock location between main building and library, closer to main building
        whenever(mockLocation.latitude).thenReturn(40.7128) // Closer to main building
        whenever(mockLocation.longitude).thenReturn(-74.0061)

        val result = locationService.mapLocationToBlock(mockLocation, testCampusBlocks)
        
        assertNotNull("Should find closest block", result)
        assertEquals("Should match closest block (main building)", "main_building", result?.id)
    }

    @Test
    fun `requestLocationUpdates with callback`() {
        val mockCallback = mock<LocationCallback>()
        
        locationService.requestLocationUpdates(mockCallback)
        
        verify(mockFusedLocationClient).requestLocationUpdates(
            any<LocationRequest>(),
            eq(mockCallback),
            isNull()
        )
    }

    @Test
    fun `stopLocationUpdates removes callback`() {
        val mockCallback = mock<LocationCallback>()
        
        locationService.stopLocationUpdates(mockCallback)
        
        verify(mockFusedLocationClient).removeLocationUpdates(mockCallback)
    }

    @Test
    fun `location updates callback handling`() {
        val latch = CountDownLatch(1)
        var receivedLocation: Location? = null
        
        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                receivedLocation = locationResult.lastLocation
                latch.countDown()
            }
        }

        // Mock location result
        whenever(mockLocationResult.lastLocation).thenReturn(mockLocation)
        whenever(mockLocation.latitude).thenReturn(40.7128)
        whenever(mockLocation.longitude).thenReturn(-74.0060)

        // Simulate receiving location update
        callback.onLocationResult(mockLocationResult)
        
        assertTrue("Should receive location update", latch.await(1, TimeUnit.SECONDS))
        assertEquals("Should receive correct location", mockLocation, receivedLocation)
    }

    @Test
    fun `calculateDistance accuracy test`() {
        val testCases = listOf(
            // lat1, lon1, lat2, lon2, expected distance (meters)
            Triple(40.7128, -74.0060, 40.7128, -74.0060, 0.0), // Same location
            Triple(40.7128, -74.0060, 40.7130, -74.0062, 28.0), // ~28 meters
            Triple(40.7128, -74.0060, 40.7138, -74.0070, 141.0), // ~141 meters
            Triple(0.0, 0.0, 0.0, 1.0, 111319.0) // 1 degree longitude at equator
        )

        testCases.forEach { (lat1, lon1, lat2, lon2, expectedDistance) ->
            val actualDistance = locationService.calculateDistance(lat1, lon1, lat2, lon2)
            val tolerance = expectedDistance * 0.1 // 10% tolerance
            
            assertTrue(
                "Distance calculation for ($lat1,$lon1) to ($lat2,$lon2) should be ~$expectedDistance meters, got $actualDistance",
                kotlin.math.abs(actualDistance - expectedDistance) <= tolerance
            )
        }
    }

    @Test
    fun `location accuracy validation`() {
        val accuracyTests = listOf(
            5.0f to true,    // High accuracy
            10.0f to true,   // Good accuracy
            20.0f to true,   // Acceptable accuracy (boundary)
            25.0f to false,  // Low accuracy
            50.0f to false,  // Very low accuracy
            100.0f to false  // Poor accuracy
        )

        accuracyTests.forEach { (accuracy, expectedValid) ->
            whenever(mockLocation.accuracy).thenReturn(accuracy)
            
            val isAccurate = locationService.isLocationAccurate(mockLocation)
            assertEquals(
                "Accuracy $accuracy should be ${if (expectedValid) "valid" else "invalid"}",
                expectedValid,
                isAccurate
            )
        }
    }

    @Test
    fun `location age validation`() {
        val currentTime = System.currentTimeMillis()
        val ageTests = listOf(
            currentTime to true,                    // Current
            currentTime - 30_000 to true,          // 30 seconds old - valid
            currentTime - 60_000 to true,          // 1 minute old - valid
            currentTime - 300_000 to false,        // 5 minutes old - invalid
            currentTime - 600_000 to false         // 10 minutes old - invalid
        )

        ageTests.forEach { (locationTime, expectedValid) ->
            whenever(mockLocation.time).thenReturn(locationTime)
            
            val isRecent = locationService.isLocationRecent(mockLocation)
            assertEquals(
                "Location time $locationTime should be ${if (expectedValid) "recent" else "stale"}",
                expectedValid,
                isRecent
            )
        }
    }

    @Test
    fun `campus block loading and caching`() {
        // Test that campus blocks are properly loaded and cached
        val blocks = locationService.loadCampusBlocks()
        
        assertNotNull("Should load campus blocks", blocks)
        assertTrue("Should have multiple blocks", blocks.size > 0)
        
        // Test caching - second call should return cached data
        val cachedBlocks = locationService.loadCampusBlocks()
        assertEquals("Should return same cached blocks", blocks, cachedBlocks)
    }

    @Test
    fun `location permission handling`() {
        // Test location permission checks
        whenever(mockContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION))
            .thenReturn(android.content.pm.PackageManager.PERMISSION_GRANTED)

        assertTrue("Should have location permission", locationService.hasLocationPermission())

        whenever(mockContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION))
            .thenReturn(android.content.pm.PackageManager.PERMISSION_DENIED)

        assertFalse("Should not have location permission", locationService.hasLocationPermission())
    }
}