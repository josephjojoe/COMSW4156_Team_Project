# PDF Quiz Generator Client

## Overview

The PDF Quiz Generator is a sample client application that demonstrates the capabilities of our Task Queue Service. It converts PDF textbooks into Anki flashcard decks by distributing page processing work across multiple worker instances.

**What it does:**
1. Takes a PDF textbook as input
2. Splits it into individual page images
3. Distributes pages to multiple workers via the queue service
4. Workers use LLM APIs to generate quiz questions from each page
5. Aggregates all questions into a single Anki-compatible CSV file

**Architecture:**
```
┌─────────────┐
│  Producer   │ Splits PDF, submits tasks
└──────┬──────┘
       │ POST /queue
       │ POST /queue/{id}/task (×N pages)
       ▼
┌──────────────────┐
│  Queue Service   │ Your Java Spring Boot service
│  (localhost:8080)│ Manages tasks & results
└─┬────┬────┬──────┘
  │    │    │ GET /queue/{id}/task (polling)
  ▼    ▼    ▼
┌────┐┌────┐┌────┐
│W#1 ││W#2 ││W#3 │ Workers process in parallel
└─┬──┘└─┬──┘└─┬──┘
  │ Call LLM API (OpenAI/Anthropic)
  ▼     ▼     ▼
  POST /queue/{id}/result
       │
┌──────┴───────────┐
│  Queue Service   │ Stores completed results
└──────┬───────────┘
       │ GET /queue/{id}/status
       │ GET /queue/{id}/result/{taskId}
       ▼
┌──────────────┐
│  Aggregator  │ Combines into Anki deck
└──────┬───────┘
       ▼
  quiz_deck.csv (Import to Anki)
```

## Installation

### System Requirements

- **Python:** 3.8 or higher
- **Poppler:** PDF rendering library
- **Queue Service:** Java Spring Boot service running on `localhost:8080`

### Install Poppler (PDF Processing Dependency)
```bash
# macOS
brew install poppler

# Ubuntu/Debian
sudo apt-get install poppler-utils

# Windows
# 1. Download from: https://github.com/oschwartz10612/poppler-windows/releases
# 2. Extract and add bin/ directory to PATH
```

### Install Python Dependencies
```bash
cd client
pip install -r requirements.txt
```

**Verify installation:**
```bash
pytest --version
python -c "from src.config import load_config; print('✓ Client installed successfully')"
```

### Configuration Setup

Create or verify your `config.yaml` file:
```yaml
queue_service:
  base_url: "http://localhost:8080"

storage:
  pdf_dir: "storage/pdfs"
  pages_dir: "storage/pages"
  results_dir: "storage/results"
  metadata_dir: "storage/metadata"

llm:
  provider: "mock"  # Use "openai" or "anthropic" for real LLMs
  api_key: null     # Set to ${OPENAI_API_KEY} for real APIs
  model: "mock-model"
  max_questions_per_page: 5

worker:
  poll_interval: 2.0
  max_retries: 3
  retry_backoff: 2.0

anki:
  deck_name: "Quiz Deck"
  output_dir: "output"
```

## Usage

### Running the Producer

**Command:**
```bash
python -m src.producer <pdf_path> [--queue-name NAME] [--config PATH]
```

**What it does:**
1. Validates the PDF file
2. Creates a queue in the task queue service
3. Converts each PDF page to a PNG image
4. Submits one task per page to the queue (with page image path and metadata)
5. Saves metadata for the aggregator

**Example:**
```bash
python -m src.producer textbooks/biology-chapter1.pdf --queue-name "bio-ch1"
```

**Output:**
```
Processing PDF: biology-chapter1.pdf
PDF has 50 pages
Creating queue: bio-ch1
Queue created with ID: a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6
Converting PDF to images...
Generated 50 page images
Submitting tasks to queue...
  Submitted 10/50 tasks
  Submitted 20/50 tasks
  ...
✓ Submitted 50 tasks successfully

Queue ID: a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6

Next steps:
  1. Start workers: python -m src.worker a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6
  2. Aggregate results: python -m src.aggregator a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6
```

### Running Workers

**Command:**
```bash
python -m src.worker <queue_id> [--config PATH]
```

**What it does:**
1. Polls the queue service for available tasks
2. Downloads the page image
3. Calls LLM API to generate quiz questions from the image
4. Submits the questions back to the queue as a result
5. Repeats until stopped (Ctrl+C)

**How to run multiple workers:**

Open separate terminal windows and run the same command:
```bash
# Terminal 1
python -m src.worker a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6

# Terminal 2
python -m src.worker a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6

# Terminal 3
python -m src.worker a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6
```

**How workers coordinate:**

Workers coordinate **automatically through the queue service** without needing to know about each other:

- Each worker independently polls `GET /queue/{id}/task`
- The queue service's **thread-safe dequeue** ensures each worker receives a unique task
- No explicit worker registration or IDs required
- Tasks are distributed naturally based on which worker requests first

