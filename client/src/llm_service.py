"""
LLM Service Module.

This module provides a small, testable wrapper around large language model
providers for generating quiz questions from textbook page images.

Providers
---------

- ``openrouter`` – default provider backed by the OpenRouter API.
- ``mock`` – deterministic, local mock used for testing and as a safe fallback
  when the OpenRouter API key is missing or invalid.
"""

from __future__ import annotations

import base64
import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import List, Dict

import requests

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

    provider: str = "openrouter"
    api_key: str | None = None
    model: str = "openai/gpt-4o-mini"
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
        provider: str = "openrouter",
        api_key: str | None = None,
        model: str = "openai/gpt-4o-mini",
        max_questions: int = 3,
    ) -> None:
        """
        Initialize the LLM service.

        Args:
            provider: Which provider to use. Supported values:

                - ``"openrouter"`` (default)
                - ``"mock"``

            api_key: API key for the provider. Required for ``"openrouter"``;
                ignored for ``"mock"``. If the OpenRouter key is missing or
                invalid the service will automatically fall back to mock mode.
            model: Model name to use (provider specific).
            max_questions: Maximum number of questions to generate per page.

        Raises:
            ValueError: If an unsupported provider is supplied.
        """
        self.provider = provider.lower().strip()
        self.api_key = api_key
        self.model = model
        self.max_questions = max(1, int(max_questions))

        if self.provider not in {"openrouter", "mock"}:
            # Fail fast so that misconfiguration is obvious and does not
            # silently fall back to a different provider.
            raise ValueError(
                f"Unsupported provider '{provider}'. "
                "Supported providers are: 'openrouter', 'mock'."
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
            if self.provider == "openrouter":
                return self._call_openrouter(image_b64, prompt, page_num)

            # Defensive: __init__ should already have validated provider.
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

    def _call_openrouter(
        self,
        image_b64: str,
        prompt: str,
        page_num: int,
    ) -> List[Dict[str, str]]:
        """
        Call the OpenRouter chat completions API to generate Q&A pairs.

        Behavior:
            - If no API key is configured, logs a warning and falls back to
              :meth:`_call_mock`.
            - If the API key is invalid (401/403) or the HTTP request fails,
              logs an error and falls back to :meth:`_call_mock`.
            - For other HTTP errors or unexpected response formats, raises
              :class:`LLMServiceError`.
        """
        if not self.api_key:
            logger.warning(
                "No OpenRouter API key configured; falling back to mock provider."
            )
            return self._call_mock(image_b64, prompt, page_num)

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            # These headers are recommended by OpenRouter for attribution,
            # but they are not strictly required for correctness.
            "HTTP-Referer": "http://localhost",
            "X-Title": "PDF Quiz Generator",
        }

        # Compose a chat-style request. We instruct the model to return ONLY a
        # JSON array of {question, answer} objects so that the parsing helper
        # can be reused.
        system_message = (
            "You are a helpful teaching assistant generating flashcard-style quiz "
            "questions from textbook pages. "
            "Return ONLY a JSON array of objects, each with 'question' and "
            "'answer' string fields. Do not include any extra commentary."
        )
        user_instructions = (
            f"Generate up to {self.max_questions} concise question/answer pairs "
            f"based on this textbook page (page {page_num}). "
            "Focus on key concepts, definitions, and how they are applied. "
            "Again, respond ONLY with JSON."
        )

        # Some providers (notably the Google-backed Gemma 3 API) do not yet
        # support separate system / developer messages and will return a 400
        # if we send them. For those models we fold the system instructions
        # into the user message so the prompt is still respected.
        model_name = (self.model or "openai/gpt-4o-mini").lower()
        if "google/gemma-3" in model_name:
            combined_instructions = f"{system_message} {user_instructions}"
            messages = [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": combined_instructions},
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:image/png;base64,{image_b64}",
                            },
                        },
                    ],
                }
            ]
        else:
            messages = [
                {"role": "system", "content": system_message},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": user_instructions},
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:image/png;base64,{image_b64}",
                            },
                        },
                    ],
                },
            ]

        payload = {
            "model": self.model or "google/gemma-3-27b-it:free",
            "messages": messages,
        }

        # Prefer structured / JSON-formatted output when supported by the model.
        #
        # OpenRouter exposes an OpenAI-style ``response_format`` parameter that,
        # for compatible models (including Gemma 3 27B via OpenRouter), strongly
        # biases the response to be valid JSON. We still keep natural-language
        # instructions in the prompt, but this flag dramatically reduces the
        # chances of getting non-JSON text that would cause parsing failures.
        #
        # For models that ignore or do not support this parameter, behaviour
        # falls back to the regular text response and our parser remains
        # defensive.
        payload["response_format"] = {"type": "json_object"}

        try:
            response = requests.post(
                "https://openrouter.ai/api/v1/chat/completions",
                headers=headers,
                json=payload,
                timeout=60,
            )
        except requests.RequestException as exc:  # pragma: no cover - network
            logger.error(
                "OpenRouter request failed (%s); falling back to mock provider.", exc
            )
            return self._call_mock(image_b64, prompt, page_num)

        if response.status_code in (401, 403):
            logger.error(
                "OpenRouter authentication failed with status %s; "
                "falling back to mock provider.",
                response.status_code,
            )
            return self._call_mock(image_b64, prompt, page_num)

        if not response.ok:
            # For non-auth HTTP errors we surface an explicit error so that
            # calling code can decide how to handle it.
            raise LLMServiceError(
                f"OpenRouter API error: {response.status_code} {response.text[:200]}"
            )

        try:
            data = response.json()
        except ValueError as exc:  # pragma: no cover - defensive
            raise LLMServiceError(
                f"OpenRouter returned non-JSON response: {exc}"
            ) from exc

        try:
            content = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise LLMServiceError(
                f"Unexpected OpenRouter response structure: {exc}"
            ) from exc

        # Depending on the model, content may be a string or a list of parts.
        if isinstance(content, list):
            text_parts = []
            for part in content:
                if isinstance(part, dict) and part.get("type") == "text":
                    text_parts.append(part.get("text", ""))
            content_text = "\n".join(text_parts)
        else:
            content_text = str(content)

        return _parse_qa_pairs_from_json(content_text)


