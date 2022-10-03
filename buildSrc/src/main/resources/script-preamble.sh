#!/usr/bin/env bash
set -e
FAILED_PROJECTS=()
EXIT_STATUS=0
DELAY=30

echo "=== Utils located at '$UTILS'"
echo "=== CRaC JDK located at '$JDK'"

read_exit_code() {
  local exitcode=$1
  local retries=60 # 30 seconds of 0.5 second sleeps
  e=$(head $exitcode 2>/dev/null)
  while [ ! $e ] && [ $retries -gt 0 ]; do
    sleep 0.5
    e=$(head $exitcode 2>/dev/null)
    retries=$((retries-1))
  done
  rm $exitcode
  if [ $retries -le 0 ]; then
    echo "Timeout waiting for $1"
    return 1
  else
    echo $e
  fi
}

test_checkpoint() {
  local JAR=$1
  echo "Vanilla test"
  local PROCESS=$($UTILS/start-bg.sh \
      -s "Startup completed" \
      java -jar $JAR)

  EXPECTED_RESPONSE='Hello test!'
  RESPONSE=$(curl -s localhost:8080/hello/test)
  if [ "$RESPONSE" != "$EXPECTED_RESPONSE" ]; then echo $RESPONSE && exit 1; fi
  kill $PROCESS

  echo "Prepare checkpoint"
  PROCESS=$($UTILS/start-bg.sh \
      -s "Startup completed" \
      -e exitcode \
      $JDK/bin/java \
      -XX:CRaCCheckpointTo=cr \
      -XX:+UnlockDiagnosticVMOptions \
      -XX:+CRTraceStartupTime \
      -Djdk.crac.trace-startup-time=true \
      -jar $JAR)
  echo "Make checkpoint"
  jcmd $PROCESS JDK.checkpoint
  local foundExitCode="$(readexitcode exitcode)"
  if [ "137" != "$foundExitCode" ]; then
    echo "ERROR: Expected exit code 137, got $foundExitCode"
    kill $PROCESS
    return 1
  else
    echo "Test restore"
    PROCESS=$($UTILS/start-bg.sh \
        -s "restore-finish" \
        $JDK/bin/java -XX:CRaCRestoreFrom=cr)
    RESPONSE=$(curl -s localhost:8080/hello/test)
    if [ "$RESPONSE" != "$EXPECTED_RESPONSE" ]; then echo $RESPONSE && exit 1; fi
    kill $PROCESS
    echo "Remove Checkpoint"
    rm -rf cr
  fi
}

execute() {
  # We only want to wait  seconds for success
  local end=$((SECONDS+DELAY))
  while ! curl -o /dev/null -s http://localhost:8080/hello/tim; do
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
  result=$(mytime execute)
  docker kill $CONTAINER > /dev/null
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

assemble_gradle() {
  ./gradlew assemble
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
  echo "| Build type | Time to First Request (secs) |" >> $GITHUB_STEP_SUMMARY
  echo "| --- | --- |" >> $GITHUB_STEP_SUMMARY
  echo "| Standard Docker | $standard |" >> $GITHUB_STEP_SUMMARY
  echo "| Native Docker | $native |" >> $GITHUB_STEP_SUMMARY
  echo "| CRaC Docker | $crac |" >> $GITHUB_STEP_SUMMARY
  echo "" >> $GITHUB_STEP_SUMMARY

  assemble_gradle
  test_checkpoint 'build/libs/micronautguide-0.1-all.jar' || EXIT_STATUS=$?
  echo "test_checkpoint exit code: \$EXIT_STATUS" >> $GITHUB_STEP_SUMMARY
}
