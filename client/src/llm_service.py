"""
LLM Service Module.

This module provides a small, testable wrapper around large language model
providers for generating quiz questions from textbook page images.

Current implementation
----------------------

For this iteration we only support a **mock provider**. The goal is to allow
end‑to‑end development and testing of the PDF → worker → aggregator pipeline
without requiring real API keys or incurring any LLM costs.

The class is intentionally designed so that real providers (OpenAI/Anthropic)
can be added later without changing the public interface.
"""

from __future__ import annotations

import base64
import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import List, Dict

logger = logging.getLogger(__name__)


class LLMServiceError(Exception):
    """Raised when the LLM service fails to generate quiz questions."""


@dataclass
class LLMServiceConfig:
    """
    Lightweight configuration for :class:`LLMService`.

    This mirrors the fields expected from the higher level ``Config.llm``
    section but is kept independent so that the service can also be used in
    isolation during testing.
    """

    provider: str = "mock"
    api_key: str | None = None
    model: str = "mock-model"
    max_questions: int = 3


class LLMService:
    """
    Generate quiz questions from a page image.

    Public usage:

    .. code-block:: python

        service = LLMService(provider=\"mock\", api_key=None, model=\"mock\")
        qa_pairs = service.generate_quiz_from_image(\"page_1.png\", page_num=1)

    The return value is always a list of dictionaries with ``question`` and
    ``answer`` keys:

    .. code-block:: python

        [
            {\"question\": \"What is X?\", \"answer\": \"X is ...\"},
            {\"question\": \"How does Y work?\", \"answer\": \"Y works by ...\"},
        ]
    """

    def __init__(
        self,
        provider: str = "mock",
        api_key: str | None = None,
        model: str = "mock-model",
        max_questions: int = 3,
    ) -> None:
        """
        Initialize the LLM service.

        Args:
            provider: Which provider to use. For now only ``\"mock\"`` is
                supported. Future values may include ``\"openai\"`` or
                ``\"anthropic\"``.
            api_key: API key for the provider. Ignored for ``\"mock\"``.
            model: Model name to use (provider specific). Ignored for mock.
            max_questions: Maximum number of questions to generate per page.

        Raises:
            ValueError: If an unsupported provider is supplied.
        """
        self.provider = provider.lower().strip()
        self.api_key = api_key
        self.model = model
        self.max_questions = max(1, int(max_questions))

        if self.provider not in {"mock"}:
            # We deliberately fail fast here so that misconfiguration is
            # obvious and does not silently fall back to a different provider.
            raise ValueError(
                f"Unsupported provider '{provider}'. "
                "For this iteration only 'mock' is supported."
            )

        logger.debug(
            "Initialized LLMService provider=%s model=%s max_questions=%s",
            self.provider,
            self.model,
            self.max_questions,
        )

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------
    def generate_quiz_from_image(
        self,
        image_path: str,
        page_num: int,
    ) -> List[Dict[str, str]]:
        """
        Generate quiz questions from a page image.

        Args:
            image_path: Path to the page image on disk.
            page_num: One-based page number, used only for nicer messages.

        Returns:
            A list of question/answer dictionaries.

        Raises:
            FileNotFoundError: If ``image_path`` does not exist.
            LLMServiceError: If question generation fails.
        """
        path = Path(image_path)
        if not path.is_file():
            raise FileNotFoundError(f"Image file not found: {image_path}")

        logger.info(
            "Generating quiz questions provider=%s page=%s image=%s",
            self.provider,
            page_num,
            path.name,
        )

        try:
            image_b64 = self._encode_image(path)
            prompt = self._create_prompt(page_num)

            if self.provider == "mock":
                return self._call_mock(image_b64, prompt, page_num)

            # Unreachable for now because we validate provider in __init__,
            # but kept for forward compatibility.
            raise LLMServiceError(f"Unsupported provider at runtime: {self.provider}")
        except LLMServiceError:
            # Bubble up our own error type unchanged.
            raise
        except Exception as exc:  # pragma: no cover - defensive
            logger.exception("Unexpected error in LLMService: %s", exc)
            raise LLMServiceError(f"Unexpected error while generating quiz: {exc}") from exc

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------
    @staticmethod
    def _encode_image(path: Path) -> str:
        """
        Read an image file and return a base64-encoded string.

        This matches the format typically required by vision-capable LLM APIs
        and is convenient to test in isolation.
        """
        with path.open("rb") as f:
            data = f.read()
        return base64.b64encode(data).decode("ascii")

    @staticmethod
    def _create_prompt(page_num: int) -> str:
        """
        Create a human-readable prompt describing the desired quiz.

        Even though the mock provider does not actually use the prompt to call
        a real model, we keep this to match the shape expected by future
        providers.
        """
        return (
            "You are a helpful teaching assistant generating flashcard-style "
            "quiz questions from a single textbook page. "
            f"Focus on the key concepts on page {page_num} and produce clear, "
            "concise question and answer pairs."
        )

    def _call_mock(
        self,
        image_b64: str,  # noqa: ARG002 - kept for future parity with real calls
        prompt: str,  # noqa: ARG002 - kept for future parity with real calls
        page_num: int,
    ) -> List[Dict[str, str]]:
        """
        Mock implementation that returns deterministic, fake Q&A pairs.

        The content is intentionally simple but structured so that worker and
        aggregator components can be developed and tested without any external
        dependencies.
        """
        # Construct a small, deterministic question set based on page number.
        # This keeps tests predictable while still resembling real data.
        questions: List[Dict[str, str]] = []

        templates = [
            (
                "What is the main idea presented on page {page}?",
                "The main idea on page {page} is a placeholder concept used for testing.",
            ),
            (
                "Name one important term introduced on page {page}.",
                "An example term from page {page} is 'Test Concept {page}'.",
            ),
            (
                "How could a student apply the concept from page {page} in practice?",
                "A student could apply the concept from page {page} by using it in a mock study scenario.",
            ),
        ]

        for idx in range(min(self.max_questions, len(templates))):
            q_template, a_template = templates[idx]
            questions.append(
                {
                    "question": q_template.format(page=page_num),
                    "answer": a_template.format(page=page_num),
                }
            )

        if not questions:
            # This should never happen given our defaults, but we guard against
            # it to keep downstream code simple.
            raise LLMServiceError("Mock LLM did not generate any questions")

        logger.debug(
            "Mock LLM generated %d questions for page %d", len(questions), page_num
        )
        return questions


def _parse_qa_pairs_from_json(text: str) -> List[Dict[str, str]]:
    """
    Utility to parse JSON-formatted Q&A pairs.

    This is not used by the mock provider but is kept as a small, focused
    helper that can be reused when real LLM providers are introduced.
    """
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:  # pragma: no cover - defensive
        raise LLMServiceError(f"Failed to decode LLM JSON response: {exc}") from exc

    if not isinstance(data, list):
        raise LLMServiceError("Expected a list of Q&A dictionaries from LLM")

    results: List[Dict[str, str]] = []
    for item in data:
        if not isinstance(item, dict):
            continue
        question = item.get("question")
        answer = item.get("answer")
        if isinstance(question, str) and isinstance(answer, str):
            results.append({"question": question, "answer": answer})

    if not results:
        raise LLMServiceError("No valid question/answer pairs found in LLM response")

    return results