def _parse_qa_pairs_from_json(text: str) -> List[Dict[str, str]]:
    """
    Utility to parse JSON-formatted Q&A pairs.

    This is not used by the mock provider but is kept as a small, focused
    helper that can be reused when real LLM providers are introduced.
    """

    # Normalise and defensively extract JSON from common wrappers such as
    # Markdown code fences or explanatory text that some models still add
    # around the JSON, even when asked not to.
    raw = text.strip()

    # Strip Markdown ``` fences (optionally with a language hint).
    if raw.startswith("```"):
        lines = raw.splitlines()
        if lines and lines[0].lstrip().startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].lstrip().startswith("```"):
            lines = lines[:-1]
        raw = "\n".join(lines).strip()

    data: List[Dict[str, str]] | Dict[str, object]

    # If the cleaned string does not start with a typical JSON delimiter,
    # attempt to slice out the first top-level array as a best effort before
    # falling back to a direct load.
    if not (raw.startswith("[") or raw.startswith("{")): 
        start = raw.find("[")
        end = raw.rfind("]")
        if start != -1 and end != -1 and start < end:
            candidate = raw[start : end + 1]
            try:
                data = json.loads(candidate)
            except json.JSONDecodeError:
                # Fall back to loading the entire string below so that the
                # caller still receives a clear, debuggable error if parsing
                # ultimately fails.
                try:
                    data = json.loads(raw)
                except json.JSONDecodeError as exc:  # pragma: no cover - defensive
                    raise LLMServiceError(
                        f"Failed to decode LLM JSON response: {exc}"
                    ) from exc
        else:
            try:
                data = json.loads(raw)
            except json.JSONDecodeError as exc:  # pragma: no cover - defensive
                raise LLMServiceError(
                    f"Failed to decode LLM JSON response: {exc}"
                ) from exc
    else:
        try:
            data = json.loads(raw)
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
