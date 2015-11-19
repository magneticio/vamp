#!/usr/bin/env bash

work_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ui

target="src/main/resources/vamp-ui"

if [ -n "$1" ]; then
  target=$1
fi

cd ${work_dir}

reset=`tput sgr0`
green=`tput setaf 2`

echo "${green}Starting Vamp UI build - target directory: ${work_dir}/${target}${reset}"

echo "${green}Removing files from previous build...${reset}"
rm -Rf ${work_dir}/build node_modules

echo "${green}Running npm install...${reset}"
npm install

echo "${green}Running gulp dist...${reset}"
gulp dist

echo "${green}Moving files to resource directory...${reset}"
rm -Rf ${work_dir}/${target} && mkdir -p `dirname ${work_dir}/${target}` && mv ${work_dir}/build ${work_dir}/${target}

echo "${green}Vamp UI build: done.${reset}"
