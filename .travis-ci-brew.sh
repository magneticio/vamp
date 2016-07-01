#!/usr/bin/env bash

# Detect if we should use JAVA_HOME or just try PATH.
get_java_cmd() {
  if [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
    echo "$JAVA_HOME/bin/java"
  else
    echo "java"
  fi
}

declare java_cmd=$(get_java_cmd)

vamp_java_version_check() {
    readonly vamp_java_version=$("$java_cmd" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    if [[ "$vamp_java_version" == "" ]]; then
        echo
        echo Hi, it appears I cannot find your Java installation. I really need Java 1.8+ to be installed.
        echo Please check http://vamp.io/installation for details
        echo
        exit 1
    elif [[ ! "$vamp_java_version" > "1.8" ]]; then
        echo
        echo Hi, you seem to have Java version $vamp_java_version installed. I need Java version 1.8 or higher.
        echo Please check http://vamp.io/installation for details
        echo
        exit 1
    fi
}

vamp_java_version_check

# Runs the Vamp CLI
java -jar ##PREFIX##/vamp-cli.jar "$@"