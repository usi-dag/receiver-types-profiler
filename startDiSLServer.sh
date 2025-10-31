#!/bin/sh

DEBUG=false
while :; do
    case $1 in
        -d|--debug) DEBUG=true            
        ;;
        *) break
    esac
    shift
done

if [ "$DEBUG" = true ]; then
 $JAVA_HOME/bin/java -agentpath:lib/libnativeagent.so -Ddisl.exclusionList="exclusion.lst" -Ddebug=true -Ddislserver.instrumented=disl_instrumented/ -cp build/profiler.jar:lib/disl-server.jar ch.usi.dag.dislserver.DiSLServer &
else
 $JAVA_HOME/bin/java -agentpath:lib/libnativeagent.so -Ddisl.exclusionList="exclusion.lst" -cp build/profiler.jar:lib/disl-server.jar ch.usi.dag.dislserver.DiSLServer &
fi

