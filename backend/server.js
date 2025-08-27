const express = require('express');
const multer = require('multer');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

const AgentService = require('./agentService');

const app = express();
const PORT = process.env.PORT || 8080;

// Initialize Agent Service with both OpenAI and Tavily
const agentService = new AgentService(
  process.env.OPENAI_API_KEY,
  process.env.TAVILY_API_KEY
);

// Middleware
app.use(cors());
app.use(express.json());

// Serve static files (for the test frontend)
app.use(express.static('public'));

// Configure multer for file uploads
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const uploadDir = 'uploads/';
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    // Generate unique filename with timestamp
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({
  storage: storage,
  limits: {
    fileSize: 10 * 1024 * 1024, // 10MB limit
  },
  fileFilter: (req, file, cb) => {
    // Accept only image files
    if (file.mimetype.startsWith('image/')) {
      cb(null, true);
    } else {
      cb(new Error('Only image files are allowed!'), false);
    }
  }
});

// Utility function to convert image to base64
function imageToBase64(imagePath) {
  try {
    const imageBuffer = fs.readFileSync(imagePath);
    return imageBuffer.toString('base64');
  } catch (error) {
    throw new Error(`Failed to read image: ${error.message}`);
  }
}

// Utility function to clean up uploaded files
function cleanupFile(filePath) {
  try {
    if (fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
      console.log(`Cleaned up file: ${filePath}`);
    }
  } catch (error) {
    console.error(`Failed to cleanup file ${filePath}:`, error.message);
  }
}

// Health check endpoint with agent service status
app.get('/api/health', async (req, res) => {
  try {
    const agentHealth = await agentService.healthCheck();
    res.json({
      status: 'OK',
      message: 'CamGPT Backend API is running',
      timestamp: new Date().toISOString(),
      services: agentHealth
    });
  } catch (error) {
    res.status(500).json({
      status: 'ERROR',
      message: 'Health check failed',
      error: error.message,
      timestamp: new Date().toISOString()
    });
  }
});

// Main endpoint for image analysis
app.post('/api/analyze-image', upload.single('image'), async (req, res) => {
  let uploadedFilePath = null;
  
  try {
    // Validate request
    if (!req.file) {
      return res.status(400).json({
        error: 'No image file provided',
        status: 'error'
      });
    }

    const prompt = req.body.prompt || 'Describe what you see in this image';
    uploadedFilePath = req.file.path;
    
    console.log(`Processing image: ${req.file.filename}`);
    console.log(`Prompt: ${prompt}`);

    // Convert image to base64
    const base64Image = imageToBase64(uploadedFilePath);

    // Use agentic flow for enhanced analysis
    const agentResult = await agentService.analyzeWithAgent(base64Image, prompt);

    // Clean up uploaded file
    cleanupFile(uploadedFilePath);

    // Return enhanced response with agent metadata
    res.json({
      analysis: agentResult.finalAnswer,
      status: 'success',
      model: 'gpt-4o',
      agent: {
        webSearchUsed: agentResult.webSearchUsed,
        searchQuery: agentResult.searchQuery || null,
        iterations: agentResult.iterations,
        webResults: agentResult.webResults || null
      }
    });

    console.log('Analysis completed successfully');

  } catch (error) {
    console.error('Error processing image:', error);
    
    // Clean up uploaded file in case of error
    if (uploadedFilePath) {
      cleanupFile(uploadedFilePath);
    }

    // Handle different types of errors
    if (error.code === 'insufficient_quota') {
      res.status(402).json({
        error: 'OpenAI API quota exceeded',
        status: 'error'
      });
    } else if (error.code === 'invalid_api_key') {
      res.status(401).json({
        error: 'Invalid OpenAI API key',
        status: 'error'
      });
    } else if (error.message.includes('Failed to read image')) {
      res.status(400).json({
        error: 'Invalid image file',
        status: 'error'
      });
    } else {
      res.status(500).json({
        error: error.message || 'Internal server error',
        status: 'error'
      });
    }
  }
});

// Test endpoint for agentic flow (for debugging)
app.post('/api/test-agent', async (req, res) => {
  try {
    const { prompt, requireWebSearch } = req.body;
    
    if (!prompt) {
      return res.status(400).json({
        error: 'Prompt is required',
        status: 'error'
      });
    }

    // Create a simple test image (1x1 pixel base64 encoded)
    const testImageBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';
    
    const agentResult = await agentService.analyzeWithAgent(testImageBase64, prompt);

    res.json({
      analysis: agentResult.finalAnswer,
      status: 'success',
      test: true,
      agent: {
        webSearchUsed: agentResult.webSearchUsed,
        searchQuery: agentResult.searchQuery || null,
        iterations: agentResult.iterations,
        webResults: agentResult.webResults || null
      }
    });

  } catch (error) {
    console.error('Test agent error:', error);
    res.status(500).json({
      error: error.message || 'Test agent failed',
      status: 'error'
    });
  }
});

// Error handling middleware
app.use((error, req, res, next) => {
  if (error instanceof multer.MulterError) {
    if (error.code === 'LIMIT_FILE_SIZE') {
      return res.status(400).json({
        error: 'File too large. Maximum size is 10MB',
        status: 'error'
      });
    }
  }
  
  res.status(500).json({
    error: error.message || 'Something went wrong!',
    status: 'error'
  });
});

// 404 handler
app.use('*', (req, res) => {
  res.status(404).json({
    error: 'Endpoint not found',
    status: 'error'
  });
});

// Start server
app.listen(PORT, () => {
  console.log(`🚀 CamGPT Backend API running on http://localhost:${PORT}`);
  console.log(`🧪 Test Frontend: http://localhost:${PORT}`);
  console.log(`📋 Health check: http://localhost:${PORT}/api/health`);
  console.log(`🤖 Image analysis: POST http://localhost:${PORT}/api/analyze-image`);
  console.log(`🌐 Also available on your network at http://0.0.0.0:${PORT}`);
  console.log(`🔍 Agentic AI with web search capabilities enabled`);
  
  if (!process.env.OPENAI_API_KEY) {
    console.warn('⚠️  WARNING: OPENAI_API_KEY environment variable not set!');
  }
  
  if (!process.env.TAVILY_API_KEY) {
    console.warn('⚠️  WARNING: TAVILY_API_KEY not set - web search will be disabled');
  } else {
    console.log('✅ Tavily web search integration active');
  }
});
