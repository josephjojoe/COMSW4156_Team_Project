"""
Worker Module.

Purpose: Poll the task queue, process pages with the LLM service, and submit
results back to the queue service.

This implementation is designed to work with the mock LLM provider first so the
end‑to‑end pipeline can be tested without real API keys or external costs.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List

from src.config import Config, load_config
from src.llm_service import LLMService, LLMServiceError
from src.queue_client import QueueClient, QueueClientError

logger = logging.getLogger(__name__)


class QuizWorker:
    """
    Worker process that consumes tasks from a queue and generates quiz content.

    Each worker instance:

    - Polls ``GET /queue/{id}/task`` for new tasks.
    - For each task, reads the page image path from the task params.
    - Uses :class:`LLMService` to generate quiz questions.
    - Writes the questions to a JSON file in the configured results directory.
    - Submits a SUCCESS or FAILURE result back to the queue service.
    """

    def __init__(self, queue_id: str, config_path: str = "config.yaml") -> None:
        """
        Initialize a worker instance.

        Args:
            queue_id: ID of the queue to poll for tasks.
            config_path: Path to YAML configuration file.
        """
        self.worker_id = f"worker-{uuid.uuid4().hex[:8]}"
        self.queue_id = queue_id

        self.config: Config = load_config(config_path)
        self.queue_client = QueueClient(self.config.queue_service.base_url)
        self.llm_service = LLMService(
            provider=self.config.llm.provider,
            api_key=self.config.llm.api_key,
            model=self.config.llm.model,
            max_questions=self.config.llm.max_questions_per_page,
        )

        self.results_dir = Path(self.config.storage.results_dir)
        self.results_dir.mkdir(parents=True, exist_ok=True)

        self.poll_interval = self.config.worker.poll_interval
        self.max_retries = self.config.worker.max_retries
        self.retry_backoff = self.config.worker.retry_backoff

        logger.info(
            "Initialized %s for queue_id=%s (results_dir=%s, provider=%s)",
            self.worker_id,
            self.queue_id,
            self.results_dir,
            self.config.llm.provider,
        )

    # ------------------------------------------------------------------
    # Main loop
    # ------------------------------------------------------------------
    def run(self) -> None:
        """
        Main worker loop.

        Continuously polls the queue for new tasks until interrupted. When a
        task is received, it is processed immediately; when the queue is empty,
        the worker waits ``poll_interval`` seconds before polling again.
        """
        logger.info("%s starting main loop", self.worker_id)
        try:
            while True:
                try:
                    task = self.queue_client.dequeue_task(self.queue_id)
                except QueueClientError as exc:
                    logger.error(
                        "%s failed to dequeue task from queue %s: %s",
                        self.worker_id,
                        self.queue_id,
                        exc,
                    )
                    time.sleep(self.poll_interval)
                    continue

                if task is None:
                    logger.debug("%s found no task in queue %s; sleeping %.1fs",
                                 self.worker_id, self.queue_id, self.poll_interval)
                    time.sleep(self.poll_interval)
                    continue

                self._process_task(task)
        except KeyboardInterrupt:
            logger.info("%s received KeyboardInterrupt, shutting down cleanly", self.worker_id)

    # ------------------------------------------------------------------
    # Task handling
    # ------------------------------------------------------------------
    def _process_task(self, task: Dict[str, Any]) -> None:
        """
        Handle a single task from the queue.

        Expected task JSON from the service:

        .. code-block:: json

            {
              "id": "<task-uuid>",
              "params": "{\"job\":\"generate_quiz\",\"pdf_id\":\"...\",\"page_num\":1,"
                        "\"page_path\":\"storage/pages/...png\",\"pdf_name\":\"...\"}",
              "priority": 1,
              "status": "IN_PROGRESS"
            }
        """
        task_id = task.get("id")
        raw_params = task.get("params")
        if not task_id or raw_params is None:
            logger.error("%s received malformed task: %s", self.worker_id, task)
            return

        try:
            params = json.loads(raw_params)
        except json.JSONDecodeError:
            logger.error(
                "%s could not decode task params for task %s: %s",
                self.worker_id,
                task_id,
                raw_params,
            )
            # Attempt to mark as failure with a simple message.
            self._submit_failure(task_id, "Invalid task params JSON")
            return

        pdf_id = params.get("pdf_id")
        page_num = params.get("page_num")
        page_path = params.get("page_path")

        if not (pdf_id and isinstance(page_num, int) and page_path):
            logger.error(
                "%s task %s missing required params: pdf_id=%s page_num=%s page_path=%s",
                self.worker_id,
                task_id,
                pdf_id,
                page_num,
                page_path,
            )
            self._submit_failure(task_id, "Missing required task parameters")
            return

        logger.info(
            "[%s] Processing task=%s pdf_id=%s page_num=%s image=%s",
            self.worker_id,
            task_id,
            pdf_id,
            page_num,
            os.path.basename(page_path),
        )

        # Retry logic for transient failures (e.g., network, temporary LLM issues)
        attempt = 0
        while True:
            attempt += 1
            try:
                questions = self.llm_service.generate_quiz_from_image(page_path, page_num)
                result_path = self._save_result_to_file(pdf_id, page_num, questions)

                logger.info(
                    "[%s] Completed task=%s page=%s; saved %d questions to %s",
                    self.worker_id,
                    task_id,
                    page_num,
                    len(questions),
                    result_path,
                )

                self.queue_client.submit_result(
                    queue_id=self.queue_id,
                    task_id=task_id,
                    output=str(result_path),
                    status="SUCCESS",
                )
                break
            except (LLMServiceError, FileNotFoundError) as exc:
                # Consider these non-transient for now – mark task as FAILURE.
                logger.error(
                    "[%s] Non-retryable error processing task=%s: %s",
                    self.worker_id,
                    task_id,
                    exc,
                )
                self._submit_failure(task_id, str(exc))
                break
            except QueueClientError as exc:
                # Network / service issues – apply retry policy.
                logger.warning(
                    "[%s] QueueClient error while submitting result for task=%s "
                    "(attempt %d/%d): %s",
                    self.worker_id,
                    task_id,
                    attempt,
                    self.max_retries,
                    exc,
                )
                if attempt > self.max_retries:
                    self._submit_failure(task_id, f"Failed to submit result: {exc}")
                    break
                sleep_for = self.retry_backoff * attempt
                logger.info("[%s] Retrying in %.1fs", self.worker_id, sleep_for)
                time.sleep(sleep_for)
            except Exception as exc:  # pragma: no cover - defensive
                logger.exception(
                    "[%s] Unexpected error processing task=%s: %s",
                    self.worker_id,
                    task_id,
                    exc,
                )
                self._submit_failure(task_id, f"Unexpected worker error: {exc}")
                break

    def _save_result_to_file(
        self,
        pdf_id: str,
        page_num: int,
        questions: List[Dict[str, str]],
    ) -> Path:
        """
        Save generated quiz questions to a JSON file.

        File name format: ``{pdf_id}_page_{page_num}_result.json``.
        """
        filename = f"{pdf_id}_page_{page_num}_result.json"
        out_path = self.results_dir / filename

        payload = {
            "pdf_id": pdf_id,
            "page_num": page_num,
            "questions": questions,
        }

        with out_path.open("w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)

        return out_path

    def _submit_failure(self, task_id: str, message: str) -> None:
        """
        Submit a FAILURE result for the given task.

        This method is best-effort: if the submission itself fails, the error is
        logged but not re-raised to avoid crashing the worker loop.
        """
        try:
            self.queue_client.submit_result(
                queue_id=self.queue_id,
                task_id=task_id,
                output=message,
                status="FAILURE",
            )
        except QueueClientError as exc:
            logger.error(
                "[%s] Failed to submit FAILURE result for task=%s: %s",
                self.worker_id,
                task_id,
                exc,
            )


def _build_arg_parser() -> argparse.ArgumentParser:
    """Create the CLI argument parser for the worker script."""
    parser = argparse.ArgumentParser(
        description="Worker process that consumes tasks and generates quiz questions.",
    )
    parser.add_argument(
        "queue_id",
        type=str,
        help="ID of the queue to poll for tasks",
    )
    parser.add_argument(
        "--config",
        type=str,
        default="config.yaml",
        help="Path to configuration file (default: config.yaml)",
    )
    return parser


def main() -> None:
    """
    CLI entry point.

    Usage:
        python worker.py <queue_id> [--config CONFIG_PATH]
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    parser = _build_arg_parser()
    args = parser.parse_args()

    try:
        worker = QuizWorker(queue_id=args.queue_id, config_path=args.config)
        worker.run()
    except FileNotFoundError as exc:
        logger.error("Configuration or required file not found: %s", exc)
        sys.exit(1)
    except Exception as exc:  # pragma: no cover - defensive
        logger.exception("Worker failed with unexpected error: %s", exc)
        sys.exit(1)


if __name__ == "__main__":
    main()
