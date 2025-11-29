"""
Configuration Management Module

Purpose: Load and validate configuration from YAML file

"""

import os
import yaml
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


@dataclass
class QueueServiceConfig:
    """
    Configuration for the Queue Service API.
    
    Attributes:
        base_url: Base URL of the queue service (e.g., "http://localhost:8080")
    """
    base_url: str
    
    def __post_init__(self):
        """Validate queue service configuration."""
        if not self.base_url:
            raise ValueError("Queue service base_url cannot be empty")
        
        # Remove trailing slash for consistency
        self.base_url = self.base_url.rstrip('/')


@dataclass
class StorageConfig:
    """
    Configuration for file storage paths.
    
    Attributes:
        pdf_dir: Directory for uploaded PDF files
        pages_dir: Directory for extracted page images
        results_dir: Directory for worker result JSON files
        metadata_dir: Directory for job metadata files
    """
    pdf_dir: str
    pages_dir: str
    results_dir: str
    metadata_dir: str
    
    def __post_init__(self):
        """Validate and create storage directories."""
        # Ensure all paths are defined
        if not all([self.pdf_dir, self.pages_dir, self.results_dir, self.metadata_dir]):
            raise ValueError("All storage directory paths must be defined")
        
        # Create directories if they don't exist
        for dir_path in [self.pdf_dir, self.pages_dir, self.results_dir, self.metadata_dir]:
            Path(dir_path).mkdir(parents=True, exist_ok=True)


@dataclass
class LLMConfig:
    """
    Configuration for LLM service.
    
    Attributes:
        provider: LLM provider ("openai", "anthropic", "gemini", or "mock")
        api_key: API key for the LLM service (not needed for "mock")
        model: Model name (e.g., "gpt-4o", "claude-3-5-sonnet-20241022", "gemini-1.5-flash")
        max_questions_per_page: Maximum number of quiz questions to generate per page
    """
    provider: str
    api_key: Optional[str]
    model: str
    max_questions_per_page: int
    
    def __post_init__(self):
        """Validate LLM configuration."""
        # Validate provider
        valid_providers = ["openai", "anthropic", "gemini", "mock"]
        if self.provider not in valid_providers:
            raise ValueError(
                f"Invalid LLM provider: {self.provider}. "
                f"Must be one of: {', '.join(valid_providers)}"
            )
        
        # Validate API key for non-mock providers
        if self.provider != "mock" and not self.api_key:
            raise ValueError(
                f"API key is required for provider '{self.provider}'. "
                f"Set it in config.yaml or as environment variable."
            )
        
        # Validate model name
        if not self.model:
            raise ValueError("Model name cannot be empty")
        
        # Validate max_questions_per_page
        if self.max_questions_per_page <= 0:
            raise ValueError("max_questions_per_page must be greater than 0")


@dataclass
class WorkerConfig:
    """
    Configuration for worker behavior.
    
    Attributes:
        poll_interval: Seconds to wait between queue polls when empty
        max_retries: Maximum number of retries for failed tasks
        retry_backoff: Multiplier for exponential backoff (seconds)
    """
    poll_interval: float
    max_retries: int
    retry_backoff: float
    
    def __post_init__(self):
        """Validate worker configuration."""
        if self.poll_interval <= 0:
            raise ValueError("poll_interval must be greater than 0")
        
        if self.max_retries < 0:
            raise ValueError("max_retries must be non-negative")
        
        if self.retry_backoff <= 0:
            raise ValueError("retry_backoff must be greater than 0")


@dataclass
class AnkiConfig:
    """
    Configuration for Anki deck output.
    
    Attributes:
        deck_name: Name of the Anki deck
        output_dir: Directory where Anki CSV files will be saved
    """
    deck_name: str
    output_dir: str
    
    def __post_init__(self):
        """Validate Anki configuration."""
        if not self.deck_name:
            raise ValueError("Anki deck_name cannot be empty")
        
        if not self.output_dir:
            raise ValueError("Anki output_dir cannot be empty")
        
        # Create output directory if it doesn't exist
        Path(self.output_dir).mkdir(parents=True, exist_ok=True)


@dataclass
class Config:
    """
    Main configuration object containing all subsystem configurations.
    
    Attributes:
        queue_service: Queue service configuration
        storage: File storage configuration
        llm: LLM service configuration
        worker: Worker behavior configuration
        anki: Anki output configuration
    """
    queue_service: QueueServiceConfig
    storage: StorageConfig
    llm: LLMConfig
    worker: WorkerConfig
    anki: AnkiConfig


