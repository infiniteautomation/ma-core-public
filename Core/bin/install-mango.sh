#!/bin/sh

#
# Copyright (C) 2021 Radix IoT LLC. All rights reserved.
# @author Jared Wiltshire
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"

# Prompts the user for input, uses the second argument as the default
prompt() {
	printf "%s [%s]: " "$1" "$2" > /dev/tty
	read -r result
	[ -z "$result" ] && result="$2"
	printf "%s" "$result"
}

if [ -z "$MA_HOME" ]; then
	DEFAULT_HOME=/opt/mango
  CHECK_DIR="$(dirname -- "$SCRIPT_DIR")"
  if [ -e "$CHECK_DIR/release.signed" ] || [ -e "$CHECK_DIR/release.properties" ]; then
      DEFAULT_HOME="$CHECK_DIR"
  fi
  MA_HOME="$(prompt 'Where should we install Mango?' "$DEFAULT_HOME")"
fi

# Create the MA_HOME directory if it does not exist
if [ ! -d "$MA_HOME" ]; then
	mkdir "$MA_HOME"
	echo "Created MA_HOME directory '$MA_HOME'."
fi

[ -z "$MA_SERVICE_NAME" ] && MA_SERVICE_NAME=mango
if [ -z "$MA_DATA" ]; then
	DEFAULT_DATA_PATH=/opt/"$MA_SERVICE_NAME"-data
  MA_DATA="$(prompt 'Where should we store Mango data?' "$DEFAULT_DATA_PATH")"
fi

# Create the MA_DATA directory if it does not exist
if [ ! -d "$MA_DATA" ]; then
	mkdir "$MA_DATA"
	echo "Created MA_DATA directory '$MA_DATA'."
fi

if [ -z "$MA_USER" ]; then
	MA_USER="$(prompt 'Which OS user should Mango run as? (Will be created if it does not exist)' 'mango')"
fi

# Create the Mango user if it does not exist
if [ ! "$(id -u "$MA_USER" 2> /dev/null)" ]; then
	NO_LOGIN_SHELL="$(command -v nologin)" || true
	[ ! -x "$NO_LOGIN_SHELL" ] && NO_LOGIN_SHELL=/bin/false

	USER_ADD_CMD="$(command -v useradd)" || true
	if [ ! -x "$USER_ADD_CMD" ]; then
		echo "Can't create user '$MA_USER' as useradd command does not exist."
		exit 1;
	fi

	"$USER_ADD_CMD" --system --no-create-home --home-dir "$MA_HOME" --shell "$NO_LOGIN_SHELL" --comment 'Mango Automation' "$MA_USER"
	echo "Created user '$MA_USER'."
fi
[ -z "$MA_GROUP" ] && MA_GROUP="$(id -gn "$MA_USER")"

# Stop and remove any existing mango service
if [ -x "$(command -v systemctl)" ]; then
	systemctl stop "$MA_SERVICE_NAME" 2> /dev/null || true
	systemctl disable "$MA_SERVICE_NAME" 2> /dev/null || true
fi

while [ "$MA_DB_TYPE" != 'mysql' ] && [ "$MA_DB_TYPE" != 'h2' ]; do
	MA_DB_TYPE="$(prompt 'What type of SQL database? (h2 or mysql)' 'h2')"
done

# Set default environment variables for DB
if [ -z "$MA_DB_NAME" ]; then
	if [ "$MA_DB_TYPE" = 'mysql' ]; then
		MA_DB_NAME="$MA_SERVICE_NAME"
	else
		MA_DB_NAME=mah2
	fi
fi
[ -z "$MA_DB_USER" ] && MA_DB_USER="$MA_SERVICE_NAME"
[ -z "$MA_DB_PASSWORD" ] && MA_DB_PASSWORD="$(openssl rand -base64 24)"

while [ "$MA_DB_TYPE" = 'mysql' ] && [ "$MA_CONFIRM_DROP" != 'yes' ] && [ "$MA_CONFIRM_DROP" != 'no' ]; do
	prompt_text="Drop SQL database '$MA_DB_NAME' and user '$MA_DB_USER'? (If they exist)"
  MA_CONFIRM_DROP="$(prompt "$prompt_text" 'no')"
done

if [ "$MA_DB_TYPE" = 'mysql' ] && [ "$MA_CONFIRM_DROP" = 'yes' ]; then
	echo "DROP DATABASE IF EXISTS $MA_DB_NAME;
		DROP USER IF EXISTS '$MA_DB_USER'@'localhost';" | mysql -u root
fi

if [ "$MA_DB_TYPE" = 'mysql' ]; then
	echo "CREATE DATABASE IF NOT EXISTS $MA_DB_NAME;
	CREATE USER IF NOT EXISTS '$MA_DB_USER'@'localhost' IDENTIFIED BY '$MA_DB_PASSWORD';
	GRANT ALL ON $MA_DB_NAME.* TO '$MA_DB_USER'@'localhost';" | mysql -u root
fi

[ -z "$MA_DOWNLOAD_ZIP" ] && MA_DOWNLOAD_ZIP=yes

# Remove any old files in MA_HOME
if [ "$(find "$MA_HOME" -mindepth 1 -maxdepth 1)" ]; then
	while [ "$MA_CONFIRM_DELETE" != 'yes' ] && [ "$MA_CONFIRM_DELETE" != 'no' ]; do
		prompt_text="Installation directory is not empty, delete all files in '$MA_HOME'? (May contain H2 databases and time series databases)"
		MA_CONFIRM_DELETE="$(prompt "$prompt_text" 'no')"
	done

	if [ "$MA_CONFIRM_DELETE" = 'yes' ]; then
	  find "$MA_HOME" -mindepth 1 -maxdepth 1 -exec rm -r '{}' \;
  else
    MA_DOWNLOAD_ZIP=no
	fi
