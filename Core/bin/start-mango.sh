#!/bin/sh

#
# Copyright (C) 2021 Radix IoT LLC. All rights reserved.
# @author Jared Wiltshire
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
. "$SCRIPT_DIR"/getenv.sh

if [ -e "$MA_DATA"/ma.pid ]; then
	PID="$(cat "$MA_DATA"/ma.pid)"
	if ps -p "$PID" > /dev/null 2>&1; then
		echo "Mango is already running at PID $PID"
		exit 2
	fi
	# Clean up old PID file
	rm -f "$MA_DATA"/ma.pid
fi

# This will ensure that the logs are written to the correct directories.
cd "$MA_HOME"

# Determine the Java home
if [ -d "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    EXECJAVA="$JAVA_HOME/bin/java"
elif [ -x "$(command -v java)" ]; then
    EXECJAVA=java
else
	echo "JAVA_HOME not set and java not found on path"
	exit 3
fi

# Check for core upgrade
for f in "$MA_HOME"/m2m2-core-*.zip; do
	if [ -r "$f" ]; then
		echo 'Upgrading core...'

		# Delete jars and work dir
		rm -f "$MA_HOME"/lib/*.jar
		rm -rf "$MA_HOME"/work

		# Delete the release properties files
		rm -f "$MA_HOME"/release.properties
		rm -f "$MA_HOME"/release.signed

		# Unzip core. The exact name is unknown, but there should only be one, so iterate
		unzip -o "$f"
    rm "$f"

		chmod +x "$MA_HOME"/bin/*.sh
	fi
done

# Construct the Java classpath
MA_CP="$MA_HOME/lib/*"

if [ -e "$MA_DATA/start-options.sh" ]; then
	. "$MA_DATA/start-options.sh"
fi

if [ -n "$MA_JAVA_OPTS" ]; then
	echo "Starting Mango Automation with options '$MA_JAVA_OPTS'"
else
	echo "Starting Mango Automation"
fi

CLASSPATH="$MA_CP" \
"$EXECJAVA" $MA_JAVA_OPTS -server \
	"-Dmango.config=$MA_ENV_PROPERTIES" \
	"-Dmango.paths.home=$MA_HOME" \
	"-Dmango.paths.data=$MA_DATA" \
	com.serotonin.m2m2.Main &

PID=$!
echo $PID > "$MA_DATA"/ma.pid
echo "Mango Automation started with process ID: " $PID

exit 0
