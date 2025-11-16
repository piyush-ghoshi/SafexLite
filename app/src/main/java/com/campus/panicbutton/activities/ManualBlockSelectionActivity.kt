package com.campus.panicbutton.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.campus.panicbutton.R
import com.campus.panicbutton.models.CampusBlock
import com.campus.panicbutton.services.FirebaseService

/**
 * Activity for manual campus block selection when GPS is unavailable
 * Displays a list of available campus blocks for user selection
 */
class ManualBlockSelectionActivity : AppCompatActivity() {
    
    private lateinit var firebaseService: FirebaseService
    private lateinit var progressBar: ProgressBar
    private lateinit var titleText: TextView
    private lateinit var blocksList: ListView
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    
    private var campusBlocks: List<CampusBlock> = emptyList()
    private var selectedBlock: CampusBlock? = null
    
    companion object {
        private const val TAG = "ManualBlockSelection"
        const val EXTRA_SELECTED_BLOCK = "selected_block"
        const val EXTRA_REASON = "selection_reason"
        const val REASON_GPS_UNAVAILABLE = "gps_unavailable"
        const val REASON_LOW_ACCURACY = "low_accuracy"
        const val REASON_USER_CHOICE = "user_choice"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_block_selection)
        
        initializeViews()
        initializeServices()
        setupUI()
        loadCampusBlocks()
    }
    
    private fun initializeViews() {
        progressBar = findViewById(R.id.progressBar)
        titleText = findViewById(R.id.titleText)
        blocksList = findViewById(R.id.blocksList)
        confirmButton = findViewById(R.id.confirmButton)
        cancelButton = findViewById(R.id.cancelButton)
    }
    
    private fun initializeServices() {
        firebaseService = FirebaseService()
    }
    
    private fun setupUI() {
        // Set title based on selection reason
        val reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_GPS_UNAVAILABLE
        when (reason) {
            REASON_GPS_UNAVAILABLE -> {
                titleText.text = "GPS Unavailable - Select Your Location"
            }
            REASON_LOW_ACCURACY -> {
                titleText.text = "GPS Accuracy Low - Confirm Your Location"
            }
            REASON_USER_CHOICE -> {
                titleText.text = "Select Campus Block"
            }
        }
        
        // Initially disable confirm button
        confirmButton.isEnabled = false
        
        // Set up button listeners
        confirmButton.setOnClickListener {
            confirmSelection()
        }
        
        cancelButton.setOnClickListener {
            cancelSelection()
        }
        
        // Set up list selection listener
        blocksList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (position < campusBlocks.size) {
                selectedBlock = campusBlocks[position]
                confirmButton.isEnabled = true
                Log.d(TAG, "Block selected: ${selectedBlock?.name}")
            }
        }
    }
    
    private fun loadCampusBlocks() {
        Log.d(TAG, "Loading campus blocks for manual selection")
        showLoading(true)
        
        firebaseService.getAllCampusBlocks()
            .addOnSuccessListener { blocks ->
                Log.d(TAG, "Loaded ${blocks.size} campus blocks")
                campusBlocks = blocks.sortedBy { it.name }
                setupBlocksList()
                showLoading(false)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to load campus blocks", exception)
                showError("Failed to load campus locations. Please try again.")
                showLoading(false)
            }
    }
    
    private fun setupBlocksList() {
        if (campusBlocks.isEmpty()) {
            showError("No campus blocks available. Please contact support.")
            return
        }
        
        val blockNames = campusBlocks.map { "${it.name}\n${it.description}" }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            blockNames
        )
        
        blocksList.adapter = adapter
        blocksList.choiceMode = ListView.CHOICE_MODE_SINGLE
    }
    
    private fun confirmSelection() {
        val selected = selectedBlock
        if (selected != null) {
            Log.d(TAG, "Confirming block selection: ${selected.name}")
            
            val resultIntent = Intent().apply {
                putExtra(EXTRA_SELECTED_BLOCK, selected)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } else {
            Toast.makeText(this, "Please select a campus block", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun cancelSelection() {
        Log.d(TAG, "Manual block selection cancelled")
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        blocksList.visibility = if (show) View.GONE else View.VISIBLE
        confirmButton.isEnabled = !show && selectedBlock != null
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, "Error: $message")
    }
}