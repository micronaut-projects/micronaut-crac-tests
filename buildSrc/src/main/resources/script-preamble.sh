#!/usr/bin/env bash
set -e
FAILED_PROJECTS=()
EXIT_STATUS=0

profile() {
  local JAR=$1
  echo "Vanilla test"
  local PROCESS=$(utils/start-bg.sh \
      -s "Startup completed" \
      java -jar $JAR)
  curl localhost:8080/hello/test | grep "Hello test!"
  utils/bench.sh http://localhost:8080/hello/test
  kill $PROCESS

  echo "Prepare checkpoint"
  PROCESS=$(utils/start-bg.sh \
      -s "Startup completed" \
      -e exitcode \
      ${{ env.JDK }}/bin/java \
      -XX:CRaCCheckpointTo=cr \
      -XX:+UnlockDiagnosticVMOptions \
      -XX:+CRTraceStartupTime \
      -Djdk.crac.trace-startup-time=true \
      -jar $JAR)

  echo "Warmup"
  utils/bench.sh http://localhost:8080/hello/test
  jcmd $PROCESS JDK.checkpoint
  [ 137 = $(utils/read-exitcode.sh exitcode) ]

  echo "Test restore"
  PROCESS=$(utils/start-bg.sh \
      -s "restore-finish" \
      ${{ env.JDK }}/bin/java -XX:CRaCRestoreFrom=cr)
  curl localhost:8080/hello/test | grep "Hello test!"
  utils/bench.sh http://localhost:8080/hello/test
  kill $PROCESS

  echo "Check startup time"
  timeout 3 bash -c "utils/javatime ; ${{ env.JDK }}/bin/java -XX:CRaCRestoreFrom=cr" | utils/sel.awk -v from=prestart -v to=restore-finish
}
