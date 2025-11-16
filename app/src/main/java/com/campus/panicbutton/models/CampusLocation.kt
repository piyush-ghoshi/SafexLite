package com.campus.panicbutton.models

/**
 * Campus location structure for manual selection
 */
data class CampusLocation(
    val department: String,
    val location: String,
    val displayText: String = "$department - $location"
)

/**
 * Campus structure helper
 */
object CampusStructure {
    
    val departments = listOf(
        "AITR",
        "AIPER",
        "AIMSR",
        "AIL",
        "AFMR"
    )
    
    private val departmentLocations = mapOf(
        "AITR" to listOf(
            "Block A - Ground Floor",
            "Block A - 1st Floor",
            "Block A - 2nd Floor",
            "Block A - 3rd Floor",
            "Block B - Ground Floor",
            "Block B - 1st Floor",
            "Block B - 2nd Floor",
            "Block B - 3rd Floor",
            "Block C - Ground Floor",
            "Block C - 1st Floor",
            "Block C - 2nd Floor",
            "Block C - 3rd Floor",
            "Ground",
            "Garden",
            "Canteen",
            "Parking"
        ),
        "AIPER" to listOf(
            "Block A - Ground Floor",
            "Block A - 1st Floor",
            "Block A - 2nd Floor",
            "Block A - 3rd Floor",
            "Block B - Ground Floor",
            "Block B - 1st Floor",
            "Block B - 2nd Floor",
            "Block B - 3rd Floor",
            "Block C - Ground Floor",
            "Block C - 1st Floor",
            "Block C - 2nd Floor",
            "Block C - 3rd Floor",
            "Ground",
            "Garden",
            "Canteen",
            "Parking"
        ),
        "AIMSR" to listOf(
            "Block A - Ground Floor",
            "Block A - 1st Floor",
            "Block A - 2nd Floor",
            "Block A - 3rd Floor",
            "Block B - Ground Floor",
            "Block B - 1st Floor",
            "Block B - 2nd Floor",
            "Block B - 3rd Floor",
            "Block C - Ground Floor",
            "Block C - 1st Floor",
            "Block C - 2nd Floor",
            "Block C - 3rd Floor",
            "Ground",
            "Garden",
            "Canteen",
            "Parking"
        ),
        "AIL" to listOf(
            "Block A - Ground Floor",
            "Block A - 1st Floor",
            "Block A - 2nd Floor",
            "Block A - 3rd Floor",
            "Block B - Ground Floor",
            "Block B - 1st Floor",
            "Block B - 2nd Floor",
            "Block B - 3rd Floor",
            "Block C - Ground Floor",
            "Block C - 1st Floor",
            "Block C - 2nd Floor",
            "Block C - 3rd Floor",
            "Ground",
            "Garden",
            "Canteen",
            "Parking"
        ),
        "AFMR" to listOf(
            "Block A - Ground Floor",
            "Block A - 1st Floor",
            "Block A - 2nd Floor",
            "Block A - 3rd Floor",
            "Block B - Ground Floor",
            "Block B - 1st Floor",
            "Block B - 2nd Floor",
            "Block B - 3rd Floor",
            "Block C - Ground Floor",
            "Block C - 1st Floor",
            "Block C - 2nd Floor",
            "Block C - 3rd Floor",
            "Ground",
            "Garden",
            "Canteen",
            "Parking"
        )
    )
    
    // Common area accessible from all departments
    val commonAreas = listOf(
        "Bus Parking Area"
    )
    
    fun getLocationsForDepartment(department: String): List<String> {
        val deptLocations = departmentLocations[department] ?: emptyList()
        return deptLocations + commonAreas
    }
    
    fun getAllDepartments(): List<String> = departments
}
