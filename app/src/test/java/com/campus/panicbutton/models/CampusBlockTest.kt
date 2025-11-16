package com.campus.panicbutton.models

import com.google.firebase.firestore.GeoPoint
import org.junit.Test
import org.junit.Assert.*

class CampusBlockTest {

    @Test
    fun `campus block creation with default values`() {
        val block = CampusBlock()
        
        assertEquals("", block.id)
        assertEquals("", block.name)
        assertEquals("", block.description)
        assertEquals(GeoPoint(0.0, 0.0), block.coordinates)
        assertEquals(50.0, block.radius, 0.001)
    }

    @Test
    fun `campus block creation with all parameters`() {
        val coordinates = GeoPoint(40.7128, -74.0060)
        
        val block = CampusBlock(
            id = "block123",
            name = "Main Building",
            description = "Primary academic building",
            coordinates = coordinates,
            radius = 75.0
        )

        assertEquals("block123", block.id)
        assertEquals("Main Building", block.name)
        assertEquals("Primary academic building", block.description)
        assertEquals(coordinates, block.coordinates)
        assertEquals(75.0, block.radius, 0.001)
    }

    @Test
    fun `campus block coordinates validation`() {
        val validCoordinates = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(40.7128, -74.0060), // NYC
            GeoPoint(51.5074, -0.1278),  // London
            GeoPoint(-33.8688, 151.2093) // Sydney
        )

        validCoordinates.forEach { coords ->
            val block = CampusBlock(coordinates = coords)
            assertEquals(coords.latitude, block.coordinates.latitude, 0.0001)
            assertEquals(coords.longitude, block.coordinates.longitude, 0.0001)
        }
    }

    @Test
    fun `campus block radius validation`() {
        val validRadii = listOf(10.0, 25.0, 50.0, 100.0, 200.0)
        
        validRadii.forEach { radius ->
            val block = CampusBlock(radius = radius)
            assertEquals(radius, block.radius, 0.001)
        }
    }

    @Test
    fun `campus block name validation`() {
        val validNames = listOf(
            "Main Building",
            "Library",
            "Student Center",
            "Parking Lot A",
            "Athletic Complex"
        )

        validNames.forEach { name ->
            val block = CampusBlock(name = name)
            assertEquals(name, block.name)
            assertFalse(block.name.isEmpty())
        }
    }

    @Test
    fun `campus block description validation`() {
        val block1 = CampusBlock(description = "")
        val block2 = CampusBlock(description = "Academic building with classrooms")
        
        assertTrue(block1.description.isEmpty())
        assertFalse(block2.description.isEmpty())
        assertEquals("Academic building with classrooms", block2.description)
    }

    @Test
    fun `campus block equality`() {
        val coordinates = GeoPoint(40.7128, -74.0060)
        
        val block1 = CampusBlock(
            id = "block123",
            name = "Main Building",
            coordinates = coordinates,
            radius = 50.0
        )
        
        val block2 = CampusBlock(
            id = "block123",
            name = "Main Building",
            coordinates = coordinates,
            radius = 50.0
        )
        
        val block3 = CampusBlock(
            id = "block456",
            name = "Library",
            coordinates = coordinates,
            radius = 50.0
        )

        assertEquals(block1, block2)
        assertNotEquals(block1, block3)
    }
}