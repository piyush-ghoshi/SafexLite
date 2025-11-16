package com.campus.panicbutton.utils

import android.location.Location
import com.campus.panicbutton.models.CampusBlock
import com.google.firebase.firestore.GeoPoint
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

class LocationValidatorTest {

    @Test
    fun `validateLocationData accepts valid location`() {
        val location = createMockLocation(40.5, -74.5, 10.0f, System.currentTimeMillis())
        
        val result = LocationValidator.validateLocationData(location)
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `validateLocationData rejects location without accuracy`() {
        val location = createMockLocation(40.5, -74.5, null, System.currentTimeMillis())
        
        val result = LocationValidator.validateLocationData(location)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.contains("Location accuracy not available"))
    }
    
    @Test
    fun `validateLocationData rejects location with poor accuracy`() {
        val location = createMockLocation(40.5, -74.5, 150.0f, System.currentTimeMillis())
        
        val result = LocationValidator.validateLocationData(location)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("accuracy is too low") })
    }
    
    @Test
    fun `validateLocationData rejects old location`() {
        val oldTime = System.currentTimeMillis() - (10 * 60 * 1000L) // 10 minutes ago
        val location = createMockLocation(40.5, -74.5, 10.0f, oldTime)
        
        val result = LocationValidator.validateLocationData(location)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("too old") })
    }
    
    @Test
    fun `validateCoordinates accepts valid campus coordinates`() {
        val result = LocationValidator.validateCoordinates(40.5, -74.5)
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `validateCoordinates rejects invalid latitude`() {
        val result = LocationValidator.validateCoordinates(91.0, -74.5)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Invalid latitude") })
    }
    
    @Test
    fun `validateCoordinates rejects invalid longitude`() {
        val result = LocationValidator.validateCoordinates(40.5, 181.0)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Invalid longitude") })
    }
    
    @Test
    fun `validateCoordinates rejects coordinates outside campus bounds`() {
        val result = LocationValidator.validateCoordinates(50.0, -80.0) // Outside campus bounds
        
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("outside campus boundaries") })
    }
    
    @Test
    fun `isWithinCampusBounds returns true for campus coordinates`() {
        assertTrue(LocationValidator.isWithinCampusBounds(40.5, -74.5))
    }
    
    @Test
    fun `isWithinCampusBounds returns false for coordinates outside campus`() {
        assertFalse(LocationValidator.isWithinCampusBounds(50.0, -80.0))
        assertFalse(LocationValidator.isWithinCampusBounds(30.0, -74.5))
        assertFalse(LocationValidator.isWithinCampusBounds(40.5, -70.0))
    }
    
    @Test
    fun `findNearestBlock returns null for empty block list`() {
        val result = LocationValidator.findNearestBlock(40.5, -74.5, emptyList())
        
        assertNull(result)
    }
    
    @Test
    fun `findNearestBlock returns block when within radius`() {
        val block = CampusBlock(
            id = "block1",
            name = "Main Building",
            coordinates = GeoPoint(40.5, -74.5),
            radius = 100.0
        )
        val blocks = listOf(block)
        
        // Location very close to block center
        val result = LocationValidator.findNearestBlock(40.50001, -74.50001, blocks)
        
        assertNotNull(result)
        assertEquals(block, result!!.block)
        assertTrue(result.isWithinRadius)
        assertTrue(result.distance < 100.0)
    }
    
    @Test
    fun `findNearestBlock returns closest block when outside all radii`() {
        val block1 = CampusBlock(
            id = "block1",
            name = "Building 1",
            coordinates = GeoPoint(40.5, -74.5),
            radius = 50.0
        )
        val block2 = CampusBlock(
            id = "block2",
            name = "Building 2",
            coordinates = GeoPoint(40.6, -74.6),
            radius = 50.0
        )
        val blocks = listOf(block1, block2)
        
        // Location closer to block1 but outside both radii
        val result = LocationValidator.findNearestBlock(40.51, -74.51, blocks)
        
        assertNotNull(result)
        assertEquals(block1, result!!.block)
        assertFalse(result.isWithinRadius)
    }
    
    @Test
    fun `calculateDistance returns correct distance`() {
        val point1 = GeoPoint(40.0, -74.0)
        val point2 = GeoPoint(40.0, -74.0) // Same point
        
        val distance = LocationValidator.calculateDistance(point1, point2)
        
        assertEquals(0.0, distance, 0.1)
    }
    
    @Test
    fun `calculateDistance returns non-zero for different points`() {
        val point1 = GeoPoint(40.0, -74.0)
        val point2 = GeoPoint(40.1, -74.1)
        
        val distance = LocationValidator.calculateDistance(point1, point2)
        
        assertTrue(distance > 0)
        assertTrue(distance < 20000) // Should be reasonable distance
    }
    
    @Test
    fun `sanitizeLocation clamps invalid coordinates`() {
        val location = createMockLocation(91.0, 181.0, 10.0f, System.currentTimeMillis())
        
        val sanitized = LocationValidator.sanitizeLocation(location)
        
        assertEquals(90.0, sanitized.latitude, 0.001)
        assertEquals(180.0, sanitized.longitude, 0.001)
    }
    
    @Test
    fun `sanitizeLocation removes poor accuracy`() {
        val location = createMockLocation(40.5, -74.5, 150.0f, System.currentTimeMillis())
        
        val sanitized = LocationValidator.sanitizeLocation(location)
        
        assertFalse(sanitized.hasAccuracy())
    }
    
    @Test
    fun `hasSufficientAccuracy returns true for good accuracy`() {
        val location = createMockLocation(40.5, -74.5, 15.0f, System.currentTimeMillis())
        
        assertTrue(LocationValidator.hasSufficientAccuracy(location))
    }
    
    @Test
    fun `hasSufficientAccuracy returns false for poor accuracy`() {
        val location = createMockLocation(40.5, -74.5, 150.0f, System.currentTimeMillis())
        
        assertFalse(LocationValidator.hasSufficientAccuracy(location))
    }
    
    @Test
    fun `hasSufficientAccuracy returns false for no accuracy`() {
        val location = createMockLocation(40.5, -74.5, null, System.currentTimeMillis())
        
        assertFalse(LocationValidator.hasSufficientAccuracy(location))
    }
    
    @Test
    fun `isLocationFresh returns true for recent location`() {
        val location = createMockLocation(40.5, -74.5, 10.0f, System.currentTimeMillis())
        
        assertTrue(LocationValidator.isLocationFresh(location))
    }
    
    @Test
    fun `isLocationFresh returns false for old location`() {
        val oldTime = System.currentTimeMillis() - (10 * 60 * 1000L) // 10 minutes ago
        val location = createMockLocation(40.5, -74.5, 10.0f, oldTime)
        
        assertFalse(LocationValidator.isLocationFresh(location))
    }
    
    @Test
    fun `getLocationQuality returns high score for good location`() {
        val location = createMockLocation(40.5, -74.5, 10.0f, System.currentTimeMillis())
        
        val quality = LocationValidator.getLocationQuality(location)
        
        assertTrue(quality > 80) // Should be high quality
    }
    
    @Test
    fun `getLocationQuality returns low score for poor location`() {
        val oldTime = System.currentTimeMillis() - (10 * 60 * 1000L) // 10 minutes ago
        val location = createMockLocation(50.0, -80.0, 150.0f, oldTime) // Outside campus, poor accuracy, old
        
        val quality = LocationValidator.getLocationQuality(location)
        
        assertTrue(quality < 50) // Should be low quality
    }
    
    @Test
    fun `validateCampusBlockLocation accepts valid block`() {
        val block = CampusBlock(
            id = "block1",
            name = "Main Building",
            coordinates = GeoPoint(40.5, -74.5),
            radius = 50.0
        )
        
        val result = LocationValidator.validateCampusBlockLocation(block)
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `validateCampusBlockLocation rejects block with invalid coordinates`() {
        val block = CampusBlock(
            id = "block1",
            name = "Main Building",
            coordinates = GeoPoint(91.0, 181.0), // Invalid coordinates
            radius = 50.0
        )
        
        val result = LocationValidator.validateCampusBlockLocation(block)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `validateCampusBlockLocation rejects block with invalid radius`() {
        val block = CampusBlock(
            id = "block1",
            name = "Main Building",
            coordinates = GeoPoint(40.5, -74.5),
            radius = -10.0 // Invalid radius
        )
        
        val result = LocationValidator.validateCampusBlockLocation(block)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("radius must be positive") })
    }
    
    @Test
    fun `validateCampusBlockLocation rejects block with too large radius`() {
        val block = CampusBlock(
            id = "block1",
            name = "Main Building",
            coordinates = GeoPoint(40.5, -74.5),
            radius = 1500.0 // Too large
        )
        
        val result = LocationValidator.validateCampusBlockLocation(block)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("radius is too large") })
    }
    
    private fun createMockLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        time: Long
    ): Location {
        val location = mock(Location::class.java)
        `when`(location.latitude).thenReturn(latitude)
        `when`(location.longitude).thenReturn(longitude)
        `when`(location.time).thenReturn(time)
        
        if (accuracy != null) {
            `when`(location.hasAccuracy()).thenReturn(true)
            `when`(location.accuracy).thenReturn(accuracy)
        } else {
            `when`(location.hasAccuracy()).thenReturn(false)
        }
        
        return location
    }
}