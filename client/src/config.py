"""
Configuration Management Module

Purpose: Load and validate configuration from YAML file

Classes:
    - Config: Main configuration dataclass
        Fields: queue_service, storage, llm, worker, anki
    
    - QueueServiceConfig: Queue service settings
        Fields: base_url (str)
    
    - LLMConfig: LLM API settings
        Fields: provider (str), api_key (str), model (str), max_questions_per_page (int)
    
    - StorageConfig: File storage paths
        Fields: pdf_dir, pages_dir, results_dir, metadata_dir
    
    - WorkerConfig: Worker behavior settings
        Fields: poll_interval, max_retries, retry_backoff
    
    - AnkiConfig: Anki output settings
        Fields: deck_name, output_path

Functions:
    - load_config(config_path: str = "config.yaml") -> Config
        Load configuration from YAML file and validate

Dependencies:
    - yaml
    - dataclasses
    - pathlib
"""

