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
  echo "AAAAAA"
fi


./startDiSLServer.sh

sleep 2

echo "> server started"
echo "running ex $1"
./runInstrumented.sh $1
