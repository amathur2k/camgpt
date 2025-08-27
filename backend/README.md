# CamGPT Backend API

An intelligent REST API backend with agentic AI capabilities that combines image analysis with real-time web search.

## 🤖 Agentic AI Features

- 🖼️ **Image Upload**: Accepts image files via multipart form data
- 🧠 **Intelligent Agent**: AI agent that decides when web search is needed
- 🔍 **Web Search Integration**: Uses Tavily API for real-time information
- 🤖 **GPT-4o Vision**: Advanced image analysis capabilities
- 🔧 **Smart Prompts**: Agent automatically enhances analysis with web data
- 🚀 **Auto Cleanup**: Automatically removes uploaded files after processing
- 🛡️ **Error Handling**: Comprehensive error handling with meaningful responses
- 📊 **Agent Transparency**: Returns metadata about agent decisions and web searches

## API Endpoints

### Health Check
```
GET /api/health
```
Returns server status and current timestamp.

### Analyze Image (with Agentic AI)
```
POST /api/analyze-image
Content-Type: multipart/form-data

Fields:
- image: Image file (required)
- prompt: Custom analysis prompt (optional)
```

**Example Response (without web search):**
```json
{
  "analysis": "I can see a movie poster for 'Inception'...",
  "status": "success",
  "model": "gpt-4o",
  "agent": {
    "webSearchUsed": false,
    "searchQuery": null,
    "iterations": 1,
    "webResults": null
  }
}
```

**Example Response (with web search):**
```json
{
  "analysis": "This is the movie poster for 'Inception' (2010). Current IMDB rating: 8.8/10 with over 2 million reviews. The film grossed $836 million worldwide and is available for streaming on Netflix as of 2024...",
  "status": "success",
  "model": "gpt-4o",
  "agent": {
    "webSearchUsed": true,
    "searchQuery": "Inception movie IMDB rating reviews 2024",
    "iterations": 2,
    "webResults": [
      {
        "title": "Inception (2010) - IMDb",
        "url": "https://www.imdb.com/title/tt1375666/",
        "content": "IMDB rating 8.8/10..."
      }
    ]
  }
}
```

### Test Agent (Development)
```
POST /api/test-agent
Content-Type: application/json

Body:
{
  "prompt": "What is the current stock price of Apple?"
}
```

## Setup Instructions

### 1. Install Dependencies
```bash
cd backend
npm install
```

### 2. Environment Configuration
```bash
# Copy environment template
cp env.example .env

# Edit .env file and add your API keys
nano .env
```

Add your API keys to the `.env` file:
```
OPENAI_API_KEY=sk-your-actual-api-key-here
TAVILY_API_KEY=tvly-your-tavily-api-key-here
PORT=8080
```

**Get your API keys:**
- **OpenAI**: [OpenAI Platform](https://platform.openai.com/api-keys)
- **Tavily**: [Tavily API](https://tavily.com/) (free tier available)

### 3. Run the Server

**Development (with auto-restart):**
```bash
npm run dev
```

**Production:**
```bash
npm start
```

The server will start on `http://0.0.0.0:8080` (or your specified PORT).

## Testing the API

### Using curl
```bash
# Health check
curl http://localhost:8080/api/health

# Analyze image
curl -X POST http://localhost:8080/api/analyze-image \
  -F "image=@/path/to/your/image.jpg" \
  -F "prompt=Describe this image in detail"
```

### Using a REST client (Postman, Insomnia, etc.)
1. Set method to `POST`
2. URL: `http://localhost:8080/api/analyze-image`
3. Body type: `form-data`
4. Add fields:
   - `image`: Select your image file
   - `prompt`: Enter your custom prompt (optional)

## Configuration

### Environment Variables
- `OPENAI_API_KEY`: Your OpenAI API key (required)
- `PORT`: Server port (default: 8080)
- `NODE_ENV`: Environment mode (development/production)

### File Upload Limits
- Maximum file size: 10MB
- Supported formats: All image types (jpg, png, gif, webp, etc.)
- Files are automatically cleaned up after processing

## Error Handling

The API returns appropriate HTTP status codes and error messages:

- `400 Bad Request`: Invalid request (no image, invalid file type, etc.)
- `401 Unauthorized`: Invalid OpenAI API key
- `402 Payment Required`: OpenAI API quota exceeded
- `404 Not Found`: Endpoint not found
- `500 Internal Server Error`: Server or processing error

## Deployment

### Using Docker (recommended)
```bash
# Build Docker image
docker build -t camgpt-backend .

# Run container
docker run -p 8080:8080 -e OPENAI_API_KEY=your-key camgpt-backend
```

### Using PM2 (for production)
```bash
# Install PM2 globally
npm install -g pm2

# Start with PM2
pm2 start server.js --name camgpt-backend

# Save PM2 configuration
pm2 save
pm2 startup
```

### Cloud Deployment
The backend can be deployed to:
- **Heroku**: Add `OPENAI_API_KEY` to config vars
- **AWS EC2/ECS**: Use environment variables
- **Google Cloud Run**: Configure environment variables
- **DigitalOcean App Platform**: Set environment variables

## Security Considerations

- Never commit your `.env` file to version control
- Use environment variables for API keys in production
- Consider implementing rate limiting for production use
- Add authentication if needed for your use case
- Use HTTPS in production environments

## Cost Optimization

- Images are sent directly to OpenAI without resizing (consider adding image compression)
- Set appropriate `max_tokens` limits to control costs
- Monitor your OpenAI API usage regularly
- Consider implementing caching for repeated analyses

## Troubleshooting

### Common Issues

1. **OpenAI API key errors**
   - Verify your API key is correct
   - Check your OpenAI account has sufficient credits
   - Ensure the API key has proper permissions

2. **File upload issues**
   - Check file size (max 10MB)
   - Verify file is a valid image format
   - Ensure proper multipart form data formatting

3. **Server not starting**
   - Check if port is already in use
   - Verify all dependencies are installed
   - Check Node.js version compatibility (requires Node.js 14+)

### Logs
The server logs all requests and errors to the console. In production, consider using a proper logging solution like Winston.
