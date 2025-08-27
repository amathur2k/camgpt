# CamGPT - Android Camera + AI Analysis System

A complete system consisting of an Android camera app and a REST API backend that uses OpenAI's GPT-4o Vision API for intelligent image analysis. Capture photos with your Android device and get AI-powered insights in real-time.

## 🏗️ Architecture

This project consists of two main components:

```
┌─────────────────┐    HTTPS/ngrok   ┌─────────────────┐    OpenAI API    ┌─────────────────┐
│   Android App   │ ──────────────►  │  Backend API    │ ───────────────► │   GPT-4o API   │
│                 │                  │                 │                  │                 │
│ • Camera UI     │                  │ • Image Upload  │                  │ • Vision Model  │
│ • Photo Capture │                  │ • File Handling │                  │ • Analysis      │
│ • Custom Prompts│ ◄──────────────  │ • OpenAI Client │ ◄─────────────── │ • Response      │
│ • Results Display│   JSON Response  │ • Auto Cleanup  │                  │                 │
└─────────────────┘                  └─────────────────┘                  └─────────────────┘
```

1. **Android App** (`/app`) - Kotlin-based camera interface with CameraX
2. **Backend API** (`/backend`) - Node.js REST API that processes images with OpenAI

## ✨ Features

- 📱 **Modern Android Camera App** with real-time preview using CameraX
- 📸 **One-tap photo capture** with intuitive material design UI
- 🌐 **Universal connectivity** via ngrok tunneling (works on emulator + physical devices)
- 🤖 **GPT-4o Vision Analysis** for intelligent image processing
- 💬 **Customizable AI prompts** with persistent settings
- ⚡ **Optimized timeouts** for reliable API communication
- 🛡️ **Robust error handling** and automatic file cleanup
- 🔒 **HTTPS support** through ngrok for secure communications

## 🚀 Quick Start

