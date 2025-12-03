"""
Tests for Admin CLI Module

Tests the administrative CLI operations including status checking and clearing queues.
"""

import pytest
from unittest.mock import Mock, patch, MagicMock
from io import StringIO
import sys

from src.admin import AdminCLI, main, _build_arg_parser
from src.queue_client import QueueClientError, QueueNotFoundError


class TestAdminCLI:
    """Test suite for AdminCLI class."""

    @pytest.fixture
    def mock_config(self):
        """Create a mock configuration."""
        mock_cfg = Mock()
        mock_cfg.queue_service.base_url = "http://localhost:8080"
        return mock_cfg

    @pytest.fixture
    def mock_queue_client(self):
        """Create a mock QueueClient."""
        return Mock()

    @pytest.fixture
    def admin_cli(self, mock_config, mock_queue_client):
        """Create an AdminCLI instance with mocked dependencies."""
        with patch('src.admin.load_config', return_value=mock_config):
            with patch('src.admin.QueueClient', return_value=mock_queue_client):
                cli = AdminCLI(config_path="test_config.yaml")
                cli.queue_client = mock_queue_client
                return cli

    # ========================================================================
    # Initialization Tests
    # ========================================================================

    def test_admin_cli_initialization(self, mock_config):
        """Test that AdminCLI initializes correctly."""
        with patch('src.admin.load_config', return_value=mock_config):
            with patch('src.admin.QueueClient') as mock_client:
                cli = AdminCLI(config_path="config.yaml")
                
                assert cli.config == mock_config
                mock_client.assert_called_once_with("http://localhost:8080")

    # ========================================================================
    # Clear All Queues Tests
    # ========================================================================

    def test_clear_all_queues_with_force(self, admin_cli, mock_queue_client, capsys):
        """Test clearing all queues with --force flag (no confirmation)."""
        # Mock the API response
        mock_queue_client.clear_all_queues.return_value = {
            'message': 'All queues cleared successfully',
            'queuesCleared': 3
        }

        # Call with force=True (skip confirmation)
        admin_cli.clear_all_queues(force=True)

        # Verify API was called
        mock_queue_client.clear_all_queues.assert_called_once()

        # Verify output
        captured = capsys.readouterr()
        assert "✅ Success!" in captured.out
        assert "All queues cleared successfully" in captured.out
        assert "Queues cleared: 3" in captured.out

    def test_clear_all_queues_with_confirmation_yes(self, admin_cli, mock_queue_client, capsys):
        """Test clearing queues with confirmation prompt (user confirms)."""
        mock_queue_client.clear_all_queues.return_value = {
            'message': 'All queues cleared successfully',
            'queuesCleared': 2
        }

        # Mock user input: user types 'DELETE ALL'
        with patch('builtins.input', return_value='DELETE ALL'):
            admin_cli.clear_all_queues(force=False)

        # Verify API was called
        mock_queue_client.clear_all_queues.assert_called_once()

        # Verify warning and success messages
        captured = capsys.readouterr()
        assert "⚠️  WARNING" in captured.out
        assert "✅ Success!" in captured.out

    def test_clear_all_queues_with_confirmation_no(self, admin_cli, mock_queue_client, capsys):
        """Test clearing queues when user cancels confirmation."""
        # Mock user input: user types something other than 'DELETE ALL'
        with patch('builtins.input', return_value='no'):
            with pytest.raises(SystemExit) as exc_info:
                admin_cli.clear_all_queues(force=False)

        # Verify exit code is 0 (user cancelled, not an error)
        assert exc_info.value.code == 0

        # Verify API was NOT called
        mock_queue_client.clear_all_queues.assert_not_called()

        # Verify cancellation message
        captured = capsys.readouterr()
        assert "❌ Operation cancelled" in captured.out

    def test_clear_all_queues_api_error(self, admin_cli, mock_queue_client, capsys):
        """Test handling of API errors when clearing queues."""
        # Mock API error
        mock_queue_client.clear_all_queues.side_effect = QueueClientError("Connection refused")

        # Should exit with code 1
        with pytest.raises(SystemExit) as exc_info:
            admin_cli.clear_all_queues(force=True)

        assert exc_info.value.code == 1

        # Verify error message
        captured = capsys.readouterr()
        assert "❌ Error: Connection refused" in captured.err

    # ========================================================================
    # Check Status Tests
    # ========================================================================

    def test_check_status_in_progress(self, admin_cli, mock_queue_client, capsys):
        """Test status check for queue in progress."""
        # Mock API response: queue in progress
        mock_queue_client.get_queue_status.return_value = {
            'pendingTaskCount': 7,
            'completedResultCount': 3,
            'hasPendingTasks': True
        }

        admin_cli.check_status(queue_id="test-queue-123")

        # Verify API was called with correct queue ID
        mock_queue_client.get_queue_status.assert_called_once_with("test-queue-123")

        # Verify output
        captured = capsys.readouterr()
        assert "Queue Status: test-queue-123" in captured.out
        assert "🔄  In Progress" in captured.out
        assert "Pending Tasks:    7" in captured.out
        assert "Completed Results: 3" in captured.out
        assert "30.0%" in captured.out  # Progress percentage

    def test_check_status_complete(self, admin_cli, mock_queue_client, capsys):
        """Test status check for completed queue."""
        # Mock API response: queue complete
        mock_queue_client.get_queue_status.return_value = {
            'pendingTaskCount': 0,
            'completedResultCount': 10,
            'hasPendingTasks': False
        }

        admin_cli.check_status(queue_id="test-queue-456")

        # Verify output shows complete status
        captured = capsys.readouterr()
        assert "✅  Complete" in captured.out
        assert "Pending Tasks:    0" in captured.out
        assert "Completed Results: 10" in captured.out
        assert "100.0%" in captured.out

    def test_check_status_idle(self, admin_cli, mock_queue_client, capsys):
        """Test status check for idle/empty queue."""
        # Mock API response: queue idle
        mock_queue_client.get_queue_status.return_value = {
            'pendingTaskCount': 0,
            'completedResultCount': 0,
            'hasPendingTasks': False
        }

        admin_cli.check_status(queue_id="test-queue-789")

        # Verify output shows idle status
        captured = capsys.readouterr()
        assert "⏸️  Idle" in captured.out
        assert "Pending Tasks:    0" in captured.out
        assert "Completed Results: 0" in captured.out

    def test_check_status_queue_not_found(self, admin_cli, mock_queue_client, capsys):
        """Test status check when queue doesn't exist."""
        # Mock API error: queue not found
        mock_queue_client.get_queue_status.side_effect = QueueClientError("Queue not found")

        # Should exit with code 1
        with pytest.raises(SystemExit) as exc_info:
            admin_cli.check_status(queue_id="nonexistent-queue")

        assert exc_info.value.code == 1

        # Verify error message
        captured = capsys.readouterr()
        assert "❌ Error: Queue not found" in captured.err

    def test_check_status_progress_bar(self, admin_cli, mock_queue_client, capsys):
        """Test that progress bar is displayed correctly."""
        mock_queue_client.get_queue_status.return_value = {
            'pendingTaskCount': 5,
            'completedResultCount': 5,
            'hasPendingTasks': True
        }

        admin_cli.check_status(queue_id="test-queue")

        captured = capsys.readouterr()
        # Progress bar should show 50% completion
        assert "50.0%" in captured.out
        assert "[" in captured.out  # Progress bar brackets
        assert "█" in captured.out  # Filled portion
        assert "░" in captured.out  # Unfilled portion

    # ========================================================================
    # Argument Parser Tests
    # ========================================================================

    def test_arg_parser_status_command(self):
        """Test argument parser for status command."""
        parser = _build_arg_parser()
        args = parser.parse_args(['status', 'test-queue-id'])

        assert args.command == 'status'
        assert args.queue_id == 'test-queue-id'
        assert args.config == 'config.yaml'  # Default

    def test_arg_parser_status_with_custom_config(self):
        """Test status command with custom config."""
        parser = _build_arg_parser()
        args = parser.parse_args(['status', 'queue-123', '--config', 'custom.yaml'])

        assert args.command == 'status'
        assert args.queue_id == 'queue-123'
        assert args.config == 'custom.yaml'

    def test_arg_parser_clear_command(self):
        """Test argument parser for clear command."""
        parser = _build_arg_parser()
        args = parser.parse_args(['clear'])

        assert args.command == 'clear'
        assert args.force is False  # Default
        assert args.config == 'config.yaml'  # Default

    def test_arg_parser_clear_with_force(self):
        """Test clear command with --force flag."""
        parser = _build_arg_parser()
        args = parser.parse_args(['clear', '--force'])

        assert args.command == 'clear'
        assert args.force is True

    def test_arg_parser_clear_with_custom_config(self):
        """Test clear command with custom config."""
        parser = _build_arg_parser()
        args = parser.parse_args(['clear', '--config', 'test.yaml'])

        assert args.command == 'clear'
        assert args.config == 'test.yaml'

    def test_arg_parser_no_command(self):
        """Test that parser requires a command."""
        parser = _build_arg_parser()
        
        with pytest.raises(SystemExit):
            parser.parse_args([])  # No command provided

    def test_arg_parser_status_missing_queue_id(self):
        """Test that status command requires queue ID."""
        parser = _build_arg_parser()
        
        with pytest.raises(SystemExit):
            parser.parse_args(['status'])  # Missing queue ID

    # ========================================================================
    # Main Function Tests
    # ========================================================================

    @patch('src.admin.AdminCLI')
    def test_main_status_command(self, mock_admin_class):
        """Test main function with status command."""
        mock_admin = Mock()
        mock_admin_class.return_value = mock_admin

        test_args = ['admin.py', 'status', 'test-queue-123']
        with patch.object(sys, 'argv', test_args):
            with pytest.raises(SystemExit) as exc_info:
                main()

        # Should exit with 0 (success)
        assert exc_info.value.code == 0

        # Verify AdminCLI was called correctly
        mock_admin.check_status.assert_called_once_with(queue_id='test-queue-123')

    @patch('src.admin.AdminCLI')
    def test_main_clear_command(self, mock_admin_class):
        """Test main function with clear command."""
        mock_admin = Mock()
        mock_admin_class.return_value = mock_admin

        test_args = ['admin.py', 'clear', '--force']
        with patch.object(sys, 'argv', test_args):
            with pytest.raises(SystemExit) as exc_info:
                main()

        # Should exit with 0 (success)
        assert exc_info.value.code == 0

        # Verify clear was called with force=True
        mock_admin.clear_all_queues.assert_called_once_with(force=True)

    @patch('src.admin.load_config')
    def test_main_config_not_found(self, mock_load_config, capsys):
        """Test main function when config file is not found."""
        mock_load_config.side_effect = FileNotFoundError("config.yaml not found")

        test_args = ['admin.py', 'status', 'test-queue']
        with patch.object(sys, 'argv', test_args):
            with pytest.raises(SystemExit) as exc_info:
                main()

        # Should exit with 1 (error)
        assert exc_info.value.code == 1

        # Verify error message
        captured = capsys.readouterr()
        assert "Configuration file not found" in captured.err

    @patch('src.admin.AdminCLI')
    def test_main_keyboard_interrupt(self, mock_admin_class, capsys):
        """Test main function handles Ctrl+C gracefully."""
        mock_admin = Mock()
        mock_admin.check_status.side_effect = KeyboardInterrupt()
        mock_admin_class.return_value = mock_admin

        test_args = ['admin.py', 'status', 'test-queue']
        with patch.object(sys, 'argv', test_args):
            with pytest.raises(SystemExit) as exc_info:
                main()

        # Should exit with 130 (Ctrl+C convention)
        assert exc_info.value.code == 130

        # Verify cancellation message
        captured = capsys.readouterr()
        assert "Operation cancelled by user" in captured.out

    @patch('src.admin.AdminCLI')
    def test_main_unexpected_error(self, mock_admin_class, capsys):
        """Test main function handles unexpected errors."""
        mock_admin = Mock()
        mock_admin.clear_all_queues.side_effect = Exception("Unexpected error")
        mock_admin_class.return_value = mock_admin

        test_args = ['admin.py', 'clear', '--force']
        with patch.object(sys, 'argv', test_args):
            with pytest.raises(SystemExit) as exc_info:
                main()

        # Should exit with 1 (error)
        assert exc_info.value.code == 1

        # Verify error message
        captured = capsys.readouterr()
        assert "Unexpected error" in captured.err


