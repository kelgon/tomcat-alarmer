#!/bin/sh

jarpath=""

for _FNAME in lib/*.jar
  do
  jarpath=$jarpath:$_FNAME
done

CLASSPATH=$CLASSPATH:$jarpath
export CLASSPATH
echo "starting tomcatMonitor..."

java -Xmx64m -classpath $CLASSPATH kelgon.monitor.jmx.tomcat.Monitor &
