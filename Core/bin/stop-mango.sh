#!/bin/sh

#
# Copyright (C) 2020 Infinite Automation Systems Inc. All rights reserved.
# @author Jared Wiltshire
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
. "$SCRIPT_DIR"/getenv.sh

SIGNAL=TERM
[ -n "$1" ] && SIGNAL="$1"

if [ -e "$MA_DATA/ma.pid" ]; then
	PID="$(cat "$MA_DATA/ma.pid")"
	echo "Killing Mango PID $PID"

	while kill -"$SIGNAL" "$PID" > /dev/null 2>&1; do
		sleep 1
	done

	# Clean up PID file
	rm -f "$MA_DATA/ma.pid"
else
	echo "Mango PID file $MA_DATA/ma.pid does not exist, did you start Mango using start-mango.sh?"
	exit 2
fi

exit 0
