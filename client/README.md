# PDF Quiz Generator Client

## Overview

This client demonstrates how to use the Runtime Terrors Task Queue Service to build a distributed system for processing PDF textbooks into Anki flashcard decks. The system splits PDF pages, distributes them to workers for LLM-based question generation, and aggregates results into importable flashcard files.

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

### Configuration Setup

Create or verify your `config.yaml` file using this template or edit current `config.yaml` file (Example config.yaml built for Anki already in repo):
```yaml
queue_service:
  base_url: "http://localhost:8080"

storage:
  pdf_dir: "storage/pdfs"
  pages_dir: "storage/pages"
  results_dir: "storage/results"
  metadata_dir: "storage/metadata"

llm:
  provider: "openai"  
  api_key: ${OPENAI_API_KEY} 
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

**Command (will not work, go to demo walkthrough section for live walkthrough):**
```bash
python -m src.producer <pdf_path> [--queue-name NAME] [--config PATH]
```

**What it does:**
1. Validates the PDF file
2. Creates a queue in the task queue service
3. Converts each PDF page to a PNG image
4. Submits one task per page to the queue (with page image path and metadata)
5. Saves metadata for the aggregator

**Example (will not work, go to demo walkthrough section for live walkthrough):**
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

**Command (will not work, go to demo walkthrough section for live walkthrough):**
```bash
python -m src.worker <queue_id> [--config PATH]
```

**What it does:**
1. Polls the queue service for available tasks
2. Downloads the page image
3. Calls LLM API to generate quiz questions from the image
4. Submits the questions back to the queue as a result
5. Repeats until stopped (Ctrl+C)

**How to run multiple workers :**

Open separate terminal windows and run the same command:
```bash
# Terminal 1 (NOT REAL QUEUE ID)
python -m src.worker a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6

# Terminal 2 (NOT REAL QUEUE ID)
python -m src.worker a1b2c3d4-5e6f-7g8h-9i0j-k1l2m3n4o5p6

# Etc
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

**Command (will not work, go to demo walkthrough section for live walkthrough):**
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

**Example (will not work, go to demo walkthrough section for live walkthrough):**
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

### Operating Administrator 

The administrator was not a part of the original client plan was added to handle new API entry points of clearing queues, and also to allow the user to check current queue status. 

The administrator (ie admin.py) is an optional CLI entry that allows the user to clear all queues or check the status of a queue. It can take --config flag for specfic working environments that aren't using the default config.yaml, or also the --flag on clear command that forces a clear without double checking

Check queue status:
```bash
python -m src.admin status <queue-id> [--config PATH]
```

Clear all queues:
```bash
python -m src.admin clear [--force] [--config PATH]
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

### How the Service Tells Clients Apart

The queue service distinguishes between client instances through HTTP request context rather than persistent identifiers. Each request includes source IP address, port, and timestamp, allowing the service to track individual HTTP calls. However, the service does not maintain worker sessions or registrations—it only tracks task ownership at the moment of dequeue. Once a worker receives a task via `GET /queue/{id}/task`, that task is "owned" by requiring the correct task ID when submitting results. Multiple workers on the same machine are distinguished by their source ports (e.g., `127.0.0.1:54321`, `127.0.0.1:54322`), but the service treats each new HTTP request independently without remembering previous interactions from the same worker. This stateless design eliminates the need for worker registration, enables dynamic scaling, and simplifies fault tolerance—workers can crash and restart without cleanup.

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
  provider: "mock"           # Options: "openrouter", "mock"
  api_key: null              # For real APIs: ${OPENROUTER_API_KEY}
  model: "google/gemma-3-27b-it:free"
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

**For Production (Real LLM via OpenRouter):**
```yaml
llm:
  provider: "openrouter"
  api_key: ${OPENROUTER_API_KEY}  # Uses environment variable
  model: "google/gemma-3-27b-it:free"
