"""
LLM Service Module

Purpose: Call LLM APIs to generate quiz questions from page images

Class: LLMService
    
    Fields:
        - provider: str ("openai", "anthropic", or "mock")
        - api_key: str
        - model: str (e.g., "gpt-4o" or "claude-3-5-sonnet-20241022")
        - max_questions: int (questions per page)
    
    Methods:
        - __init__(provider: str, api_key: str, model: str, max_questions: int = 3)
            Initialize LLM service with configuration
        
        - generate_quiz_from_image(image_path: str, page_num: int) -> List[dict]
            Main method: Generate quiz questions from a page image
            Returns: [{"question": "...", "answer": "..."}, ...]
        
        - _call_openai(image_data: str, prompt: str) -> List[dict]
            OpenAI-specific API call
        
        - _call_anthropic(image_data: str, prompt: str) -> List[dict]
            Anthropic-specific API call
        
        - _call_mock(image_data: str, prompt: str) -> List[dict]
            Mock implementation for testing (no API calls, fake data)
        
        - _create_prompt() -> str
            Generate system prompt for LLM
        
        - _parse_qa_pairs(llm_response: str) -> List[dict]
            Parse LLM text response into structured Q&A pairs
            Expected format: "Q: ... A: ..."
        
        - _encode_image(image_path: str) -> str
            Convert image to base64 for API

Return Format:
    [
        {"question": "What is X?", "answer": "X is..."},
        {"question": "How does Y work?", "answer": "Y works by..."}
    ]

Dependencies:
    - openai library
    - anthropic library
    - base64
    - logging

Important Notes:
    - Start with mock mode for free testing
    - Add retry logic for API failures
    - Handle rate limiting
    - LLM API calls cost money!
"""

