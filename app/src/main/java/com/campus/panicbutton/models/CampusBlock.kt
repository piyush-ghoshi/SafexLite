package com.campus.panicbutton.models

import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.firestore.GeoPoint

/**
 * Data class representing a campus block/location with GPS coordinates
 * and radius for mapping user locations to predefined campus areas.
 */
data class CampusBlock(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val coordinates: GeoPoint = GeoPoint(0.0, 0.0),
    val radius: Double = 50.0 // meters
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        GeoPoint(parcel.readDouble(), parcel.readDouble()),
        parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(description)
        parcel.writeDouble(coordinates.latitude)
        parcel.writeDouble(coordinates.longitude)
        parcel.writeDouble(radius)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<CampusBlock> {
        override fun createFromParcel(parcel: Parcel): CampusBlock {
            return CampusBlock(parcel)
        }

        override fun newArray(size: Int): Array<CampusBlock?> {
            return arrayOfNulls(size)
        }
    }
}