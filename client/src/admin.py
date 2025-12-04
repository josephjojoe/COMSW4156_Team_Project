"""
Administrative CLI for Queue Service

Purpose: Perform administrative operations on the queue service such as
clearing all queues, checking service health, etc.

WARNING: These operations can be destructive. Use with caution.
"""

import argparse
import sys
import logging
from typing import NoReturn

from src.config import load_config
from src.queue_client import QueueClient, QueueClientError

logger = logging.getLogger(__name__)


class AdminCLI:
    """Administrative command-line interface for queue service operations."""

    def __init__(self, config_path: str = "config.yaml"):
        """
        Initialize the admin CLI.

        Args:
            config_path: Path to configuration YAML file
        """
        self.config = load_config(config_path)
        self.queue_client = QueueClient(self.config.queue_service.base_url)

        logger.info(
            "Admin CLI initialized (service=%s)",
            self.config.queue_service.base_url,
        )

    def clear_all_queues(self, force: bool = False) -> None:
        """
        Clear all queues from the service.

        WARNING: This is a destructive operation that permanently deletes
        all queues, tasks, and results. Cannot be undone.

        Args:
            force: If True, skip confirmation prompt
        """
        if not force:
            print("⚠️  WARNING: This will permanently delete ALL queues, tasks, and results!")
            print("⚠️  This operation CANNOT be undone!")
            print()
            confirmation = input("Type 'DELETE ALL' to confirm: ")

            if confirmation != "DELETE ALL":
                print("❌ Operation cancelled.")
                sys.exit(0)

        try:
            logger.info("Requesting clear all queues from service...")
            response = self.queue_client.clear_all_queues()

            message = response.get('message', 'All queues cleared')
            queues_cleared = response.get('queuesCleared', 0)

            print()
            print("✅ Success!")
            print(f"   {message}")
            print(f"   Queues cleared: {queues_cleared}")
            print()

            logger.info("Cleared %d queues successfully", queues_cleared)

        except QueueClientError as exc:
            print(f"\n❌ Error: {exc}\n", file=sys.stderr)
            logger.error("Failed to clear queues: %s", exc)
            sys.exit(1)

    def check_status(self, queue_id: str) -> None:
        """
        Check the status of a specific queue.

        Shows pending tasks, completed results, and overall queue status.

        Args:
            queue_id: ID of the queue to check
        """
        try:
            logger.info("Fetching status for queue %s...", queue_id)
            status = self.queue_client.get_queue_status(queue_id)

            pending = status.get('pendingTaskCount', 0)
            completed = status.get('completedResultCount', 0)
            has_pending = status.get('hasPendingTasks', False)

            # Calculate progress
            total = pending + completed
            if total > 0:
                progress_pct = (completed / total) * 100
            else:
                progress_pct = 0

            # Determine status emoji and text
            if pending == 0 and completed > 0:
                status_emoji = "✅"
                status_text = "Complete"
            elif has_pending:
                status_emoji = "🔄"
                status_text = "In Progress"
            else:
                status_emoji = "⏸️"
                status_text = "Idle"

            # Print formatted status
            print()
            print("=" * 60)
            print(f"Queue Status: {queue_id}")
            print("=" * 60)
            print()
            print(f"  Status:           {status_emoji}  {status_text}")
            print(f"  Pending Tasks:    {pending}")
            print(f"  Completed Results: {completed}")
            
            if total > 0:
                print(f"  Progress:         {progress_pct:.1f}% ({completed}/{total})")
                # Progress bar
                bar_width = 40
                filled = int(bar_width * completed / total)
                bar = "█" * filled + "░" * (bar_width - filled)
                print(f"                    [{bar}]")
            
            print()
            print("=" * 60)
            print()

            logger.info(
                "Queue %s status: pending=%d completed=%d",
                queue_id,
                pending,
                completed,
            )

        except QueueClientError as exc:
            print(f"\n❌ Error: {exc}\n", file=sys.stderr)
            logger.error("Failed to get queue status: %s", exc)
            sys.exit(1)


def _build_arg_parser() -> argparse.ArgumentParser:
    """Create the CLI argument parser for admin operations."""
    parser = argparse.ArgumentParser(
        description="Administrative CLI for queue service operations",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
    Examples:
    # Check queue status
    python -m src.admin status <queue-id>

    # Clear all queues (with confirmation prompt)
    python -m src.admin clear

    # Clear all queues (skip confirmation - dangerous!)
    python -m src.admin clear --force

    # Use custom config file
    python -m src.admin status <queue-id> --config custom.yaml

    WARNING: Clear operation is destructive and cannot be undone!
            """,
    )

    subparsers = parser.add_subparsers(dest='command', help='Admin command to execute')
    subparsers.required = True

    # Status command
    status_parser = subparsers.add_parser(
        'status',
        help='Check queue status',
        description='Display the status of a queue including pending tasks and completed results.',
    )
    status_parser.add_argument(
        'queue_id',
        type=str,
        help='ID of the queue to check',
    )
    status_parser.add_argument(
        '--config',
        type=str,
        default='config.yaml',
        help='Path to configuration file (default: config.yaml)',
    )

    # Clear command
    clear_parser = subparsers.add_parser(
        'clear',
        help='Clear all queues (DESTRUCTIVE)',
        description='Clear all queues, tasks, and results from the service. '
                    'WARNING: This operation is permanent and cannot be undone!',
    )
    clear_parser.add_argument(
        '--force',
        action='store_true',
        help='Skip confirmation prompt (use with caution!)',
    )
    clear_parser.add_argument(
        '--config',
        type=str,
        default='config.yaml',
        help='Path to configuration file (default: config.yaml)',
    )

    return parser


def main() -> NoReturn:
    """
    CLI entry point for admin operations.

    Usage:
        python -m src.admin status <queue-id> [--config PATH]
        python -m src.admin clear [--force] [--config PATH]
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    parser = _build_arg_parser()
    args = parser.parse_args()

    try:
        admin_cli = AdminCLI(config_path=args.config)

        if args.command == 'status':
            admin_cli.check_status(queue_id=args.queue_id)
        elif args.command == 'clear':
            admin_cli.clear_all_queues(force=args.force)

        sys.exit(0)

    except FileNotFoundError as exc:
        logger.error("Configuration file not found: %s", exc)
        print(f"\n❌ Error: Configuration file not found: {exc}\n", file=sys.stderr)
        sys.exit(1)

    except KeyboardInterrupt:
        print("\n\n❌ Operation cancelled by user.\n")
        sys.exit(130)

    except Exception as exc:
        logger.exception("Admin operation failed: %s", exc)
        print(f"\n❌ Unexpected error: {exc}\n", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()