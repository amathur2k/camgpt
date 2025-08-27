# CamGPT - Android Camera + GPT-4o Integration

An Android app that automatically launches the camera when started, captures a photo, and sends it to OpenAI's GPT-4o with a custom prompt for analysis.

## Features

- 🚀 **Auto-launch camera** on app startup
- 📸 **Automatic photo capture** after 2 seconds
- 🤖 **GPT-4o integration** for intelligent image analysis
- 💬 **Custom prompts** for specific analysis needs
- 📱 **Modern Android UI** with camera preview

## Setup Instructions

### 1. OpenAI API Key Setup

1. Get your OpenAI API key from [OpenAI Platform](https://platform.openai.com/api-keys)
2. Open `app/src/main/java/com/example/myapplication/MainActivity.kt`
3. Replace `YOUR_OPENAI_API_KEY_HERE` with your actual API key:

```kotlin
private val openAiApiKey = "sk-your-actual-api-key-here"
```

### 2. Customize the Prompt (Optional)

You can modify the analysis prompt in `MainActivity.kt`:

```kotlin
private val customPrompt = "Your custom prompt here..."
```

### 3. Build and Run

1. Open the project in Android Studio
2. Connect an Android device or start an emulator
3. Build and run the app

## How It Works

1. **App Launch**: Camera preview starts immediately
2. **Auto-Capture**: Photo is taken automatically after 2 seconds
3. **Processing**: Image is resized and converted to base64
4. **API Call**: Sent to GPT-4o with your custom prompt
5. **Response**: GPT-4o analysis is displayed in a dialog

## App Flow

```
App Launch → Camera Preview → Auto-Capture (2s) → Process Image → GPT-4o API → Show Response
```

## Permissions Required

- **Camera**: To capture photos
- **Internet**: To communicate with OpenAI API
- **Storage**: For temporary image files

## Dependencies

- **CameraX**: Modern Android camera API
- **OkHttp**: HTTP client for API calls
- **Gson**: JSON parsing
- **AndroidX**: Modern Android components

## Customization

### Change Auto-Capture Delay

Modify the delay in `startCamera()`:

```kotlin
previewView.postDelayed({
    capturePhoto()
}, 5000) // 5 seconds instead of 2
```

### Add Manual Capture Button

Add a capture button to the layout and bind it to `capturePhoto()`.

### Custom Analysis

Modify the `customPrompt` variable to change what GPT-4o analyzes:

- Object detection: "List all objects you can see in this image"
- Text extraction: "Extract and transcribe any text visible in this image"
- Scene description: "Describe the setting and atmosphere of this scene"

## Troubleshooting

1. **Camera not working**: Ensure camera permissions are granted
2. **API calls failing**: Check your OpenAI API key and internet connection
3. **Build errors**: Make sure Android SDK and dependencies are up to date

## Cost Considerations

- GPT-4o vision API costs vary based on image size and resolution
- Images are automatically resized to 512px to reduce costs
- Consider implementing usage limits for production apps

## Security Notes

- Never commit API keys to version control
- Consider using environment variables or secure storage for API keys
- Implement proper error handling for production use
