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

# From http://mywiki.wooledge.org/BashFAQ/032 capture the elapsed time in seconds
mytime() {
  exec 3>&1 4>&2
  mytime=$(TIMEFORMAT="%R"; { time $1 1>&3 2>&4; } 2>&1)
  exec 3>&- 4>&-
  echo $mytime
}

time_to_first_request_docker() {
  CONTAINER=$(docker run -d -p 8080:8080 --privileged $1)
  echo ""
  echo "======================================="
  echo "TIME TO FIRST SUCCESSFUL REQUEST FOR $1"
  result=$(mytime execute)
  echo "======================================="
  echo ""
  docker kill $CONTAINER
  echo $result
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
  # Build regular app in docker, and rename image to micronautguide-standard
  build_gradle_docker
  docker tag micronautguide:latest micronautguide-standard:latest

  # Build native app in docker, and rename image to micronautguide-native
  build_gradle_docker_native
  docker tag micronautguide:latest micronautguide-native:latest

  # Build crac app in docker
  build_gradle_docker_crac

  standard=$(time_to_first_request_docker micronautguide-standard:latest)
  native=$(time_to_first_request_docker micronautguide-native:latest)
  crac=$(time_to_first_request_docker micronautguide:latest)

  echo "### Summary" >> $GITHUB_STEP_SUMMARY
  echo "" >> $GITHUB_STEP_SUMMARY
  echo "| Build type | TTFR |" >> $GITHUB_STEP_SUMMARY
  echo "| --- | --- |" >> $GITHUB_STEP_SUMMARY
  echo "| Standard | $standard |" >> $GITHUB_STEP_SUMMARY
  echo "| Native | $native |" >> $GITHUB_STEP_SUMMARY
  echo "| CRaC | $crac |" >> $GITHUB_STEP_SUMMARY
}
