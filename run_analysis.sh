#!/bin/bash

function quit(){
	echo "Usage: $0 <dacapo or ren> <number of iterations per benchmark>"
	exit 0
}


if [ "$#" -ne 2 ]; then
	quit
fi


case "$1" in
    "dacapo")
        FLAGS="-n 1"
    		BENCH=dacapo-23.11-MR2-chopin.jar 
        # benchmarks=(avrora batik biojava cassandra eclipse fop graphchi h2 h2o jme jython kafka luindex lusearch pmd spring sunflow tomcat tradebeans tradesoap xalan zxing)
        benchmarks=(jme)
        ;;
    "ren")
    		FLAGS="-r 1"
        BENCH=renaissance-gpl-0.16.0.jar 
        benchmarks=(scrabble page-rank future-genetic akka-uct movie-lens scala-doku chi-square fj-kmeans rx-scrabble db-shootout neo4j-analytics finagle-http reactors dec-tree scala-stm-bench7 naive-bayes als par-mnemonics scala-kmeans philosophers log-regression gauss-mix mnemonics dotty finagle-chirper)
        ;;

    *)
    quit ;;
esac


ant -Ddislclass=profiler.Instrumentation -buildfile build.xml

for entry in "${benchmarks[@]}"; do

  for i in $(seq $2); do 
    
    processid=`$JAVA_HOME/bin/jps | grep DiSLServer | cut -d " " -f1`

    if [ -n "$processid" ]; then
      kill -9 "$processid"
      echo "Old DiSLServer killed"
    fi

    ./startDiSLServer.sh

    sleep 2

    ARCH=`uname -p`


    AGENT_PATH=lib/$ARCH/libdislagent.so
    DISL_BYPASS=lib/disl-bypass.jar
    PROFILER=build/profiler.jar

    $JAVA_HOME/bin/java -agentpath:$AGENT_PATH --patch-module java.base=$DISL_BYPASS \
    -Djava.security.manager=allow \
    --add-exports java.base/ch.usi.dag.disl.dynamicbypass=ALL-UNNAMED \
    -Xbootclasspath/a:$DISL_BYPASS:$PROFILER -noverify -cp $PROFILER \
    -Xmx6G -Xms6G \
    -jar $BENCH $entry $FLAGS

    # exit 0
    # For some reason this sleep fixes a bug. I have no idea why nor how.
    # This is some of the jankiest fix ever made and it brings shame upon my family.
    sleep 30

    $JAVA_HOME/bin/java -Xmx10G -classpath analysis/target/classes/ com.msde.app.App -i output/

    ARCHIVENAME="$1"_"$entry"_"$i".tar.gz
    echo $ARCHIVENAME
    tar --use-compress-program="pigz -k" -cf $ARCHIVENAME result

    # ARCHIVEDIR=/mnt/hdd/archives/
    ARCHIVEDIR=archives/

    if [ ! -d $ARCHIVEDIR ]; then
      mkdir $ARCHIVEDIR
    fi

    mv $ARCHIVENAME $ARCHIVEDIR
    # rm output/*
    # rm result/*
  done
done
