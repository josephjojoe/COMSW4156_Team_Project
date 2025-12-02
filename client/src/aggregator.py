"""
Aggregator Module.

Purpose: Wait for queue processing to complete, collect all worker results,
and generate an Anki-importable CSV deck.
"""

from __future__ import annotations

import argparse
import csv
import json
import logging
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Tuple

from src.config import Config, load_config
from src.queue_client import QueueClient, QueueClientError, QueueNotFoundError

logger = logging.getLogger(__name__)


class QuizAggregator:
    """
    Aggregates per-page quiz results into a single Anki deck CSV.

    Typical usage:

    1. Run the producer to create a queue and submit one task per page.
    2. Run one or more workers to process tasks and write results.
    3. Run this aggregator once processing is complete (or let it wait until
       completion) to build the final deck.
    """

    def __init__(self, config_path: str = "config.yaml") -> None:
        """
        Initialize the aggregator.

        Args:
            config_path: Path to configuration YAML file.
        """
        self.config: Config = load_config(config_path)
        self.queue_client = QueueClient(self.config.queue_service.base_url)

        self.metadata_dir = Path(self.config.storage.metadata_dir)
        self.output_dir = Path(self.config.anki.output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

        logger.info(
            "Initialized QuizAggregator (metadata_dir=%s, output_dir=%s)",
            self.metadata_dir,
            self.output_dir,
        )

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------
    def aggregate(self, queue_id: str | None = None, pdf_id: str | None = None) -> Path:
        """
        Aggregate all results for a given job into an Anki CSV deck.

        You can identify the job either by ``pdf_id`` (preferred) or by
        ``queue_id`` if you already know it. When ``pdf_id`` is provided the
        corresponding metadata file created by the producer is loaded.

        Args:
            queue_id: Queue identifier returned by the producer.
            pdf_id: Unique PDF identifier used in the metadata file name.

        Returns:
            Path to the generated CSV deck file.

        Raises:
            FileNotFoundError: If the metadata file cannot be found.
            QueueClientError: If status or result retrieval fails.
            ValueError: If neither queue_id nor pdf_id can be resolved.
        """
        metadata = None
        if pdf_id:
            metadata = self._load_metadata(pdf_id)
            queue_id = metadata["queue_id"]
        elif queue_id:
            # Try to infer the metadata from queue_id by scanning for a file
            # that references this queue. This is a convenience fallback.
            metadata, pdf_id = self._find_metadata_for_queue(queue_id)
        else:
            raise ValueError("Either pdf_id or queue_id must be provided to aggregate()")

        task_ids: List[str] = metadata["task_ids"]
        expected_count = len(task_ids)

        logger.info(
            "Starting aggregation for pdf_id=%s queue_id=%s (%d tasks)",
            pdf_id,
            queue_id,
            expected_count,
        )

        # 1) Wait for queue completion
        self._wait_for_completion(queue_id, expected_count)

        # 2) Collect all results from the queue service
        results = self._collect_all_results(queue_id, task_ids)

        # 3) Load questions from each result file
        all_questions: List[Dict[str, Any]] = []
        for result in results:
            questions = self._load_questions_from_result(result)
            all_questions.extend(questions)

        # 4) Generate Anki CSV
        deck_path = self._generate_anki_csv(pdf_id, all_questions)

        # 5) Print statistics
        stats = self._generate_statistics(all_questions, expected_count)
        logger.info(
            "Aggregation complete: questions=%d pages=%d success_rate=%.1f%% deck=%s",
            stats["total_questions"],
            stats["pages_processed"],
            stats["success_rate"] * 100.0,
            deck_path,
        )

        return deck_path

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------
    def _load_metadata(self, pdf_id: str) -> Dict[str, Any]:
        """
        Load job metadata saved by the producer.

        Expected file path pattern:

            {metadata_dir}/{pdf_id}_metadata.json
        """
        path = self.metadata_dir / f"{pdf_id}_metadata.json"
        if not path.is_file():
            raise FileNotFoundError(f"Metadata file not found: {path}")

        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)

        required_keys = {"pdf_id", "queue_id", "task_ids", "total_pages"}
        missing = required_keys - data.keys()
        if missing:
            raise ValueError(f"Metadata file {path} missing keys: {missing}")

        return data

    def _find_metadata_for_queue(self, queue_id: str) -> Tuple[Dict[str, Any], str]:
        """
        Try to locate a metadata file that references the given queue ID.

        This is a best-effort helper used when only ``queue_id`` is provided.
        It scans all ``*_metadata.json`` files under ``metadata_dir`` and
        returns the first matching entry.
        """
        for path in self.metadata_dir.glob("*_metadata.json"):
            try:
                with path.open("r", encoding="utf-8") as f:
                    data = json.load(f)
            except (OSError, json.JSONDecodeError):
                continue

            if str(data.get("queue_id")) == str(queue_id):
                pdf_id = data.get("pdf_id") or path.stem.replace("_metadata", "")
                return data, pdf_id

        raise FileNotFoundError(
            f"No metadata file found in {self.metadata_dir} for queue_id={queue_id}"
        )

    def _wait_for_completion(self, queue_id: str, expected_count: int) -> None:
        """
        Poll queue status until all tasks are completed.

        The aggregator queries ``GET /queue/{id}/status`` via the queue client
        and waits until:

        - ``pendingTaskCount == 0`` AND
        - ``completedResultCount == expected_count``.

        A simple exponential backoff is used between polls to keep traffic
        light, starting at 1 second and capping at 30 seconds.
        """
        backoff = 1.0
        max_backoff = 30.0

        logger.info(
            "Waiting for queue %s to complete (%d expected results)...",
            queue_id,
            expected_count,
        )

        while True:
            try:
                status = self.queue_client.get_queue_status(queue_id)
            except QueueNotFoundError:
                raise QueueClientError(
                    f"Queue {queue_id} not found while waiting for completion"
                ) from None
            except QueueClientError as exc:
                logger.warning(
                    "Failed to fetch queue status for %s: %s; retrying in %.1fs",
                    queue_id,
                    exc,
                    backoff,
                )
                time.sleep(backoff)
                backoff = min(max_backoff, backoff * 2)
                continue

            pending = status.get("pendingTaskCount")
            completed = status.get("completedResultCount")
            has_pending = status.get("hasPendingTasks")

            logger.info(
                "Queue %s status: pending=%s completed=%s hasPending=%s",
                queue_id,
                pending,
                completed,
                has_pending,
            )

            if pending == 0 and completed == expected_count:
                logger.info("Queue %s has completed all %d results.", queue_id, expected_count)
                break

            time.sleep(backoff)
            backoff = min(max_backoff, backoff * 2)

    def _collect_all_results(
        self,
        queue_id: str,
        task_ids: List[str],
    ) -> List[Dict[str, Any]]:
        """
        Fetch all results for the given task IDs from the queue service.
        """
        results: List[Dict[str, Any]] = []

        for task_id in task_ids:
            try:
                result = self.queue_client.get_result(queue_id, task_id)
            except QueueClientError as exc:
                logger.error(
                    "Failed to fetch result for task %s in queue %s: %s",
                    task_id,
                    queue_id,
                    exc,
                )
                continue

            if not result:
                logger.warning(
                    "No result found for task %s in queue %s (skipping)", task_id, queue_id
                )
                continue

            results.append(result)

        return results

    @staticmethod
    def _load_questions_from_result(result: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        Load question data from a single result record.

        The queue result's ``output`` field is expected to contain the path to
        the JSON file written by the worker. That JSON has the structure:

        .. code-block:: json

            {
              "pdf_id": "...",
              "page_num": 1,
              "questions": [
                {"question": "...", "answer": "..."},
                ...
              ]
            }
        """
        output_path = result.get("output")
        if not output_path:
            logger.warning("Result missing 'output' field; skipping: %s", result)
            return []

        path = Path(output_path)
        if not path.is_file():
            logger.warning("Result file not found at %s; skipping", path)
            return []

        try:
            with path.open("r", encoding="utf-8") as f:
                payload = json.load(f)
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning("Failed to read result file %s: %s", path, exc)
            return []

        questions = payload.get("questions") or []
        if not isinstance(questions, list):
            logger.warning("Result file %s has invalid 'questions' format", path)
            return []

        pdf_id = payload.get("pdf_id")
        page_num = payload.get("page_num")
        tag = f"{pdf_id}_page_{page_num}" if pdf_id and page_num is not None else ""

        normalized: List[Dict[str, Any]] = []
        for item in questions:
            question = item.get("question")
            answer = item.get("answer")
            if isinstance(question, str) and isinstance(answer, str):
                normalized.append(
                    {
                        "question": question,
                        "answer": answer,
                        "tag": tag,
                    }
                )

        return normalized

    def _generate_anki_csv(
        self,
        pdf_id: str,
        questions: List[Dict[str, Any]],
    ) -> Path:
        """
        Generate an Anki-importable CSV file from all questions.

        The CSV format is:

            Question,Answer,Tags
        """
        if not questions:
            logger.warning("No questions to write to Anki deck; creating empty CSV.")

        deck_filename = f"{pdf_id or self.config.anki.deck_name}.csv"
        deck_path = self.output_dir / deck_filename

        with deck_path.open("w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["Question", "Answer", "Tags"])

            for item in questions:
                writer.writerow(
                    [
                        item.get("question", ""),
                        item.get("answer", ""),
                        item.get("tag", ""),
                    ]
                )

        return deck_path

    @staticmethod
    def _generate_statistics(
        questions: List[Dict[str, Any]],
        total_expected_pages: int,
    ) -> Dict[str, Any]:
        """
        Compute simple summary statistics for the aggregated deck.
        """
        total_questions = len(questions)

        # Heuristic: count distinct page tags to estimate pages processed.
        tags = {q.get("tag") for q in questions if q.get("tag")}
        pages_processed = len(tags) if tags else 0

        success_rate = (
            pages_processed / total_expected_pages if total_expected_pages > 0 else 0.0
        )

        return {
            "total_questions": total_questions,
            "pages_processed": pages_processed,
            "success_rate": success_rate,
        }


def _build_arg_parser() -> argparse.ArgumentParser:
    """Create the CLI argument parser for the aggregator script."""
    parser = argparse.ArgumentParser(
        description="Aggregate worker results into an Anki deck CSV.",
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument(
        "--pdf-id",
        type=str,
        help="PDF identifier used in metadata file name "
        "(e.g., '123e4567-e89b-12d3-a456-426614174000')",
    )
    group.add_argument(
        "--queue-id",
        type=str,
        help="Queue ID returned by the producer (metadata will be inferred)",
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

    Example usages:

        python aggregator.py --pdf-id <PDF_ID>
        python aggregator.py --queue-id <QUEUE_ID>
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    parser = _build_arg_parser()
    args = parser.parse_args()

    try:
        aggregator = QuizAggregator(config_path=args.config)
        deck_path = aggregator.aggregate(queue_id=args.queue_id, pdf_id=args.pdf_id)
        logger.info("Generated Anki deck at %s", deck_path)
        print(deck_path)
    except FileNotFoundError as exc:
        logger.error("Required file not found: %s", exc)
        sys.exit(1)
    except QueueClientError as exc:
        logger.error("Failed to communicate with queue service: %s", exc)
        sys.exit(1)
    except Exception as exc:  # pragma: no cover - defensive
        logger.exception("Aggregator failed with unexpected error: %s", exc)
        sys.exit(1)


if __name__ == "__main__":
    main()