fi

# Remove any old files in MA_DATA
if [ "$(find "$MA_DATA" -mindepth 1 -maxdepth 1)" ]; then
	while [ "$MA_CONFIRM_DELETE_DATA" != 'yes' ] && [ "$MA_CONFIRM_DELETE_DATA" != 'no' ]; do
		prompt_text="Data directory is not empty, delete all files in '$MA_DATA'? (May contain H2 databases and time series databases)"
		MA_CONFIRM_DELETE_DATA="$(prompt "$prompt_text" 'no')"
	done

	if [ "$MA_CONFIRM_DELETE_DATA" = 'yes' ]; then
	  find "$MA_DATA" -mindepth 1 -maxdepth 1 -exec rm -r '{}' \;
	fi
fi

if [ "$MA_DOWNLOAD_ZIP" = 'yes' ]; then
  if [ ! -f "$MA_CORE_ZIP" ] && [ -z "$MA_VERSION" ]; then
    MA_VERSION="$(prompt 'What version of Mango do you want to install?' '4.0.0')"
  fi

  if [ ! -f "$MA_CORE_ZIP" ] && [ -z "$MA_BUNDLE_TYPE" ]; then
    MA_BUNDLE_TYPE="$(prompt 'Install free (personal/educational use, 300 point limit) or enterprise (evaluation) bundle?' 'enterprise')"
  fi

  # Download and extract the Mango enterprise archive
  if [ ! -f "$MA_CORE_ZIP" ]; then
    MA_CORE_ZIP=$(mktemp)
    curl "https://store.infiniteautomation.com/downloads/fullCores/${MA_BUNDLE_TYPE}-m2m2-core-${MA_VERSION}.zip" > "$MA_CORE_ZIP"
    MA_DELETE_ZIP=true
  fi

  jar_cmd="$(command -v jar)" || true
  [ -d "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/jar" ] && jar_cmd="$JAVA_HOME/bin/jar"

  if [ -x "$(command -v unzip)" ]; then
    unzip "$MA_CORE_ZIP" -d "$MA_HOME"
  elif [ -x "$jar_cmd" ]; then
    cd "$MA_HOME" && "$jar_cmd" xvf "$MA_CORE_ZIP"
  else
    echo "Can't find command to extract zip file, please install unzip"
    exit 3
  fi

  [ "$MA_DELETE_ZIP" = true ] && rm -f "$MA_CORE_ZIP"
fi

MA_ENV_PROPERTIES="$MA_DATA"/env.properties
if [ "$MA_DB_TYPE" = 'mysql' ]; then
	echo "db.url=jdbc:mysql://localhost/$MA_DB_NAME" > "$MA_ENV_PROPERTIES"
elif [ "$MA_DB_TYPE" = 'h2' ]; then
	echo "db.url=jdbc:h2:$MA_DATA/databases/$MA_DB_NAME" > "$MA_ENV_PROPERTIES"
else
	echo "Unknown database type $MA_DB_TYPE"
	exit 2;
fi

MA_KEYSTORE="$MA_DATA/keystore.p12"
MA_KEYSTORE_PASSWORD="$(openssl rand -base64 24)"

echo "paths.home=$MA_HOME
paths.data=$MA_DATA
db.type=$MA_DB_TYPE
db.username=$MA_DB_USER
db.password=$MA_DB_PASSWORD
web.openBrowserOnStartup=false
ssl.on=true
ssl.keystore.location=$MA_KEYSTORE
ssl.keystore.password=$MA_KEYSTORE_PASSWORD" >> "$MA_ENV_PROPERTIES"

chmod 600 "$MA_ENV_PROPERTIES"

chmod +x "$MA_HOME"/bin/*.sh
cp "$MA_HOME/bin/start-options.sh" "$MA_DATA/"

# generate a default self signed SSL/TLS certificate
keytool="$JAVA_HOME/bin/keytool"
if [ -z "$JAVA_HOME" ] || [ ! -x "$keytool" ]; then
	# use keytool from PATH
	keytool=keytool
fi
"$keytool" -genkey -noprompt -keyalg EC -alias "$MA_KEY_ALIAS" -dname "CN=$(hostname)" -keystore "$MA_KEYSTORE" \
-storetype PKCS12 -storepass "$MA_KEYSTORE_PASSWORD" -keypass "$MA_KEYSTORE_PASSWORD"

# ensure user read only permission
chmod 400 "$MA_KEYSTORE"

chown -R "$MA_USER":"$MA_GROUP" "$MA_HOME"
chown -R "$MA_USER":"$MA_GROUP" "$MA_DATA"

# Stop and remove any existing mango service
if [ -x "$(command -v systemctl)" ] && [ -d /etc/systemd/system ]; then
  # create a systemd override file for the service
  [ ! -d /etc/systemd/system/"$MA_SERVICE_NAME".service.d ] && mkdir /etc/systemd/system/"$MA_SERVICE_NAME".service.d
  echo "[Service]
Environment=\"mango_config=$MA_ENV_PROPERTIES\"
WorkingDirectory=$MA_HOME
PIDFile=$MA_DATA/ma.pid
ExecStart=
ExecStart=$MA_HOME/bin/start-mango.sh
User=$MA_USER
" > /etc/systemd/system/"$MA_SERVICE_NAME".service.d/override.conf

	# enable the systemd service (but dont start Mango)
	systemctl enable "$MA_HOME"/bin/mango.service
	echo "Mango was installed successfully. Type 'systemctl start $MA_SERVICE_NAME' to start Mango."
else
	echo "Mango was installed successfully. Type 'sudo -u $MA_USER $MA_HOME/bin/start-mango.sh' to start Mango. (systemd is not available and Mango will not start on boot)"
fi
