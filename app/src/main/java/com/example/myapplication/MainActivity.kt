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
    
    // Default prompt - will be loaded from SharedPreferences
    private val defaultPrompt = "Extract any entities you see in the image, If any one of these is a movie, get its imdb rating"
    
    // Current prompt - dynamically loaded
    private var customPrompt: String = defaultPrompt

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
        
        // Load saved prompt or use default
        customPrompt = sharedPreferences.getString("custom_prompt", defaultPrompt) ?: defaultPrompt
        
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
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("prompt", customPrompt)
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
        val editText = EditText(this).apply {
            setText(customPrompt)
            hint = "Enter your custom prompt for AI analysis"
            minLines = 3
            maxLines = 8
            setPadding(32, 24, 32, 24)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Edit Custom Prompt")
            .setMessage("Customize how AI analyzes your photos:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newPrompt = editText.text.toString().trim()
                if (newPrompt.isNotEmpty()) {
                    customPrompt = newPrompt
                    savePromptToPreferences(newPrompt)
                    Toast.makeText(this, "Prompt saved successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Prompt cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset to Default") { _, _ ->
                customPrompt = defaultPrompt
                savePromptToPreferences(defaultPrompt)
                Toast.makeText(this, "Prompt reset to default", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun savePromptToPreferences(prompt: String) {
        sharedPreferences.edit()
            .putString("custom_prompt", prompt)
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