def load_config(config_path: str = "config.yaml") -> Config:
    """
    Load and validate configuration from YAML file.
    
    This function:
    1. Loads the YAML file
    2. Supports environment variable substitution (e.g., ${OPENAI_API_KEY})
    3. Validates all configuration values
    4. Creates necessary directories
    5. Returns a validated Config object
    
    Args:
        config_path: Path to the configuration YAML file
    
    Returns:
        Config: Validated configuration object
    
    Raises:
        FileNotFoundError: If config file doesn't exist
        ValueError: If configuration is invalid
        yaml.YAMLError: If YAML parsing fails
    
    Example:
        >>> config = load_config("config.yaml")
        >>> print(config.queue_service.base_url)
        'http://localhost:8080'
    """
    # Check if config file exists
    config_file = Path(config_path)
    if not config_file.exists():
        raise FileNotFoundError(
            f"Configuration file not found: {config_path}\n"
            f"Please create a config.yaml file in the current directory."
        )
    
    # Load YAML file
    try:
        with open(config_file, 'r') as f:
            raw_config = yaml.safe_load(f)
    except yaml.YAMLError as e:
        raise ValueError(f"Failed to parse YAML configuration: {e}")
    
    if not raw_config:
        raise ValueError("Configuration file is empty")
    
    # Substitute environment variables
    raw_config = _substitute_env_vars(raw_config)
    
    try:
        # Build configuration objects
        queue_service = QueueServiceConfig(
            base_url=raw_config['queue_service']['base_url']
        )
        
        storage = StorageConfig(
            pdf_dir=raw_config['storage']['pdf_dir'],
            pages_dir=raw_config['storage']['pages_dir'],
            results_dir=raw_config['storage']['results_dir'],
            metadata_dir=raw_config['storage']['metadata_dir']
        )
        
        llm = LLMConfig(
            provider=raw_config['llm']['provider'],
            api_key=raw_config['llm'].get('api_key'),  # Optional for mock mode
            model=raw_config['llm']['model'],
            max_questions_per_page=raw_config['llm']['max_questions_per_page']
        )
        
        worker = WorkerConfig(
            poll_interval=raw_config['worker']['poll_interval'],
            max_retries=raw_config['worker']['max_retries'],
            retry_backoff=raw_config['worker']['retry_backoff']
        )
        
        anki = AnkiConfig(
            deck_name=raw_config['anki']['deck_name'],
            output_dir=raw_config['anki']['output_dir']
        )
        
        # Create main config object
        config = Config(
            queue_service=queue_service,
            storage=storage,
            llm=llm,
            worker=worker,
            anki=anki
        )
        
        return config
        
    except KeyError as e:
        raise ValueError(
            f"Missing required configuration key: {e}\n"
            f"Please check your config.yaml file for completeness."
        )
    except TypeError as e:
        raise ValueError(f"Invalid configuration value: {e}")


def _substitute_env_vars(config_dict: dict) -> dict:
    """
    Recursively substitute environment variables in configuration.
    
    Supports ${VAR_NAME} syntax for environment variable substitution.
    If the environment variable is not set, keeps the original value.
    
    Args:
        config_dict: Configuration dictionary
    
    Returns:
        dict: Configuration with environment variables substituted
    
    Example:
        Input: {"api_key": "${OPENAI_API_KEY}"}
        Output: {"api_key": "sk-..."}
    """
    if not isinstance(config_dict, dict):
        if isinstance(config_dict, str) and config_dict.startswith('${') and config_dict.endswith('}'):
            # Extract environment variable name
            env_var = config_dict[2:-1]
            # Return environment variable value or None if not set
            return os.environ.get(env_var)
        return config_dict
    
    result = {}
    for key, value in config_dict.items():
        if isinstance(value, dict):
            result[key] = _substitute_env_vars(value)
        elif isinstance(value, str) and value.startswith('${') and value.endswith('}'):
            # Extract environment variable name
            env_var = value[2:-1]
            # Return environment variable value or None if not set
            result[key] = os.environ.get(env_var)
        elif isinstance(value, list):
            result[key] = [_substitute_env_vars(item) if isinstance(item, dict) else item 
                          for item in value]
        else:
            result[key] = value
    
    return result
