#!/usr/bin/env bash

function publish {
  # Check bintray credentials
  : ${BINTRAY_USER:?"No BINTRAY_USER set"}
  : ${BINTRAY_API_KEY:?"No BINTRAY_API_KEY set"}

  PACKAGE=$1
  DISTRIBUTABLE=$2
  SOURCEPATH=$3
  VERSION=$4

  : ${PACKAGE:?"Not set"}
  : ${DISTRIBUTABLE:?"Not set"}
  : ${SOURCEPATH:?"Not set"}
  : ${VERSION:?"Not set"}

  curl -v -T $SOURCEPATH/${DISTRIBUTABLE} \
   -u${BINTRAY_USER}:${BINTRAY_API_KEY} \
   -H "X-Bintray-Package:${PACKAGE}" \
   -H "X-Bintray-Version:${VERSION}" \
   -H "X-Bintray-Publish:1" \
   https://api.bintray.com/content/magnetic-io/downloads/${PACKAGE}/${DISTRIBUTABLE}
}

version="$( git describe --tags)"

vamp_jar=vamp-${version}.jar
vamp_cli_jar=vamp-cli.jar
vamp_cli_zip=vamp-cli-${version}.zip

sbt assembly

cp $(find "${TRAVIS_BUILD_DIR}/bootstrap/target/scala-2.11" -name 'vamp-assembly-*.jar' | sort | tail -1) ${TRAVIS_BUILD_DIR}/${vamp_jar}

mkdir -p ${TRAVIS_BUILD_DIR}/brew/brew
cp $(find "${TRAVIS_BUILD_DIR}/cli/target/scala-2.11" -name 'vamp-cli-*.jar' | sort | tail -1) ${TRAVIS_BUILD_DIR}/brew/${vamp_cli_jar}
cp ${TRAVIS_BUILD_DIR}/.travis-ci-brew.sh ${target}/brew/brew/vamp
cd ${TRAVIS_BUILD_DIR}/brew
zip -r ${vamp_cli_zip} *
cd ${TRAVIS_BUILD_DIR}

publish vamp ${vamp_jar} ${TRAVIS_BUILD_DIR} ${version}

publish vamp-cli ${vamp_cli_zip} ${TRAVIS_BUILD_DIR}/brew ${version}

sbt publish