### Prerequisites
- **Node.js** (v14 or higher) for the backend
- **Android Studio** with Android SDK for the app
- **OpenAI API Key** from [OpenAI Platform](https://platform.openai.com/api-keys)
- **ngrok account** (free) from [ngrok.com](https://ngrok.com/)

### 1. 🔧 Backend Setup

#### Install Dependencies
```bash
cd backend
npm install
```

#### Configure Environment
```bash
# Copy environment template and edit
cp env.example .env
```

Add your OpenAI API key to `.env`:
```env
OPENAI_API_KEY=sk-your-actual-api-key-here
PORT=8080
```

#### Start Backend Server
```bash
npm run dev
```
Server will run on `http://localhost:8080`

### 2. 🌐 Setup ngrok Tunnel

#### Install and Configure ngrok
1. Download ngrok from [ngrok.com](https://ngrok.com/)
2. Sign up for a free account and get your auth token
3. Install ngrok and authenticate

#### Start ngrok Tunnel
```bash
# In a new terminal window
ngrok http 8080
```

Copy the HTTPS URL (e.g., `https://abc123.ngrok.io`)

### 3. 📱 Android App Setup

#### Configure Backend URL
1. Open `app/src/main/java/com/example/myapplication/MainActivity.kt`
2. Update the `backendBaseUrl` with your ngrok HTTPS URL:

```kotlin
private val backendBaseUrl = "https://your-ngrok-url.ngrok.io"
```

#### Build and Run
1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Connect an Android device or start an emulator
4. Click Run (▶️) to build and install the app

### 4. 🧪 Test the System

1. **Grant camera permission** when prompted
2. **Point camera** at something interesting
3. **Tap the red capture button**
4. **Wait for AI analysis** (may take 10-30 seconds)
5. **View results** in the dialog that appears

## 🔄 How It Works

1. **App Launch**: Camera preview starts immediately using CameraX
2. **Photo Capture**: User taps the red capture button to take a photo
3. **Image Upload**: Photo is sent to backend via HTTPS multipart form data through ngrok
4. **AI Processing**: Backend forwards image to OpenAI GPT-4o Vision API with custom prompt
5. **Response**: AI analysis is returned and displayed in an alert dialog
6. **Cleanup**: Backend automatically deletes uploaded files after processing

## 📋 System Flow

```
Android App → CameraX Preview → Capture Photo → 
ngrok HTTPS → Backend API → OpenAI GPT-4o Vision → 
JSON Response → Android Dialog → File Cleanup
```

## 📋 Project Structure

```
camgpt/
├── app/                          # Android Application
│   ├── src/main/
│   │   ├── java/.../MainActivity.kt    # Main camera activity
│   │   ├── res/layout/activity_main.xml # UI layout
│   │   └── AndroidManifest.xml         # App permissions
│   └── build.gradle.kts              # Android dependencies
├── backend/                      # REST API Backend
│   ├── server.js                 # Express.js server
│   ├── package.json             # Node.js dependencies
│   ├── Dockerfile               # Docker configuration
│   ├── docker-compose.yml       # Docker Compose setup
│   └── README.md                # Backend documentation
├── gradle/                      # Gradle configuration
└── README.md                    # This file
```

## 🔒 Permissions Required

### Android App
- **Camera**: To capture photos
- **Internet**: To communicate with backend API via HTTPS

### Backend API
- **Internet**: To communicate with OpenAI API
- **File System**: For temporary image storage (auto-cleanup)

## 🛠️ Technology Stack

### Android App
- **Language**: Kotlin
- **Camera**: CameraX (modern camera API)
- **HTTP Client**: OkHttp with extended timeouts
- **JSON Parsing**: Gson
- **UI**: Material Design Components
- **Architecture**: Single Activity with coroutines

### Backend API
- **Runtime**: Node.js
- **Framework**: Express.js
- **File Upload**: Multer
- **AI Integration**: OpenAI official client
- **Cross-Origin**: CORS enabled
- **Environment**: dotenv configuration

## ⚙️ Customization

### Android App

#### Change Backend URL
Update the `backendBaseUrl` in `MainActivity.kt`:
```kotlin
private val backendBaseUrl = "https://your-ngrok-url.ngrok.io"
```

#### Custom Analysis Prompts
- **In-app**: Tap the settings button (✏️) to modify prompts
- **Default**: Edit `defaultPrompt` variable in `MainActivity.kt`
- **Examples**:
  - `"Describe what you see in detail"`
  - `"Extract all text from this image"`
  - `"List all objects and their colors"`

#### Timeout Configuration
Adjust HTTP timeouts in `MainActivity.kt`:
```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()
```

### Backend API

#### Environment Variables
Configure in `.env` file:
```env
OPENAI_API_KEY=your-key
PORT=8080
NODE_ENV=production
```

#### Custom Analysis Types
Add specialized endpoints in `server.js`:
```javascript
// Text extraction endpoint
app.post('/api/extract-text', upload.single('image'), async (req, res) => {
  const prompt = "Extract all text from this image";
  // ... processing logic
});
```

#### Rate Limiting & Security
```javascript
const rateLimit = require('express-rate-limit');

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100 // requests per IP
});

app.use('/api/', limiter);
```

## 🚀 Deployment

### Backend Deployment

#### Local Development (Recommended)
```bash
# Terminal 1: Start backend
cd backend
npm run dev

# Terminal 2: Start ngrok tunnel
ngrok http 8080
```

#### Using Docker
```bash
cd backend

# Build and run
docker build -t camgpt-backend .
docker run -p 8080:8080 -e OPENAI_API_KEY=your-key camgpt-backend

# Or use docker-compose
echo "OPENAI_API_KEY=your-key" > .env
docker-compose up -d
```

#### Cloud Platforms
- **Heroku**: Deploy with environment variables
- **Railway**: Connect GitHub repo with env vars
- **Render**: Deploy Node.js service
- **DigitalOcean App Platform**: Use environment variables
- **AWS/GCP**: Deploy with proper environment configuration

### Android App Deployment
- **Development**: Install APK via Android Studio
- **Distribution**: Build signed APK or AAB for distribution
- **Google Play**: Follow standard Android publishing process

## 🐛 Troubleshooting

### Common Issues

#### Android App
| Issue | Solution |
|-------|----------|
| Camera permission denied | Grant camera permission in app settings |
| Timeout errors | Check ngrok tunnel is active and backend is running |
| Connection refused | Verify backend URL in `MainActivity.kt` |
| Build errors | Update Android SDK and sync Gradle |

#### Backend API
| Issue | Solution |
|-------|----------|
| Port 8080 in use | Kill process: `npx kill-port 8080` |
| OpenAI API errors | Verify API key and account credits |
| File upload fails | Check image size (max 10MB) |
| CORS errors | Ensure CORS is enabled in server.js |

#### ngrok Issues
| Issue | Solution |
|-------|----------|
| Tunnel expired | Restart ngrok and update Android app URL |
| 404 errors | Ensure backend is running on port 8080 |
| HTTPS required | Always use HTTPS ngrok URL in Android app |

## 💰 Cost Considerations

- **OpenAI GPT-4o Vision**: ~$0.01 per image analysis
- **ngrok**: Free tier with 2-hour sessions (paid plans available)
- **Hosting**: Free tiers available on most cloud platforms
- **Development**: Completely free for local development

### Cost Optimization Tips
- Monitor OpenAI API usage in dashboard
- Implement request caching for repeated images
- Add rate limiting to prevent abuse
- Use image compression before sending to API

## 🔒 Security Best Practices

- ✅ **Never commit API keys** to version control
- ✅ **Use environment variables** for all sensitive configuration
- ✅ **Enable HTTPS** via ngrok for secure communication
- ✅ **Implement rate limiting** for production APIs
- ✅ **Validate file uploads** (size, type, content)
- ✅ **Auto-cleanup uploaded files** after processing
- ✅ **Monitor API usage** and set spending limits

## 📊 Monitoring & Analytics

### Backend Monitoring
```javascript
// Add to server.js for basic logging
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);
  next();
});
```

### ngrok Dashboard
- Access ngrok web interface at `http://localhost:4040`
- Monitor requests, response times, and errors
- Useful for debugging API communication

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly on both Android and backend
5. Submit a pull request

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
