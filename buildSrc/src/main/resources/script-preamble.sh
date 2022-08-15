#!/usr/bin/env bash
set -e
FAILED_PROJECTS=()
EXIT_STATUS=0

echo "=== Utils located at '$UTILS'"
echo "=== CRaC JDK located at '$JDK'"
profile() {
  local JAR=$1
  echo "Vanilla test"
  local PROCESS=$($UTILS/start-bg.sh \
      -s "Startup completed" \
      java -jar $JAR)
  curl localhost:8080/hello/test | grep "Hello test!"
  $UTILS/bench.sh http://localhost:8080/hello/test
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

  echo "Warmup"
  $UTILS/bench.sh http://localhost:8080/hello/test
  jcmd $PROCESS JDK.checkpoint
  [ 137 = $($UTILS/read-exitcode.sh exitcode) ]

  echo "Test restore"
  PROCESS=$($UTILS/start-bg.sh \
      -s "restore-finish" \
      $JDK/bin/java -XX:CRaCRestoreFrom=cr)
  curl localhost:8080/hello/test | grep "Hello test!"
  $UTILS/bench.sh http://localhost:8080/hello/test
  kill $PROCESS

  echo "Check startup time"
  timeout 3 bash -c "$UTILS/javatime ; $JDK/bin/java -XX:CRaCRestoreFrom=cr" | $UTILS/sel.awk -v from=prestart -v to=restore-finish
  return
}
