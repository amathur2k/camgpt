const OpenAI = require('openai');
const axios = require('axios');

class AgentService {
  constructor(openaiApiKey, tavilyApiKey) {
    this.openai = new OpenAI({ apiKey: openaiApiKey });
    this.tavilyApiKey = tavilyApiKey;
    this.maxIterations = 3; // Prevent infinite loops
  }

  /**
   * Main agentic flow - analyzes image and decides if web search is needed
   */
  async analyzeWithAgent(base64Image, userPrompt) {
    try {
      console.log('🤖 Starting agentic analysis...');
      
      // Step 1: Initial analysis to determine if web search is needed
      const initialAnalysis = await this.performInitialAnalysis(base64Image, userPrompt);
      
      // Step 2: Check if agent needs web search
      const needsWebSearch = await this.shouldPerformWebSearch(initialAnalysis, userPrompt);
      
      if (!needsWebSearch.shouldSearch) {
        console.log('✅ No web search needed, returning initial analysis');
        return {
          finalAnswer: initialAnalysis,
          webSearchUsed: false,
          iterations: 1
        };
      }

      console.log('🔍 Web search needed:', needsWebSearch.searchQuery);
      
      // Step 3: Perform web search
      const searchResults = await this.performWebSearch(needsWebSearch.searchQuery);
      
      // Step 4: Combine initial analysis with web search results
      const enhancedAnalysis = await this.combineAnalysisWithWebSearch(
        initialAnalysis, 
        searchResults, 
        userPrompt
      );

      return {
        finalAnswer: enhancedAnalysis,
        webSearchUsed: true,
        searchQuery: needsWebSearch.searchQuery,
        webResults: searchResults.slice(0, 3), // Include top 3 results for transparency
        iterations: 2
      };

    } catch (error) {
      console.error('❌ Agent analysis error:', error);
      throw error;
    }
  }

  /**
   * Performs initial image analysis
   */
  async performInitialAnalysis(base64Image, userPrompt) {
    const response = await this.openai.chat.completions.create({
      model: "gpt-4o",
      messages: [
        {
          role: "user",
          content: [
            { type: "text", text: userPrompt },
            {
              type: "image_url",
              image_url: {
                url: `data:image/jpeg;base64,${base64Image}`,
              },
            },
          ],
        },
      ],
      max_tokens: 500,
    });

    return response.choices[0]?.message?.content || 'No analysis received';
  }

  /**
   * Determines if web search is needed and generates search query
   */
  async shouldPerformWebSearch(initialAnalysis, userPrompt) {
    const decisionPrompt = `
You are an intelligent agent that decides whether web search is needed to better answer a user's request about an image.

Initial image analysis: "${initialAnalysis}"
User's prompt: "${userPrompt}"

Determine if web search would significantly improve the answer. Web search is useful for:
- Getting current information (prices, ratings, reviews, news)
- Finding specific details about movies, products, places, people
- Getting real-time data (stock prices, weather, events)
- Fact-checking or getting additional context

Respond with JSON only:
{
  "shouldSearch": true/false,
  "searchQuery": "specific search query if needed",
  "reasoning": "brief explanation"
}

Examples of when to search:
- Movie poster → search for "movie name IMDB rating reviews"
- Product → search for "product name price reviews where to buy"
- Restaurant → search for "restaurant name location hours reviews"
- Person → search for "person name current news"
- Place → search for "place name current information visiting hours"

Examples of when NOT to search:
- Simple object description
- General scene description
- Abstract art analysis
- Color/composition analysis
`;

    try {
      const response = await this.openai.chat.completions.create({
        model: "gpt-4o-mini",
        messages: [{ role: "user", content: decisionPrompt }],
        max_tokens: 200,
        temperature: 0.1,
      });

      const decision = JSON.parse(response.choices[0]?.message?.content || '{"shouldSearch": false}');
      return decision;
    } catch (error) {
      console.error('Error in search decision:', error);
      return { shouldSearch: false, reasoning: 'Error in decision making' };
    }
  }

  /**
   * Performs web search using Tavily API
   */
  async performWebSearch(query) {
    if (!this.tavilyApiKey) {
      console.warn('⚠️ Tavily API key not provided, skipping web search');
      return [];
    }

    try {
      const response = await axios.post('https://api.tavily.com/search', {
        api_key: this.tavilyApiKey,
        query: query,
        search_depth: "advanced",
        include_answer: true,
        include_domains: [],
        exclude_domains: [],
        max_results: 5
      }, {
        headers: {
          'Content-Type': 'application/json'
        },
        timeout: 10000 // 10 second timeout
      });

      return response.data.results || [];
    } catch (error) {
      console.error('❌ Tavily search error:', error.message);
      return [];
    }
  }

  /**
   * Combines initial analysis with web search results
   */
  async combineAnalysisWithWebSearch(initialAnalysis, webResults, userPrompt) {
    const webSearchSummary = webResults.map(result => 
      `Source: ${result.title}\nURL: ${result.url}\nContent: ${result.content}`
    ).join('\n\n');

    const enhancementPrompt = `
You are an expert analyst. Combine the initial image analysis with current web search results to provide a comprehensive answer.

Initial Analysis: "${initialAnalysis}"

Web Search Results:
${webSearchSummary}

User's Original Request: "${userPrompt}"

Instructions:
1. Use the web search results to enhance, verify, or update the initial analysis
2. Answer to the point, be precise and to the point
3. Use official sources for the information.
`;

    try {
      const response = await this.openai.chat.completions.create({
        model: "gpt-4o",
        messages: [{ role: "user", content: enhancementPrompt }],
        max_tokens: 600,
        temperature: 0.3,
      });

      return response.choices[0]?.message?.content || initialAnalysis;
    } catch (error) {
      console.error('Error in combining analysis:', error);
      return initialAnalysis; // Fallback to initial analysis
    }
  }

  /**
   * Health check for the agent service
   */
  async healthCheck() {
    try {
      // Test OpenAI connection
      await this.openai.chat.completions.create({
        model: "gpt-4o-mini",
        messages: [{ role: "user", content: "Hello" }],
        max_tokens: 5,
      });

      // Test Tavily connection if API key is provided
      let tavilyStatus = 'not_configured';
      if (this.tavilyApiKey) {
        try {
          await axios.post('https://api.tavily.com/search', {
            api_key: this.tavilyApiKey,
            query: 'test',
            max_results: 1
          }, { timeout: 5000 });
          tavilyStatus = 'connected';
        } catch (error) {
          tavilyStatus = 'error';
        }
      }

      return {
        openai: 'connected',
        tavily: tavilyStatus,
        agentService: 'operational'
      };
    } catch (error) {
      return {
        openai: 'error',
        tavily: 'unknown',
        agentService: 'error',
        error: error.message
      };
    }
  }
}

module.exports = AgentService;
