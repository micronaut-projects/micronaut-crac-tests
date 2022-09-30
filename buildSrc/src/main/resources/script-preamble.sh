#!/usr/bin/env bash
set -e
FAILED_PROJECTS=()
EXIT_STATUS=0
DELAY=30

execute() {
  # We only want to wait  seconds for success
  local end=$((SECONDS+DELAY))
  while ! curl -s http://localhost:8080/hello/tim; do
    if [ $SECONDS -gt $end ]; then
      echo "No response from the app in $DELAY seconds"
      return 1
    fi
    sleep 0.001;
  done
}

foo=$(TIMEFORMAT="%U"; time sleep 2)

time_to_first_request() {
  CONTAINER=$(docker run -d -p 8080:8080 --privileged $1)
  echo ""
  echo "======================================="
  echo "TIME TO FIRST SUCCESSFUL REQUEST FOR $1"
  time execute || EXIT_STATUS=$?
  echo "======================================="
  echo ""
  docker kill $CONTAINER
}

build_gradle_docker() {
  ./gradlew dockerBuild || EXIT_STATUS=$?
}

build_gradle_docker_native() {
  ./gradlew dockerBuildNative || EXIT_STATUS=$?
}

build_gradle_docker_crac() {
  ./gradlew dockerBuildCrac || EXIT_STATUS=$?
}

gradle() {
  build_gradle_docker
  docker tag micronautguide:latest micronautguide-standard:latest
  build_gradle_docker_native
  docker tag micronautguide:latest micronautguide-native:latest
  build_gradle_docker_crac
  time_to_first_request micronautguide-standard:latest
  time_to_first_request micronautguide-native:latest
  time_to_first_request micronautguide:latest
  time_to_first_request micronautguide-standard:latest
  time_to_first_request micronautguide-native:latest
  time_to_first_request micronautguide:latest
}
