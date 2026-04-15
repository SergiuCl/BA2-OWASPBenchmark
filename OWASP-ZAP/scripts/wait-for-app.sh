#!/bin/bash
#
# Copyright Notice
#
# Copyright (c) mediamid digital services GmbH (www.mediamid.com), 2025
# All rights reserved.
#
# Proprietary of the copyright owners. Contains confidential information.
# Use is subject to license terms.
#
#  $Id$
#  $Revision$
#

# wait-for-app.sh - Wait for application to be ready before starting scan

set -e

# Default values
DEFAULT_TIMEOUT=60
DEFAULT_INTERVAL=2

# Parse arguments
TARGET_URL="${1:-https://127.0.0.1:8443/benchmark}"
TIMEOUT="${2:-$DEFAULT_TIMEOUT}"
INTERVAL="${3:-$DEFAULT_INTERVAL}"

echo "Waiting for application to be ready..."
echo "   Target: $TARGET_URL"
echo "   Timeout: ${TIMEOUT}s"
echo "   Check interval: ${INTERVAL}s"

elapsed=0
while [ $elapsed -lt $TIMEOUT ]; do
    # Check if curl is available, if not try to install it or use alternative
    if command -v curl >/dev/null 2>&1; then
        if curl -s -o /dev/null -w "%{http_code}" "$TARGET_URL" | grep -q "200\|301\|302"; then
            echo "------ Application is ready! ------"
            exit 0
        fi
    else
        echo "Warning: curl not available, using basic connectivity test"
        # Alternative using wget if curl is not available
        if wget -q --spider "$TARGET_URL" 2>/dev/null; then
            echo "------ Application is ready! ------"
            exit 0
        fi
    fi

    echo "Waiting... ($elapsed/$TIMEOUT seconds elapsed)"
    sleep $INTERVAL
    elapsed=$((elapsed + INTERVAL))
done

echo "Timeout waiting for application to be ready"
echo "Application at $TARGET_URL did not respond within ${TIMEOUT} seconds"
exit 1