class TestAdminCLIIntegration:
    """Integration-style tests with more realistic scenarios."""

    @patch('src.admin.load_config')
    @patch('src.admin.QueueClient')
    def test_full_status_workflow(self, mock_client_class, mock_load_config, capsys):
        """Test complete status checking workflow."""
        # Setup mocks
        mock_config = Mock()
        mock_config.queue_service.base_url = "http://localhost:8080"
        mock_load_config.return_value = mock_config

        mock_client = Mock()
        mock_client.get_queue_status.return_value = {
            'pendingTaskCount': 3,
            'completedResultCount': 7,
            'hasPendingTasks': True
        }
        mock_client_class.return_value = mock_client

        # Run admin CLI
        cli = AdminCLI()
        cli.check_status("abc123")

        # Verify the full workflow
        captured = capsys.readouterr()
        assert "Queue Status: abc123" in captured.out
        assert "Pending Tasks:    3" in captured.out
        assert "Completed Results: 7" in captured.out

    @patch('src.admin.load_config')
    @patch('src.admin.QueueClient')
    def test_full_clear_workflow_with_force(self, mock_client_class, mock_load_config, capsys):
        """Test complete clear workflow with force flag."""
        # Setup mocks
        mock_config = Mock()
        mock_config.queue_service.base_url = "http://localhost:8080"
        mock_load_config.return_value = mock_config

        mock_client = Mock()
        mock_client.clear_all_queues.return_value = {
            'message': 'Cleared successfully',
            'queuesCleared': 5
        }
        mock_client_class.return_value = mock_client

        # Run admin CLI with force
        cli = AdminCLI()
        cli.clear_all_queues(force=True)

        # Verify the full workflow
        mock_client.clear_all_queues.assert_called_once()
        captured = capsys.readouterr()
        assert "✅ Success!" in captured.out
        assert "Queues cleared: 5" in captured.out

