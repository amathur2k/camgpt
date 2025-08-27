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

class MainActivity : ComponentActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sharedPreferences: SharedPreferences
    private var imageCapture: ImageCapture? = null
    private val client = OkHttpClient()
    
    // Replace with your OpenAI API key
    private val openAiApiKey = ""
    
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
                    
                    statusText.text = "Analyzing with GPT-4o..."
                    
                    // Convert image to base64 and send to GPT-4o
                    val photoFile = output.savedUri?.path?.let { java.io.File(it) }
                    photoFile?.let { file ->
                        processImageWithGPT(file)
                    }
                }
            }
        )
    }

    private fun processImageWithGPT(imageFile: java.io.File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Convert image to base64
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                val resizedBitmap = resizeBitmap(bitmap, 512) // Resize to reduce API costs
                val base64Image = bitmapToBase64(resizedBitmap)
                
                // Create OpenAI API request
                val request = createGPTRequest(base64Image)
                
                // Make API call
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val gptResponse = parseGPTResponse(responseBody)
                    
                    withContext(Dispatchers.Main) {
                        showGPTResponse(gptResponse)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "API call failed: ${response.code}", Toast.LENGTH_LONG).show()
                        // Re-enable button on error
                        captureButton.isEnabled = true
                        statusText.text = "Tap the button to capture photo"
                    }
                }
                
            } catch (e: Exception) {
                Log.e("GPT", "Error processing image", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    // Re-enable button on error
                    captureButton.isEnabled = true
                    statusText.text = "Tap the button to capture photo"
                }
            }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val scale = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }
        
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun createGPTRequest(base64Image: String): Request {
        val requestBody = GPTRequest(
            model = "gpt-4o",
            messages = listOf(
                GPTMessage(
                    role = "user",
                    content = listOf(
                        GPTContent(type = "text", text = customPrompt),
                        GPTContent(
                            type = "image_url",
                            imageUrl = GPTImageUrl("data:image/jpeg;base64,$base64Image")
                        )
                    )
                )
            ),
            maxTokens = 300
        )
        
        val json = Gson().toJson(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())
        
        return Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $openAiApiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
    }

    private fun parseGPTResponse(responseBody: String?): String {
        return try {
            val response = Gson().fromJson(responseBody, GPTResponse::class.java)
            response.choices.firstOrNull()?.message?.content ?: "No response received"
        } catch (e: Exception) {
            "Error parsing response: ${e.message}"
        }
    }

    private fun showGPTResponse(response: String) {
        // For now, show in a Toast. In a real app, you'd show this in a proper UI
        Toast.makeText(this, "GPT Response: $response", Toast.LENGTH_LONG).show()
        
        // Log the full response
        Log.d("GPT_RESPONSE", response)
        
        // You can also show in a dialog or navigate to a new screen
        showResponseDialog(response)
    }

    private fun showResponseDialog(response: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("GPT-4o Analysis")
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
            hint = "Enter your custom prompt for GPT-4o"
            minLines = 3
            maxLines = 8
            setPadding(32, 24, 32, 24)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Edit Custom Prompt")
            .setMessage("Customize how GPT-4o analyzes your photos:")
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

    // Data classes for OpenAI API
    data class GPTRequest(
        val model: String,
        val messages: List<GPTMessage>,
        @SerializedName("max_tokens") val maxTokens: Int
    )

    data class GPTMessage(
        val role: String,
        val content: List<GPTContent>
    )

    data class GPTContent(
        val type: String,
        val text: String? = null,
        @SerializedName("image_url") val imageUrl: GPTImageUrl? = null
    )

    data class GPTImageUrl(
        val url: String
    )

    data class GPTResponse(
        val choices: List<GPTChoice>
    )

    data class GPTChoice(
        val message: GPTResponseMessage
    )

    data class GPTResponseMessage(
        val content: String
    )
}
