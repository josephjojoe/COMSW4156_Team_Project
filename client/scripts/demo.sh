#!/bin/bash
#
# Demo Script for PDF Quiz Generator
#
# Purpose: Automate the full demo workflow for class presentation
#
# Flow:
#   1. Check if queue service is running
#   2. Run producer with sample PDF
#   3. Start 3 worker processes in background
#   4. Monitor progress
#   5. Run aggregator when complete
#   6. Display final Anki deck
#
# Usage: ./scripts/demo.sh path/to/sample.pdf
#
