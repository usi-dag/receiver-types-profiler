#!/bin/bash

function quit(){
	echo "Usage: $0 <server or local> <dacapo or ren> <number of iterations per benchmark>"
	exit 0
}


if [ "$#" -ne 3 ]; then
	quit
fi


MODE=$1
SUITE=$2
ITERATION=$3

case "$SUITE" in
    "dacapo")
        FLAGS="-n 1"
    		BENCH=dacapo-23.11-MR2-chopin.jar 
        # benchmarks=(avrora batik biojava cassandra eclipse fop graphchi h2 h2o jme jython kafka luindex lusearch pmd spring sunflow tomcat tradebeans tradesoap xalan zxing)
        benchmarks=(jme)
        ;;
    "ren")
    		FLAGS="-r 1"
        BENCH=renaissance-gpl-0.16.0.jar 
        # benchmarks=(scrabble page-rank future-genetic akka-uct movie-lens scala-doku chi-square fj-kmeans rx-scrabble db-shootout neo4j-analytics finagle-http reactors dec-tree scala-stm-bench7 naive-bayes als par-mnemonics scala-kmeans philosophers log-regression gauss-mix mnemonics dotty finagle-chirper)
        benchmarks=(rx-scrabble)
        ;;

    *)
    quit ;;
esac

case "$MODE" in
    "server")
        DISLHEAP=60G
        ANALYSISHEAP=200G
        ;;
    "local")
        DISLHEAP=10G
        ANALYSISHEAP=10G
        ;;

    *)
    quit ;;
esac


ant -Ddislclass=profiler.Instrumentation -buildfile build.xml

if [ ! -d result/ ]; then
  mkdir result/
fi


ARCH=`uname -p`


AGENT_PATH=lib/$ARCH/libdislagent.so
DISL_BYPASS=lib/disl-bypass.jar
PROFILER=build/profiler.jar
# TIER4=20000

for entry in "${benchmarks[@]}"; do

  for i in $(seq $ITERATION); do 
    
    processid=`$JAVA_HOME/bin/jps | grep DiSLServer | cut -d " " -f1`

    if [ -n "$processid" ]; then
      kill -9 "$processid"
      echo "Old DiSLServer killed"
    fi

    ./startDiSLServer.sh

    sleep 2

    LOG_FILE=result/compiler_log_"$SUITE"_"$entry"_"$i".xml

    $JAVA_HOME/bin/java -agentpath:$AGENT_PATH --patch-module java.base=$DISL_BYPASS \
    -Djava.security.manager=allow \
    --add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED \
    -Xbootclasspath/a:$DISL_BYPASS:$PROFILER -noverify -cp $PROFILER \
    -Xmx$DISLHEAP -Xms$DISLHEAP \
    -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation -XX:LogFile=$LOG_FILE \
    -XX:CompilationMode=high-only \
    -jar $BENCH $entry $FLAGS

    # -XX:Tier4InvocationThreshold=$TIER4 \
    # 
    # exit 0
    # For some reason this sleep fixes a bug. I have no idea why nor how.
    # This is some of the jankiest fix ever made and it brings shame upon my family.
    sleep 10

    $JAVA_HOME/bin/java -Xmx$ANALYSISHEAP -classpath src-digest/target/classes/ com.msde.app.App -i output/ -c $LOG_FILE -d 1000 

    if [ $? -ne 0 ]; then
      echo Something went wrong analyzing $SUITE $entry iteration $i
      rm output/*
      rm result/*
      continue
    fi

    rm result/callsite_*.txt

    cp output/* result/

    if [ $MODE = "local" ]; then
      continue
    fi

    ARCHIVENAME="$SUITE"_"$entry"_"$i".tar.gz
    echo $ARCHIVENAME
    tar --use-compress-program="gzip --fast" -cf $ARCHIVENAME result

    # ARCHIVEDIR=/mnt/hdd/archives/
    ARCHIVEDIR=archives/

    if [ ! -d $ARCHIVEDIR ]; then
      mkdir $ARCHIVEDIR
    fi

    mv $ARCHIVENAME $ARCHIVEDIR
    rm output/*
    rm result/*
  done
done
