package com.campus.panicbutton.utils

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import java.util.concurrent.Executor

/**
 * Mock DocumentReference for offline mode
 * This is a minimal implementation that only provides the ID and path
 */
class MockDocumentReference(private val documentId: String) : DocumentReference() {
    
    override fun getId(): String = documentId
    
    override fun getPath(): String = "alerts/$documentId"
    
    override fun getParent(): CollectionReference {
        throw UnsupportedOperationException("Mock DocumentReference - getParent() not implemented")
    }
    
    override fun collection(collectionPath: String): CollectionReference {
        throw UnsupportedOperationException("Mock DocumentReference - collection() not implemented")
    }
    
    override fun set(data: Any): Task<Void> {
        throw UnsupportedOperationException("Mock DocumentReference - set() not implemented")
    }
    
    override fun set(data: Any, options: SetOptions): Task<Void> {
        throw UnsupportedOperationException("Mock DocumentReference - set() not implemented")
    }
    
    override fun update(data: Map<String, Any>): Task<Void> {
        throw UnsupportedOperationException("Mock DocumentReference - update() not implemented")
    }
    
    override fun update(field: String, value: Any?, vararg moreFieldsAndValues: Any?): Task<Void> {
        throw UnsupportedOperationException("Mock DocumentReference - update() not implemented")
    }
    
    override fun update(fieldPath: FieldPath, value: Any?, vararg moreFieldsAndValues: Any?): Task<Void> {
        throw UnsupportedOperationException("Mock DocumentReference - update() not implemented")
    }
    
    override fun delete(): Task<Void> {
        throw UnsupportedOperationException("Mock DocumentReference - delete() not implemented")
    }
    
    override fun get(): Task<DocumentSnapshot> {
        throw UnsupportedOperationException("Mock DocumentReference - get() not implemented")
    }
    
    override fun get(source: Source): Task<DocumentSnapshot> {
        throw UnsupportedOperationException("Mock DocumentReference - get() not implemented")
    }
    
    override fun addSnapshotListener(listener: EventListener<DocumentSnapshot>): ListenerRegistration {
        throw UnsupportedOperationException("Mock DocumentReference - addSnapshotListener() not implemented")
    }
    
    override fun addSnapshotListener(executor: Executor, listener: EventListener<DocumentSnapshot>): ListenerRegistration {
        throw UnsupportedOperationException("Mock DocumentReference - addSnapshotListener() not implemented")
    }
    
    override fun addSnapshotListener(activity: android.app.Activity, listener: EventListener<DocumentSnapshot>): ListenerRegistration {
        throw UnsupportedOperationException("Mock DocumentReference - addSnapshotListener() not implemented")
    }
    
    override fun getFirestore(): FirebaseFirestore {
        throw UnsupportedOperationException("Mock DocumentReference - getFirestore() not implemented")
    }
    
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