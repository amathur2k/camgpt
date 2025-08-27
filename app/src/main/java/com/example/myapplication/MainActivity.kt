package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.Button
import android.widget.ScrollView
import android.view.ViewGroup
import android.view.View
import android.text.TextWatcher
import android.text.Editable
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sharedPreferences: SharedPreferences
    private var imageCapture: ImageCapture? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // Backend API configuration - Using ngrok for universal access
    // Replace this URL with your actual ngrok HTTPS URL
    private val backendBaseUrl = "https://tightly-ultimate-crayfish.ngrok-free.app"
    
    // Alternative URLs (comment out when using ngrok):
    // private val backendBaseUrl = "http://10.0.2.2:8080" // For emulator
    // private val backendBaseUrl = "http://192.168.201.141:8080" // For physical device
    
    // Data class for conditional prompts
    data class ConditionalPrompt(
        var condition: String,
        var action: String,
        var id: String = java.util.UUID.randomUUID().toString()
    )
    
    // Default conditional prompts
    private val defaultConditionalPrompts = mutableListOf(
        ConditionalPrompt("a movie poster or movie title", "return the movie name and IMDB rating"),
        ConditionalPrompt("text or writing", "extract and transcribe all visible text"),
        ConditionalPrompt("objects or items", "list all objects and their colors"),
        ConditionalPrompt("anything else", "describe what you see in detail")
    )
    
    // Current conditional prompts - dynamically loaded
    private var conditionalPrompts: MutableList<ConditionalPrompt> = mutableListOf()
    
    // Generate final prompt from conditional prompts
    private fun generateFinalPrompt(): String {
        if (conditionalPrompts.isEmpty()) {
            return "Describe what you see in this image"
        }
        
        val promptBuilder = StringBuilder()
        promptBuilder.append("Analyze this image and follow these instructions in order:\n\n")
        
        conditionalPrompts.forEachIndexed { index, prompt ->
            promptBuilder.append("${index + 1}. If you find ${prompt.condition}, then ${prompt.action}.\n")
        }
        
        promptBuilder.append("\nProvide only answers to what is asked for, be precise and to the point.")
        return promptBuilder.toString()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to use this app", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("CamGPTPrefs", Context.MODE_PRIVATE)
        
        // Load saved conditional prompts or use default
        loadConditionalPrompts()
        
        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        settingsButton = findViewById(R.id.settingsButton)
        statusText = findViewById(R.id.statusText)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Initially disable button until camera is ready
        captureButton.isEnabled = false
        statusText.text = "Initializing camera..."
        
        // Set up capture button click listener
        captureButton.setOnClickListener {
            capturePhoto()
        }
        
        // Set up settings button click listener
        settingsButton.setOnClickListener {
            showPromptEditDialog()
        }
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                
                // Camera is ready, enable capture button
                captureButton.isEnabled = true
                statusText.text = "Tap the button to capture photo"

            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
                statusText.text = "Camera initialization failed"
                captureButton.isEnabled = false
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        // Disable button and update status
        captureButton.isEnabled = false
        statusText.text = "Capturing photo..."

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            createTempFile("photo", ".jpg", cacheDir)
        ).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(this@MainActivity, "Photo capture failed", Toast.LENGTH_SHORT).show()
                    
                    // Re-enable button
                    captureButton.isEnabled = true
                    statusText.text = "Tap the button to capture photo"
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("CameraX", "Photo saved successfully")
                    
                    statusText.text = "Analyzing with AI..."
                    
                    // Send image to backend API
                    val photoFile = output.savedUri?.path?.let { java.io.File(it) }
                    photoFile?.let { file ->
                        sendImageToBackend(file)
                    }
                }
            }
        )
    }

    private fun sendImageToBackend(imageFile: java.io.File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Create multipart form data with image and prompt
                val finalPrompt = generateFinalPrompt()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("prompt", finalPrompt)
                    .addFormDataPart(
                        "image", 
                        imageFile.name,
                        RequestBody.create("image/jpeg".toMediaType(), imageFile)
                    )
                    .build()
                
                // Create request to backend API
                val request = Request.Builder()
                    .url("$backendBaseUrl/api/analyze-image")
                    .post(requestBody)
                    .build()
                
                // Make API call
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val backendResponse = parseBackendResponse(responseBody)
                    
                    withContext(Dispatchers.Main) {
                        showAnalysisResponse(backendResponse)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Backend API call failed: ${response.code}", Toast.LENGTH_LONG).show()
                        // Re-enable button on error
                        captureButton.isEnabled = true
                        statusText.text = "Tap the button to capture photo"
                    }
                }
                
            } catch (e: Exception) {
                Log.e("Backend", "Error sending image to backend", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    // Re-enable button on error
                    captureButton.isEnabled = true
                    statusText.text = "Tap the button to capture photo"
                }
            }
        }
    }

    private fun parseBackendResponse(responseBody: String?): String {
        return try {
            val response = Gson().fromJson(responseBody, BackendResponse::class.java)
            response.analysis ?: "No analysis received"
        } catch (e: Exception) {
            responseBody ?: "Error parsing response: ${e.message}"
        }
    }

    private fun showAnalysisResponse(response: String) {
        // Log the full response
        Log.d("BACKEND_RESPONSE", response)
        
        // Show in a dialog
        showResponseDialog(response)
    }

    private fun showResponseDialog(response: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("AI Analysis")
            .setMessage(response)
            .setPositiveButton("Take Another Photo") { _, _ ->
                // Re-enable capture button for another photo
                captureButton.isEnabled = true
                statusText.text = "Tap the button to capture photo"
            }
            .setNegativeButton("Close") { _, _ ->
                finish()
            }
            .show()
    }
    
    private fun showPromptEditDialog() {
        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        
        // Title and instructions
        val instructionText = TextView(this).apply {
            text = "Define conditional analysis rules. The AI will check each condition in order:"
            setPadding(0, 0, 0, 24)
            textSize = 14f
        }
        mainLayout.addView(instructionText)
        
        // Container for prompt items
        val promptContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Add existing prompts
        conditionalPrompts.forEach { prompt ->
            promptContainer.addView(createPromptItemView(prompt, promptContainer))
        }
        
        mainLayout.addView(promptContainer)
        
        // Add new prompt button
        val addButton = Button(this).apply {
            text = "Add New Rule"
            setOnClickListener {
                val newPrompt = ConditionalPrompt("", "")
                conditionalPrompts.add(newPrompt)
                promptContainer.addView(createPromptItemView(newPrompt, promptContainer))
            }
        }
        mainLayout.addView(addButton)
        
        scrollView.addView(mainLayout)
        
        AlertDialog.Builder(this)
            .setTitle("Edit Analysis Rules")
            .setView(scrollView)
            .setPositiveButton("Save") { _, _ ->
                // Remove empty prompts
                conditionalPrompts.removeAll { it.condition.isBlank() || it.action.isBlank() }
                saveConditionalPrompts()
                Toast.makeText(this, "Rules saved successfully!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset to Default") { _, _ ->
                conditionalPrompts.clear()
                conditionalPrompts.addAll(defaultConditionalPrompts.map { it.copy() })
                saveConditionalPrompts()
                Toast.makeText(this, "Reset to default rules", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun createPromptItemView(prompt: ConditionalPrompt, container: LinearLayout): View {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        
        // "If you find" row
        val ifLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val ifLabel = TextView(this).apply {
            text = "If you find: "
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val conditionEdit = EditText(this).apply {
            setText(prompt.condition)
            hint = "e.g., a movie poster or text"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    prompt.condition = s.toString()
                }
            })
        }
        
        ifLayout.addView(ifLabel)
        ifLayout.addView(conditionEdit)
        
        // "Then return" row
        val thenLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val thenLabel = TextView(this).apply {
            text = "Then: "
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val actionEdit = EditText(this).apply {
            setText(prompt.action)
            hint = "e.g., return the movie name and IMDB rating"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    prompt.action = s.toString()
                }
            })
        }
        
        thenLayout.addView(thenLabel)
        thenLayout.addView(actionEdit)
        
        // Control buttons row
        val controlLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val moveUpButton = Button(this).apply {
            text = "UP"
            textSize = 10f
            setBackgroundColor(0xFF4CAF50.toInt()) // Green
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(80, 60).apply {
                marginEnd = 8
            }
            setOnClickListener {
                val index = conditionalPrompts.indexOf(prompt)
                if (index > 0) {
                    conditionalPrompts.removeAt(index)
                    conditionalPrompts.add(index - 1, prompt)
                    refreshPromptContainer(container)
                }
            }
        }
        
        val moveDownButton = Button(this).apply {
            text = "DOWN"
            textSize = 10f
            setBackgroundColor(0xFF2196F3.toInt()) // Blue
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(80, 60).apply {
                marginEnd = 8
            }
            setOnClickListener {
                val index = conditionalPrompts.indexOf(prompt)
                if (index < conditionalPrompts.size - 1) {
                    conditionalPrompts.removeAt(index)
                    conditionalPrompts.add(index + 1, prompt)
                    refreshPromptContainer(container)
                }
            }
        }
        
        val deleteButton = Button(this).apply {
            text = "Delete"
            textSize = 12f
            setBackgroundColor(0xFFFF6B6B.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 16
            }
            setOnClickListener {
                conditionalPrompts.remove(prompt)
                container.removeView(itemLayout)
            }
        }
        
        controlLayout.addView(moveUpButton)
        controlLayout.addView(moveDownButton)
        controlLayout.addView(deleteButton)
        
        itemLayout.addView(ifLayout)
        itemLayout.addView(thenLayout)
        itemLayout.addView(controlLayout)
        
        return itemLayout
    }
    
    private fun refreshPromptContainer(container: LinearLayout) {
        container.removeAllViews()
        conditionalPrompts.forEach { prompt ->
            container.addView(createPromptItemView(prompt, container))
        }
    }
    
    private fun loadConditionalPrompts() {
        val savedPromptsJson = sharedPreferences.getString("conditional_prompts", null)
        if (savedPromptsJson != null) {
            try {
                val promptsArray = Gson().fromJson(savedPromptsJson, Array<ConditionalPrompt>::class.java)
                conditionalPrompts.clear()
                conditionalPrompts.addAll(promptsArray)
            } catch (e: Exception) {
                Log.e("LoadPrompts", "Error loading prompts", e)
                conditionalPrompts.clear()
                conditionalPrompts.addAll(defaultConditionalPrompts.map { it.copy() })
            }
        } else {
            conditionalPrompts.clear()
            conditionalPrompts.addAll(defaultConditionalPrompts.map { it.copy() })
        }
    }
    
    private fun saveConditionalPrompts() {
        val promptsJson = Gson().toJson(conditionalPrompts)
        sharedPreferences.edit()
            .putString("conditional_prompts", promptsJson)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // Data class for Backend API response
    data class BackendResponse(
        val analysis: String?,
        val status: String? = null
    )
}
