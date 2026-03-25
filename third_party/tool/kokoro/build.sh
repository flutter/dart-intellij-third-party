#!/bin/bash

source ./third_party/tool/kokoro/setup.sh
setup

echo "kokoro build start"

cd third_party
./gradlew buildPlugin

echo "kokoro build finished"
