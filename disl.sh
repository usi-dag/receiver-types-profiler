#!/bin/sh

if ["$#" -ne 1 ]; then
    echo "Usage: $0"
    exit;
fi

ant clean
echo "> CLEANED"

ant -Ddislclass=Instrumentation
./startDiSLServer.sh

echo "> server started"
echo "running ex $1"
./runInstrumented.sh $1
