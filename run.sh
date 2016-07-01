#!/usr/bin/env bash

dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

reset=`tput sgr0`
red=`tput setaf 1`
green=`tput setaf 2`

jar=$(find "${dir}/bootstrap/target/scala-2.11" -name 'vamp-assembly-*.jar' | sort | tail -1)

if [ -z "${jar}" ]; then
    echo "${green}Vamp binary not found, building Vamp...${reset}"
    sbt test assembly
    response_code=$?
    if [[ ${response_code} != 0 ]]; then
      exit ${response_code};
    fi
    jar=$(find "${dir}/bootstrap/target/scala-2.11" -name 'vamp-assembly-*.jar' | sort | tail -1)
fi

app_config=${dir}/conf/application.conf
log_config=${dir}/conf/logback.xml

if [ ! -e "${app_config}" ] ; then
    echo "${red}No app config: ${app_config}${reset}"
    exit 1
fi

echo "${green}App config  : ${app_config}${reset}"

if [ ! -e "${log_config}" ] ; then
    echo "${red}No log config: ${log_config}${reset}"
    exit 1
fi

echo "${green}Log config  : ${log_config}${reset}"

if [ ! -e "${jar}" ] ; then
    echo "${red}No Vamp binary: ${jar}${reset}"
    exit 1
fi

echo "${green}Vamp binary : ${jar}${reset}"

java -Dlogback.configurationFile=${log_config} \
     -Dconfig.file=${app_config} \
     -jar ${jar}