```

Then set the environment variable:
```bash
export OPENAI_API_KEY="sk-..."
```

**Supported Providers:**
- `openrouter` - Any model available via OpenRouter (e.g., Gemma 3)
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

Here's a complete example from start to finish.

### Step 1: Start the Queue Service
```bash
cd GroupProject/server
mvn clean spring-boot:run
```

Wait for:
```
Started Application in X.XXX seconds
```

### Step 2: Process a PDF in new Terminal

In any terminal that will run client/worker commands, make sure your OpenRouter key is
exported before starting:

```bash
cd client
export OPENROUTER_API_KEY="sk-or-..."  # required for LLM calls
python -m src.producer sampleInputs/sample.pdf --queue-name "demo"
```

**Note the Queue ID and PDF ID** from the output. Will need this for the aggregator:
```bash
Queue ID: abc123...(<Queue_ID>)
PDF ID: qaz345...(<PDF_ID>)
```

### Step 3: Start Multiple Workers in different terminals

Open 3 terminal windows:

**Open Terminal 1:**
```bash
cd client
export OPENROUTER_API_KEY="sk-or-..."  # required in every worker terminal
python -m src.worker <Queue_ID>
```

**Open Terminal 2:**
```bash
cd client
export OPENROUTER_API_KEY="sk-or-..."  # required in every worker terminal
python -m src.worker <Queue_ID>
```

**Open Terminal 3:**
```bash
cd client
export OPENROUTER_API_KEY="sk-or-..."  # required in every worker terminal
python -m src.worker <Queue_ID>
```

Watch as they process pages in parallel:
```
[Worker 1] Processing task=<Task_ID> pdf_id=<PDF_ID> image=<Img.png>...
[Worker 2] Processing task=<Task_ID> pdf_id=<PDF_ID> image=<Img.png>...
[Worker 3] Processing task=<Task_ID> pdf_id=<PDF_ID> image=<Img.png>...
```

### Step 4: Wait for Completion

Workers will continue until all tasks are done. When you see:
```
Waiting for tasks... (no tasks available)
```

**Check how many tasks remain in Producer Terminal:**
```bash
python -m src.admin status <Queue-ID> [--config PATH]
```
Template: curl http://localhost:8080/queue/<Queue_ID>/status

from all workers, processing is complete. Press Ctrl+C to stop them.

### Step 5: Run Aggregator in Same terminal as Producer
```bash
python -m src.aggregator --queue-id <QUEUE_ID>
```

Output:
```
✓ Anki deck generated: output/demo_deck.csv
Total questions: 47
```

Alternative if doing much later after running producer, and in different session now.:
```bash
python -m src.aggregator --pdf-id <PDF_ID>
```

### Step 6: Import to Anki

1. Open Anki application
2. Click "Import File"
3. Select `output/demo_deck.csv`
4. Map columns: Question → Front, Answer → Back
5. Click "Import"

Your flashcards are now ready to study!

### Use the following to clear all queues (WARNING: This will delete all queues, tasks, and results):

```bash
cd client
python -m src.admin clear [--force] [--config PATH]
```

### Use the following to clear metadata:

```bash
cd client
rm -rf storage/pages/*
rm -rf storage/metadata/*
rm -rf storage/results/*
```


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
python -m src.producer sample.pdf  # Correct
python src/producer.py sanmple.pdf  # Wrong
```

### "FileNotFoundError: Configuration file not found"

**Cause:** Missing `config.yaml`

**Solution:**
```bash
# Ensure config.yaml exists
ls config.yaml

# Or specify path
python -m src.producer sample.pdf --config /path/to/config.yaml
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
file sample.pdf  # Should say "PDF document"

# Try opening in Preview/Acrobat
open sample.pdf

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

## Developing Your Own Client

### Overview

A client application integrates with the queue service through standard REST API calls. Clients implement one or more roles: **Producer** creates queues and submits tasks, **Worker** processes tasks, and **Consumer/Aggregator** collects results. The service is stateless—no registration, authentication, or persistent sessions required.

### API Endpoints

The service exposes six REST endpoints:

1. **POST /queue** - Create a new queue (returns queue ID)
2. **POST /queue/{queueId}/task** - Submit a task with parameters and priority
3. **GET /queue/{queueId}/task** - Poll for next available task (thread-safe, returns 204 if empty)
4. **POST /queue/{queueId}/result** - Submit result for completed task
5. **GET /queue/{queueId}/result/{taskId}** - Retrieve result for specific task
6. **GET /queue/{queueId}/status** - Check queue status (pending/completed counts)

All requests use JSON format. Task parameters must be JSON-encoded strings. The service returns appropriate HTTP status codes (200 OK, 201 Created, 204 No Content, 404 Not Found).

### Client Implementation Requirements

**Technical Prerequisites:**
- HTTP client library (e.g., `requests` in Python, `HttpClient` in Java, `axios` in JavaScript)
- JSON serialization/deserialization support
- Basic error handling and retry logic

**Producer Pattern:**
Create a queue, split work into tasks, submit each task with appropriate priority, and save queue/task IDs for later retrieval.

**Worker Pattern:**
Continuously poll for tasks using GET /task endpoint. When a task is received, deserialize the parameters, process the work, and submit results using POST /result. Include proper error handling and submit status as "SUCCESS" or "FAILURE". Implement polling intervals (2-5 seconds when queue is empty) to avoid overwhelming the service.

**Aggregator Pattern:**
Poll queue status endpoint until all expected tasks are complete (pendingTaskCount = 0). Collect all results using GET /result/{taskId} for each task, then combine or process the aggregated results.

### Best Practices

- **Polling:** Wait 2-5 seconds between polls when queue is empty
- **Timeouts:** Set 30-second HTTP timeouts to prevent hanging
- **Error Handling:** Implement exponential backoff for transient failures
- **JSON Encoding:** Always JSON-encode task parameters as strings, not objects
- **Connection Pooling:** Reuse HTTP connections for better performance
- **Graceful Shutdown:** Handle SIGINT/SIGTERM to stop workers cleanly

### Common Pitfalls to Avoid

- Don't poll continuously without delays—this creates unnecessary load
- Don't assume tasks are processed in submission order—use priority if order matters
- Don't hardcode queue or task IDs—always use returned values from API
- Don't submit results multiple times for the same task
- Don't forget to handle 204 No Content responses (empty queue is not an error)

### Testing Your Client

Test against a running queue service (localhost:8080 by default). Verify your client can create queues, submit tasks, retrieve tasks, and submit results. Test edge cases like empty queues, network failures, and worker crashes. The PDF Quiz Generator client in this repository serves as a complete reference implementation.

### Getting Started

1. Ensure queue service is running at http://localhost:8080
2. Implement API wrapper methods for the six endpoints
3. Build producer to create queue and submit tasks
4. Build worker to poll, process, and submit results
5. Build aggregator to collect completed results
6. Add error handling, logging, and retry logic
7. Test complete workflow end-to-end

For detailed examples, refer to the PDF Quiz Generator client implementation in this repository. The `queue_client.py` module demonstrates all API interactions.

