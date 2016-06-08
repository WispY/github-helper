#!/usr/bin/env bash


if [ ! -f ./target/github-helper.jar ]; then
    echo "Jar not found ... building"
    mvn clean package
fi

java -jar ./target/github-helper.jar