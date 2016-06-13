#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ ! -f $DIR/target/github-helper.jar ]; then
    echo "Jar not found ... building"
    mvn clean package
fi

java -jar $DIR/target/github-helper.jar