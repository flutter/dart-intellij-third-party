#!/bin/bash

source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"

cd third_party
./gradlew buildPlugin

echo "kokoro build finished"
