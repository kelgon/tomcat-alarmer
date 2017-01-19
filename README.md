#tomcat-alamer

中文说明点此：[README_zhCN.md](/README_zhCN.md)

##Introduction
monitors Apache Tomcat instances and generates alarms under certain conditions:

- too many pending requests/request queue size exceeded threshold
- health check request failed/timeout/got invalid status code(other than 200)/got invalid response entity
- certain threads (with same thread name prefix) busy rate exceeded threshold
- certain threads (with same thread name prefix) been blocked over threshold milliseconds
- last fullGC cost too much time (more than threshold)
- potential memory leak (old gen usage too high after last fullGC)
- heap/non-heap memory usage too high

also does something else:

- dumping JVM threads under certain conditions, for analyze purpose. Will dump 3 threadDumps in a row, and will keep an minimum dump interval.

##Usage
###Prerequistie
- Apache Tomcat 6.0 or higher
- Tomcat instances must open JMX ports, here is a sample configuration in catalina.sh:

	CATALINA_OPTS="$CATALINA_OPTS
	-Dcom.sun.management.jmxremote
	-Dcom.sun.management.jmxremote.port=8901
	-Dcom.sun.management.jmxremote.ssl=false
	-Dcom.sun.management.jmxremote.authenticate=false"
- To monitor request queue, you must use Executor in your Tomcat Connector, here is a sample configuration in server.xml:

	<Executor name="tomcatThreadPool" namePrefix="catalina-exec-" maxThreads="200" minSpareThreads="50" maxQueueSize="3000" />
	<Connector port="8080" protocol="org.apache.coyote.http11.Http11NioProtocol" executor="tomcatThreadPool" maxConnections="2000"/>
 
note that the **maxConnections** must larger than **maxThreads**, otherwise there will be no request goes into the request queue of the Executor.
Also, do not set **maxConnections** larger than **maxQueueSize**, otherwise some requests will be accepted by Connector and instantly rejected by Executor (because there are no rooms in the request queue)

###Install
just unzip

###Run
	bin/start.sh

###Shutdown
	ps -ef|grep tomcat.Monitor
	kill <pid>

note that do not use kill -9 to shutdown tomcat-alarmer. If there are ongoing JMX connections while kill -9 is triggered, may result in some dead JMX threads in Apache Tomcat.

###Configuration
Most configuration is under monitorJob.properties:

- **RequestQueue.executorName**: the name of the Tomcat Executor, in server.xml
- **RequestQueue.alertOnQueueSize**: the threshold of the request queue size. If current queue size is larger than this, will trigger an alarm
- **RequestQueue.dumpThreadsOnAlert**: whether to dump threads while RequestQueue alarm is triggered. true/false
- **HealthCheck.url**: the health check URL, relative
- **HealthCheck.connectTimeout**: connect timeout of the healthcheck request
- **HealthCheck.readTimeout**: read timeout of the healthcheck request
- **HealthCheck.expectation**: the response entity that indicates a healthy response
- **Thread.prefix**: the prefix of the name of the thread needs monitoring. separated by commas
- **Thread.{prefix}.idleStatus**: which state (WAITING/TIMED_WAITING) indicates an idle thread of this prefix.
- **Thread.alertOnBusyRate**: the threshold of the busy rate. If current busy rate is higher than this, will trigger an alarm
- **Thread.alertOnBlockTime**: the threshold of the blocked time (milliseconds). If a thread has been blocked more than this, will trigger an alarm
- **Thread.dumpThreadsOnAlert**: whether to dump threads while Thread alarm is triggered. true/false
- **FullGC.alertOnUsageAfterGC**: threshold of the old gen usage right after last fullGC. If the old gen usage right after last fullGC is higher than this, alarm will be triggered.
- **FullGC.alertOnCostTime**: threshold of the fullGC time cost (milliseconds). If last fullGC costs more than this, alarm will be triggered
- **Memory.alertOnHeapUsage**: threshold of the heap memory usage.
- **Memory.alertOnNonHeapUsage**: threshold of the non-heap memory usage.
- **RequestQueue.cron**: cron expression of the RequestQueue job
- **HealthCheck.cron**: cron expression of the HealthCheck job
- **Thread.cron**: cron expression of the Thread job
- **FullGC.cron**: cron expression of the FullGC job
- **Memory.cron**: cron expression of the Memory job

All configurations above can be under "global" context or under a specific tomcat instance. The latter (if exists) will override the former. 

- **lang**: language, current supporting enUS and zhCN
- **golbalReceiver**: where the alarms goes to. cellphone numbers, email addresses, etc. separated by commas
- **global.configurationReloadJob.cron**: cron expression of the configuration reload job
- **global.minThreadDumpInterval**: the minimum dump interval (seconds), only one dump job (consist of 3 threadDumps) will be launched during this period of time

- **targetServers**: name (or anything can tell one server from another) of the servers which Apache Tomcat are deployed on, separated by commas. e.g.: targetServers=server1,server2 
- **{server}.host**: hostname or ip address of this server. e.g.: server1.host=192.168.0.100
- **{server}.targetApps**: name (or anything can tell one instance from another) of the Apache Tomcat instances deployed on this server, separated by commas. e.g.: server1.targetApps=tomcat1,tomcat2
- **{server}.{app}.JMXPort**: JMX port of this Tomcat instance
- **{server}.{app}.port**: HTTP port of this Tomcat instance
- **{server}.{app}.monitorJobs**: the monitor jobs to run on this Tomcat instance, separated by commas. Supported jobs are: [RequestQueue, Thread, HealthCheck, FullGC, Memory]

You can add alarmReceiver to a certain kind of monitor job under a certain Tomcat instance, such as:

	server1.tomcat1.RequestQueue.receiver=13800138004

when this specific job triggers an alarm, it will be send to this receiver in addition to all the global receivers.

### Where did the alarms go?
All alarms triggered will be append to logs/alarm.log, under the format of "<alarmText>|<receivers separated by space>". You can have a process monitoring this file and send out the alarm. You can also implement the method

	public static boolean sendAlarm(String text, Set<String> receiver)

in class kelgon.monitor.jmx.tomcat.util.AlarmSender to send out alarms.

### Where can I find the thread dumps?
The thread dumps are in dumps/ directory (relative). Each dump file has a suffix indicates its dump time.