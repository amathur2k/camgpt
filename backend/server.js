const express = require('express');
const multer = require('multer');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

const OpenAI = require('openai');

const app = express();
const PORT = process.env.PORT || 8080;

// Initialize OpenAI client
const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

// Middleware
app.use(cors());
app.use(express.json());

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

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({
    status: 'OK',
    message: 'CamGPT Backend API is running',
    timestamp: new Date().toISOString()
  });
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

    // Call OpenAI GPT-4o Vision API
    const response = await openai.chat.completions.create({
      model: "gpt-4o",
      messages: [
        {
          role: "user",
          content: [
            { type: "text", text: prompt },
            {
              type: "image_url",
              image_url: {
                url: `data:image/jpeg;base64,${base64Image}`,
              },
            },
          ],
        },
      ],
      max_tokens: 300,
    });

    const analysis = response.choices[0]?.message?.content || 'No analysis received';

    // Clean up uploaded file
    cleanupFile(uploadedFilePath);

    // Return successful response
    res.json({
      analysis: analysis,
      status: 'success',
      model: 'gpt-4o'
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
  console.log(`📋 Health check: http://localhost:${PORT}/api/health`);
  console.log(`🤖 Image analysis: POST http://localhost:${PORT}/api/analyze-image`);
  console.log(`🌐 Also available on your network at http://0.0.0.0:${PORT}`);
  
  if (!process.env.OPENAI_API_KEY) {
    console.warn('⚠️  WARNING: OPENAI_API_KEY environment variable not set!');
  }
});