**Example:** With 3 workers processing 10 pages:
- Worker 1 might process pages 1, 4, 7, 10
- Worker 2 might process pages 2, 5, 8
- Worker 3 might process pages 3, 6, 9

The distribution happens automatically based on timing.

**Worker output:**
```
Worker started for queue: a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6
Polling for tasks... (Press Ctrl+C to stop)

[2025-01-15 10:23:45] Received task: page 1
[2025-01-15 10:23:47] Calling LLM API...
[2025-01-15 10:23:52] Generated 5 questions
[2025-01-15 10:23:53] ✓ Result submitted for page 1

[2025-01-15 10:23:55] Received task: page 4
...
```

### Running the Aggregator

**Command:**
```bash
python -m src.aggregator <queue_id> [--output PATH] [--config PATH]
```

**When to run it:**
Run the aggregator **after all workers have completed** processing all tasks. The aggregator will:
1. Wait for all tasks to complete (polls queue status)
2. Collect all results from the queue service
3. Combine questions from all pages
4. Generate a single Anki-compatible CSV file

**Output format:**

The aggregator produces a CSV file that Anki can import directly:
```csv
Question,Answer,Tags
"What is photosynthesis?","Photosynthesis is...","textbook_page_1"
"How does ATP work?","ATP works by...","textbook_page_2"
...
```

**Example:**
```bash
python -m src.aggregator a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6 --output biology_deck.csv
```

**Output:**
```
Waiting for queue a1b2c3d4-... to complete...

Polling status... (0/50 tasks complete)
Polling status... (15/50 tasks complete)
Polling status... (32/50 tasks complete)
Polling status... (50/50 tasks complete)

✓ All tasks completed!
Collecting results...
  Page 1: 5 questions
  Page 2: 6 questions
  ...
  Page 50: 4 questions

Total questions: 253
Generating Anki CSV...

✓ Anki deck generated: output/biology_deck.csv
Import this file into Anki to use your flashcards!
```

## Architecture

### How Multiple Workers Coordinate

The queue service handles worker coordination **automatically** through its REST API:

**No Explicit Worker IDs:**
- Workers don't register or identify themselves
- The service doesn't track individual workers
- Each HTTP request is independent

**Task Distribution Mechanism:**
```java
// In your Queue Service (simplified):
public synchronized Task dequeueTask(String queueId) {
    Queue queue = queueStore.get(queueId);
    return queue.tasks.poll();  // Thread-safe: one task per call
}
```

When multiple workers call `GET /queue/{id}/task` simultaneously:
1. Each request is handled sequentially by the service
2. Each call to `dequeue()` removes one task from the queue
3. No two workers receive the same task (guaranteed by thread-safety)
4. Distribution is natural: whoever requests first, gets the next task

**Example Flow:**
```
Time  Worker 1              Worker 2              Worker 3              Queue State
----  ----------            ----------            ----------            -----------
0s    GET /task             -                     -                     [T1,T2,T3,T4,T5]
      → Returns T1          -                     -                     [T2,T3,T4,T5]
1s    Processing T1         GET /task             GET /task             [T2,T3,T4,T5]
      -                     → Returns T2          → Returns T3          [T4,T5]
2s    Processing T1         Processing T2         Processing T3         [T4,T5]
5s    POST /result (T1)     Processing T2         Processing T3         [T4,T5]
6s    GET /task             Processing T2         Processing T3         [T4,T5]
      → Returns T4          -                     -                     [T5]
7s    Processing T4         POST /result (T2)     Processing T3         [T5]
...
```

**Key Points:**
- Workers are **stateless** and **independent**
- The service uses **thread-safe data structures** (e.g., `PriorityBlockingQueue`)
- No worker-to-worker communication needed
- Scalable: run 1 worker or 100 workers with no configuration changes

## Configuration

The `config.yaml` file controls all client behavior:

### Queue Service Section
```yaml
queue_service:
  base_url: "http://localhost:8080"  # URL of your Java service
```

### Storage Section
```yaml
storage:
  pdf_dir: "storage/pdfs"          # Where uploaded PDFs are stored
  pages_dir: "storage/pages"       # Where page images are saved
  results_dir: "storage/results"   # Where worker results are saved
  metadata_dir: "storage/metadata" # Where job metadata is saved
```

All directories are created automatically.

### LLM Section
```yaml
llm:
  provider: "mock"           # Options: "openai", "anthropic", "gemini", "mock"
  api_key: null              # For real APIs: ${OPENAI_API_KEY}
  model: "mock-model"        # e.g., "gpt-4o", "claude-3-5-sonnet-20241022"
  max_questions_per_page: 5  # Questions generated per page
```

**Switching between Mock and Real LLM:**

