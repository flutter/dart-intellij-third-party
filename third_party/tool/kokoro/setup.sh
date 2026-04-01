#!/bin/bash

setup() {
  # Fail on any error.
  set -e

  java --version

  # Enable verbose output for Gradle
  export JAVA_OPTS=" -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true"

  ./gradlew --version
}
