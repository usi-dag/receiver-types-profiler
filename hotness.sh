#!/bin/bash


function quit(){
	echo "Usage: $0 <dacapo or ren>"
	exit 0
}


if [ "$#" -ne 1 ]; then
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
        # benchmarks=(scrabble page-rank future-genetic akka-uct movie-lens scala-doku chi-square fj-kmeans rx-scrabble db-shootout neo4j-analytics finagle-http reactors dec-tree scala-stm-bench7 naive-bayes als par-mnemonics scala-kmeans philosophers log-regression gauss-mix mnemonics dotty finagle-chirper)
        benchmarks=(rx-scrabble)
        ;;

    *)
    quit ;;
esac

if [ ! -d hotness/ ]; then
  mkdir hotness/
fi

if [ ! -d experiment/ ]; then
  mkdir experiment/
fi

for entry in "${benchmarks[@]}"; do

  if [ ! -d experiment/$entry ]; then
    mkdir experiment/$entry
  fi

  $ORACLE/collect -d experiment/$entry $JAVA_HOME/bin/java -jar renaissance-gpl-0.16.0.jar $entry $FLAGS  
  $ORACLE/er_print -metrics e.+cycles -viewmode user  -functions experiment/$entry/test.1.er/ > $entry.txt
  mv $entry.txt hotness/

done

python3 src-analysis/hot_methods.py --input-folder=hotness --output-folder=hotness
