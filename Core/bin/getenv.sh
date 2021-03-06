#!/bin/sh

#
# Copyright (C) 2021 Radix IoT LLC. All rights reserved.
# @author Jared Wiltshire
#

set -e

if [ -z "$SCRIPT_DIR" ]; then
	SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
fi

# function for getting values from env.properties file
# does not support multiline properties or properties containing = sign
get_prop() {
	awk -v name="$1" -v dval="$2" -F = '$1==name{print $2; f=1; exit} END{if(f!=1){print dval}}' "$MA_ENV_PROPERTIES"
}

is_relative() {
	case "$1" in /*) return 1;; esac
}

resolve_path() {
	if is_relative "$2"; then
		printf %s/ "$1"
	fi
	printf %s "$2"
}

[ -n "$mango_config" ] && MA_ENV_PROPERTIES="$mango_config"
[ -n "$mango_paths_home" ] && MA_HOME="$mango_paths_home"
[ -n "$mango_paths_data" ] && MA_DATA="$mango_paths_data"

if [ -z "$MA_HOME" ]; then
	possible_ma_home="$(dirname -- "$SCRIPT_DIR")"
	if [ -e "$possible_ma_home/release.signed" ] || [ -e "$possible_ma_home/release.properties" ]; then
		MA_HOME="$possible_ma_home"
	else
		MA_HOME=/opt/mango
	fi
fi

if [ -z "$MA_ENV_PROPERTIES" ]; then
  if [ -f "$MA_HOME/env.properties" ]; then
	  MA_ENV_PROPERTIES="$MA_HOME/env.properties"
  elif [ -f "$MA_HOME/overrides/properties/env.properties" ]; then
	  MA_ENV_PROPERTIES="$MA_HOME/overrides/properties/env.properties"
  elif [ -f /opt/mango-data/env.properties ]; then
	  MA_ENV_PROPERTIES=/opt/mango-data/env.properties
  fi
fi

if [ ! -f "$MA_ENV_PROPERTIES" ]; then
	echo "Config file '$MA_ENV_PROPERTIES' does not exist"
	exit 1
fi

if [ -z "$MA_DATA" ]; then
	data_path="$(get_prop "paths.data" "$MA_HOME")"
	MA_DATA="$(resolve_path "$MA_HOME" "$data_path")"
fi

if [ -z "$MA_KEYSTORE" ]; then
	keystore="$(get_prop "ssl.keystore.location" "$MA_DATA/keystore.p12")"
	MA_KEYSTORE="$(resolve_path "$MA_DATA" "$keystore")"
fi

if [ -z "$MA_KEYSTORE_PASSWORD" ]; then
	MA_KEYSTORE_PASSWORD="$(get_prop 'ssl.keystore.password' 'freetextpassword')"
fi

if [ -z "$MA_KEY_PASSWORD" ]; then
	MA_KEY_PASSWORD="$(get_prop 'ssl.key.password' "$MA_KEYSTORE_PASSWORD")"
fi

if [ -z "$MA_KEY_ALIAS" ]; then
	MA_KEY_ALIAS=mango
fi

keytool_cmd="$JAVA_HOME/bin/keytool"
if [ -z "$JAVA_HOME" ] || [ ! -x "$keytool_cmd" ]; then
	# use keytool from PATH
	keytool_cmd=keytool
fi

if [ ! -d "$MA_HOME" ]; then
    echo 'Error: MA_HOME is not set or is not a directory'
    exit 1
fi
if [ ! -d "$MA_DATA" ]; then
    echo 'Error: MA_DATA is not set or is not a directory'
    exit 1
fi
echo MA_HOME is "$MA_HOME"
echo MA_DATA is "$MA_DATA"
