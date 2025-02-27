function  quit(){
	echo "Usage: $0 <dacapo or ren> <benchmark name>"
	exit 0
}


if [ "$#" -ne 2 ]; then
	quit
fi

case "$1" in
    "dacapo")
    		BENCH=dacapo-23.11-MR2-chopin.jar ;;
    "ren")
    		FLAGS="-r 1"
        BENCH=renaissance-gpl-0.16.0.jar ;;
    *)
    quit ;;
esac

ant -Ddislclass=profiler.Instrumentation -buildfile build.xml


processid=`jps | grep DiSLServer | cut -d " " -f1`

if [ -n "$processid" ]; then
  kill -9 "$processid"
  echo "Old DiSLServer killed"
fi

./startDiSLServer.sh

sleep 2

AGENT_PATH=lib/aarch64/libdislagent.so
DISL_BYPASS=lib/disl-bypass.jar
PROFILER=build/profiler.jar

java -agentpath:$AGENT_PATH --patch-module java.base=$DISL_BYPASS \
-Djava.security.manager=allow \
--add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED \
-Xbootclasspath/a:$DISL_BYPASS:$PROFILER -noverify -cp $PROFILER \
-Xmx6G -Xms6G \
-jar $BENCH $2 $FLAGS

