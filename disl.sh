#!/bin/sh

if [ "$#" -ne 1 ]; then
    echo "Usage: $0"
    exit;
fi

ant clean
echo "> CLEANED"

ant -Ddislclass=profiler.Instrumentation

processid=`jps | grep DiSLServer | cut -d " " -f1`

if [ -n "$processid" ]; then
  kill -9 "$processid"
fi


./startDiSLServer.sh

sleep 2

echo "> server started"
echo "running ex $1"
AGENT_FLAGS="$AGENT_FLAGS --patch-module java.base=lib/disl-bypass.jar --add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED"
ARCH=`uname -p`
AGENT_EXT=.so

if [ -n "$GRAAL" ]; then
 echo "Using GraalVM"
 GRAAL_FLAGS="-server -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI --add-exports=java.base/jdk.internal.misc=jdk.graal.compiler -Djdk.graal.CompilationFailureAction=Diagnose -Djdk.graal.DumpOnError=true -Djdk.graal.ShowDumpFiles=true -Djdk.graal.PrintGraph=Network -Djdk.graal.ObjdumpExecutables=objdump,gobjdump -Dgraalvm.locatorDisabled=true"
fi


# ./runInstrumented.sh $1
 $JAVA_HOME/bin/java $GRAAL_FLAGS \
  -agentpath:lib/$ARCH/libdislagent$AGENT_EXT \
  --patch-module java.base=lib/disl-bypass.jar \
  --add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED \
  -Xbootclasspath/a:lib/disl-bypass.jar:build/profiler.jar \
   -cp build/app.jar -noverify -Xms5g -Xmx5g \
   -XX:+UnlockDiagnosticVMOptions  -XX:+LogCompilation -XX:LogFile=compiler_log.xml \
   Main
