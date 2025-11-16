package com.campus.panicbutton.services

import android.content.Context
import android.location.Location
import com.campus.panicbutton.models.CampusBlock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.GeoPoint
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class LocationServiceTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockFusedLocationClient: FusedLocationProviderClient
    
    @Mock
    private lateinit var mockLocation: Location
    
    private lateinit var locationService: LocationService
    private lateinit var campusBlocks: List<CampusBlock>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        locationService = LocationService(mockContext)
        
        // Set up test campus blocks
        campusBlocks = listOf(
            CampusBlock(
                id = "main",
                name = "Main Building",
                coordinates = GeoPoint(40.7128, -74.0060),
                radius = 50.0
            ),
            CampusBlock(
                id = "library",
                name = "Library",
                coordinates = GeoPoint(40.7130, -74.0062),
                radius = 30.0
            ),
            CampusBlock(
                id = "gym",
                name = "Gymnasium",
                coordinates = GeoPoint(40.7125, -74.0058),
                radius = 40.0
            )
        )
        
        // Use reflection to set private fields
        val fusedLocationField = LocationService::class.java.getDeclaredField("fusedLocationClient")
        fusedLocationField.isAccessible = true
        fusedLocationField.set(locationService, mockFusedLocationClient)
    }

    @Test
    fun `getCurrentLocation success`() {
        whenever(mockLocation.latitude).thenReturn(40.7128)
        whenever(mockLocation.longitude).thenReturn(-74.0060)
        whenever(mockLocation.accuracy).thenReturn(10.0f)
        whenever(mockFusedLocationClient.lastLocation).thenReturn(Tasks.forResult(mockLocation))

        val result = locationService.getCurrentLocation()
        
        assertTrue(result.isSuccessful)
        assertEquals(mockLocation, result.result)
        verify(mockFusedLocationClient).lastLocation
    }

    @Test
    fun `getCurrentLocation failure`() {
        val exception = Exception("Location unavailable")
        whenever(mockFusedLocationClient.lastLocation).thenReturn(Tasks.forException(exception))

        val result = locationService.getCurrentLocation()
        
        assertFalse(result.isSuccessful)
        assertEquals(exception, result.exception)
    }

    @Test
    fun `getCurrentLocation null result`() {
        whenever(mockFusedLocationClient.lastLocation).thenReturn(Tasks.forResult(null))

        val result = locationService.getCurrentLocation()
        
        assertFalse(result.isSuccessful)
        assertNotNull(result.exception)
    }

    @Test
    fun `mapLocationToBlock exact match`() {
        whenever(mockLocation.latitude).thenReturn(40.7128)
        whenever(mockLocation.longitude).thenReturn(-74.0060)

        val result = locationService.mapLocationToBlock(mockLocation, campusBlocks)
        
        assertNotNull(result)
        assertEquals("main", result?.id)
        assertEquals("Main Building", result?.name)
    }

    @Test
    fun `mapLocationToBlock within radius`() {
        // Location slightly offset but within radius
        whenever(mockLocation.latitude).thenReturn(40.7129)
        whenever(mockLocation.longitude).thenReturn(-74.0061)

        val result = locationService.mapLocationToBlock(mockLocation, campusBlocks)
        
        assertNotNull(result)
        assertEquals("main", result?.id)
    }

    @Test
    fun `mapLocationToBlock no match`() {
        // Location far from any campus block
        whenever(mockLocation.latitude).thenReturn(41.0000)
        whenever(mockLocation.longitude).thenReturn(-75.0000)

        val result = locationService.mapLocationToBlock(mockLocation, campusBlocks)
        
        assertNull(result)
    }

    @Test
    fun `mapLocationToBlock closest match when multiple in range`() {
        // Location between main building and library, closer to main
        whenever(mockLocation.latitude).thenReturn(40.7128)
        whenever(mockLocation.longitude).thenReturn(-74.0061)

        val result = locationService.mapLocationToBlock(mockLocation, campusBlocks)
        
        assertNotNull(result)
        assertEquals("main", result?.id)
    }

    @Test
    fun `calculateDistance accurate calculation`() {
        val lat1 = 40.7128
        val lon1 = -74.0060
        val lat2 = 40.7130
        val lon2 = -74.0062

        val distance = locationService.calculateDistance(lat1, lon1, lat2, lon2)
        
        // Distance should be approximately 28 meters
        assertTrue("Distance should be around 28 meters", distance > 20 && distance < 35)
    }

    @Test
    fun `calculateDistance same location`() {
        val lat = 40.7128
        val lon = -74.0060

        val distance = locationService.calculateDistance(lat, lon, lat, lon)
        
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `isLocationAccurate high accuracy`() {
        whenever(mockLocation.accuracy).thenReturn(5.0f)
        
        assertTrue(locationService.isLocationAccurate(mockLocation))
    }

    @Test
    fun `isLocationAccurate low accuracy`() {
        whenever(mockLocation.accuracy).thenReturn(100.0f)
        
        assertFalse(locationService.isLocationAccurate(mockLocation))
    }

    @Test
    fun `isLocationAccurate boundary accuracy`() {
        whenever(mockLocation.accuracy).thenReturn(20.0f)
        
        assertTrue(locationService.isLocationAccurate(mockLocation))
        
        whenever(mockLocation.accuracy).thenReturn(21.0f)
        
        assertFalse(locationService.isLocationAccurate(mockLocation))
    }

    @Test
    fun `requestLocationUpdates creates proper request`() {
        val callback = mock<com.google.android.gms.location.LocationCallback>()
        
        locationService.requestLocationUpdates(callback)
        
        verify(mockFusedLocationClient).requestLocationUpdates(
            any<LocationRequest>(),
            eq(callback),
            isNull()
        )
    }

    @Test
    fun `stopLocationUpdates removes callback`() {
        val callback = mock<com.google.android.gms.location.LocationCallback>()
        
        locationService.stopLocationUpdates(callback)
        
        verify(mockFusedLocationClient).removeLocationUpdates(callback)
    }
}