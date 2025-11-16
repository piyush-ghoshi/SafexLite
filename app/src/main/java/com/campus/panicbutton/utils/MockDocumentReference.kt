package com.campus.panicbutton.utils

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import java.util.concurrent.Executor

/**
 * Mock DocumentReference for offline mode
 * This is a minimal implementation that only provides the ID and path
 */
class MockDocumentReference(private val documentId: String) {
    
    fun getId(): String = documentId
    
    fun getPath(): String = "alerts/$documentId"
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MockDocumentReference) return false
        return documentId == other.documentId
    }
    
    override fun hashCode(): Int {
        return documentId.hashCode()
    }
    
    override fun toString(): String {
        return "MockDocumentReference(id=$documentId, path=${getPath()})"
    }
}