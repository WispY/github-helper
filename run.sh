#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

pushd $DIR
if [ ! -f ./target/github-helper.jar ]; then
    echo "Jar not found ... building"
    mvn clean package
fi

java -jar ./target/github-helper.jar
popd