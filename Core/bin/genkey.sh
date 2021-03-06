#!/bin/sh

#
# Copyright (C) 2021 Radix IoT LLC. All rights reserved.
# @author Jared Wiltshire
#

set -e
umask 077

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
. "$SCRIPT_DIR"/getenv.sh

"$keytool_cmd" -genkey -noprompt -keyalg EC -alias "$MA_KEY_ALIAS" -dname "CN=$(hostname)" -keystore "$MA_KEYSTORE" \
-storetype PKCS12 -storepass "$MA_KEYSTORE_PASSWORD" -keypass "$MA_KEY_PASSWORD"

# ensure user read only permission
chmod 400 "$MA_KEYSTORE"
