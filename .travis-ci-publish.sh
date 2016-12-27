#!/usr/bin/env bash

set -e

# Check bintray credentials
: ${BINTRAY_USER:?"No BINTRAY_USER set"}
: ${BINTRAY_API_KEY:?"No BINTRAY_API_KEY set"}

if [ -z "${TRAVIS_BUILD_DIR}" ]; then
  TRAVIS_BUILD_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
fi

target=${TRAVIS_BUILD_DIR}/target
version="$( git describe --tags )"

vamp_jar=vamp-${version}.jar
vamp_cli_jar=vamp-cli.jar
vamp_cli_zip=vamp-cli-${version}.zip

sbt assembly

cp $(find "${TRAVIS_BUILD_DIR}/bootstrap/target/scala-2.12" -name 'vamp-assembly-*.jar' | sort | tail -1) ${target}/${vamp_jar}

mkdir -p ${target}/brew/brew
cp $(find "${TRAVIS_BUILD_DIR}/cli/target/scala-2.12" -name 'vamp-cli-*.jar' | sort | tail -1) ${target}/brew/${vamp_cli_jar}
cp ${TRAVIS_BUILD_DIR}/.travis-ci-brew.sh ${target}/brew/brew/vamp
cd ${target}/brew
zip -r ${vamp_cli_zip} *
cd ${TRAVIS_BUILD_DIR}

echo "Bintray publish"

curl -v -T ${target}/${vamp_jar} \
   -u${BINTRAY_USER}:${BINTRAY_API_KEY} \
   -H "X-Bintray-Package:vamp" \
   -H "X-Bintray-Version:${version}" \
   -H "X-Bintray-Publish:1" \
   https://api.bintray.com/content/magnetic-io/downloads/vamp/${vamp_jar}

curl -v -T ${target}/brew/${vamp_cli_zip} \
   -u${BINTRAY_USER}:${BINTRAY_API_KEY} \
   -H "X-Bintray-Package:vamp-cli" \
   -H "X-Bintray-Version:${version}" \
   -H "X-Bintray-Publish:1" \
   https://api.bintray.com/content/magnetic-io/downloads/vamp-cli/${vamp_cli_zip}

echo "sbt publish"

mkdir $HOME/.bintray/

FILE=$HOME/.bintray/.credentials

cat <<EOF >$FILE
realm = Bintray API Realm
host = api.bintray.com
user = $BINTRAY_USER
password = $BINTRAY_API_KEY
EOF

echo "Created ~/.bintray/.credentials file: "
ls -la $FILE

sbt publish

rm -Rf $HOME/.bintray