**For Development/Testing (No API costs):**
```yaml
llm:
  provider: "mock"
  api_key: null
  model: "mock-model"
```

**For Production (Real LLM):**
```yaml
llm:
  provider: "openai"
  api_key: ${OPENAI_API_KEY}  # Uses environment variable
  model: "gpt-4o"
```

Then set the environment variable:
```bash
export OPENAI_API_KEY="sk-..."
```

**Supported Providers:**
- `openai` - OpenAI GPT-4o, GPT-4 Turbo
- `anthropic` - Claude 3.5 Sonnet, Claude 3 Opus
- `gemini` - Gemini 1.5 Flash, Gemini 1.5 Pro
- `mock` - Fake responses for testing (no API calls)

### Worker Section
```yaml
worker:
  poll_interval: 2.0    # Seconds to wait between polls when queue is empty
  max_retries: 3        # Maximum retry attempts for failed tasks
  retry_backoff: 2.0    # Exponential backoff multiplier
```

### Anki Section
```yaml
anki:
  deck_name: "Quiz Deck"  # Name for the Anki deck
  output_dir: "output"     # Where to save CSV files
```

## Demo Walkthrough

Here's a complete example from start to finish:

### Step 1: Start the Queue Service
```bash
cd GroupProject
mvn spring-boot:run
```

Wait for:
```
Started Application in X.XXX seconds
```

### Step 2: Process a PDF
```bash
cd client
python -m src.producer sample.pdf --queue-name "demo"
```

**Note the Queue ID** from the output:
```
Queue ID: abc123...
```

### Step 3: Start Multiple Workers

Open 3 terminal windows:

**Terminal 1:**
```bash
cd client
python -m src.worker abc123...
```

**Terminal 2:**
```bash
cd client
python -m src.worker abc123...
```

**Terminal 3:**
```bash
cd client
python -m src.worker abc123...
```

Watch as they process pages in parallel:
```
[Worker 1] Processing page 1...
[Worker 2] Processing page 2...
[Worker 3] Processing page 3...
```

### Step 4: Wait for Completion

Workers will continue until all tasks are done. When you see:
```
Waiting for tasks... (no tasks available)
```

from all workers, processing is complete. Press Ctrl+C to stop them.

### Step 5: Generate Anki Deck
```bash
python -m src.aggregator abc123... --output demo_deck.csv
```

Output:
```
✓ Anki deck generated: output/demo_deck.csv
Total questions: 47
```

### Step 6: Import to Anki

1. Open Anki application
2. Click "Import File"
3. Select `output/demo_deck.csv`
4. Map columns: Question → Front, Answer → Back
5. Click "Import"

Your flashcards are now ready to study!

## Troubleshooting

### "ModuleNotFoundError: No module named 'src'"

**Cause:** Running from wrong directory or missing `__init__.py`

**Solution:**
```bash
# Ensure you're in the client directory
cd client

# Verify structure
ls src/__init__.py  # Should exist
ls tests/__init__.py  # Should exist

# Run with -m flag
python -m src.producer file.pdf  # ✅ Correct
python src/producer.py file.pdf  # ❌ Wrong
```

### "FileNotFoundError: Configuration file not found"

**Cause:** Missing `config.yaml`

**Solution:**
```bash
# Ensure config.yaml exists
ls config.yaml

# Or specify path
python -m src.producer file.pdf --config /path/to/config.yaml
```

### "QueueClientError: Failed to create queue"

**Cause:** Queue service not running or wrong URL

**Solution:**
```bash
# Check if service is running
curl http://localhost:8080/queue

# Verify config.yaml has correct URL
grep base_url config.yaml
# Should show: base_url: "http://localhost:8080"

# Start the service
cd GroupProject
mvn spring-boot:run
```

### "pdf2image.exceptions.PDFInfoNotInstalledError"

**Cause:** Poppler not installed

**Solution:**
```bash
# macOS
brew install poppler

# Ubuntu/Debian
sudo apt-get install poppler-utils

# Verify installation
pdftoppm -v
```

### "Invalid or corrupted PDF file"

**Cause:** PDF file is damaged or not a valid PDF

**Solution:**
```bash
# Verify PDF with system tools
file document.pdf  # Should say "PDF document"

# Try opening in Preview/Acrobat
open document.pdf

# Use a different PDF for testing
```

### Workers Not Processing Tasks

**Cause:** Queue ID mismatch or queue is empty

**Solution:**
```bash
# Verify queue ID matches what producer output
# Check queue status via API
curl http://localhost:8080/queue/{queue-id}/status

# Should return: {"pendingTaskCount": X, ...}
```

### "No questions generated" from Workers

**Cause:** LLM provider misconfigured

**Solution:**
```bash
# For testing, use mock mode
# In config.yaml:
llm:
  provider: "mock"

# For real LLM, verify API key
echo $OPENAI_API_KEY  # Should show your key

# Test API key
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```





