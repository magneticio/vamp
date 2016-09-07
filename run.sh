#!/usr/bin/env bash

set -e

dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

reset=`tput sgr0`
red=`tput setaf 1`
green=`tput setaf 2`
yellow=`tput setaf 3`

jar=$(find "${dir}/bootstrap/target/scala-2.11" -name 'vamp-assembly-*.jar' | sort | tail -1)

for key in "$@"
do
case ${key} in
    -h|--help)
    echo
    echo "${green}Usage: $0 [options] ${reset}"
    echo
    echo "${yellow}  -h|--help   ${green}show help message end exit${reset}"
    echo "${yellow}  -c|--clean  ${green}rebuild Vamp binary before run${reset}"
    echo
    exit 0
    ;;
    -c|--clean)
    rm ${jar} 2> /dev/null
    jar=""
    ;;
    *)
    ;;
esac
done

if [ -z "${jar}" ]; then
    echo "${green}Building Vamp binary...${reset}"
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
