#tomcat-alamer

##功能介绍
tomcat-alarmer监控复数个Apache Tomcat实例，并在特定情况下发出告警

监控能力

- RequestQueue：监控HTTP请求积压情况，积压队列长度超阈值告警
- HealthCheck：对所有tomcat实例进行发送http请求进行健康检查，响应时长超阈值、响应状态码异常、响应内容异常时告警
- Thread：线程监控，指定前缀线程繁忙率超阈值告警、指定前缀线程阻塞时间超阈值告警
- FullGC：垃圾回收监控，Full GC耗时超阈值告警、Full GC完成后Old Gen内存使用率超阈值告警
- Memory：内存使用监控，堆区内存使用率超阈值告警、非堆取内存使用率超阈值告警

通用能力

- 支持在RequestQueue和Thread监控触发告警时自动保存对应tomcat实例的threadDump。每次间隔5秒连续dump三次，并支持配置threadDump的最小间隔时间，避免频繁触发threadDump造成性能浪费。
- 每个实例，每类告警的监控策略、阈值、接收告警手机号、任务周期等均可通过配置文件配置
- 配置热加载，绝大部分配置的修改无需重启监控程序


##使用说明
###环境要求
- Apache Tomcat 6.0 或以上版本
- Tomcat实例必须打开JMX端口，以下是catalina.sh中的配置样例：

```
CATALINA_OPTS="$CATALINA_OPTS
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=8901
-Dcom.sun.management.jmxremote.ssl=false
-Dcom.sun.management.jmxremote.authenticate=false"
```

- 要让RequestQueue监控发挥作用，必须在Tomcat中配置Connector使用Executor，以下是server.xml中的配置样例：

```
<Executor name="tomcatThreadPool" namePrefix="catalina-exec-" maxThreads="200" minSpareThreads="50" maxQueueSize="3000" />
<Connector port="8080" protocol="org.apache.coyote.http11.Http11NioProtocol" executor="tomcatThreadPool" maxConnections="2000"/>
```
 
注意，**maxConnections**的数值必须大于**maxThreads**的数值，否则积压的请求将不会进入Executor的等待队列。
同时，**maxConnections**的数值必须小于**maxQueueSize**，否则可能会出现请求被Connector受理，但无法加入Executor的队列，导致请求立即被拒绝。

###安装
解压即可

###启动
	bin/start.sh

###停止
	ps -ef|grep tomcat.Monitor
	kill <pid>

注意，勿使用kill -9来停止tomcat-alarmer，如果此时有正在进行中的JMX连接，可能会导致Tomcat实例中出现僵死的JMX线程

###配置
大部分的配置都在monitorJob.properties文件中：

- **RequestQueue.executorName**: Tomcat Executor的name，与server.xml中的配置保持一致
- **RequestQueue.alertOnQueueSize**: 请求积压数的阈值，超过此值会触发告警
- **RequestQueue.dumpThreadsOnAlert**: 当RequestQueue触发告警时，是否自动抓取threadDump。true/false
- **HealthCheck.url**: 健康检查的目标URL，相对URL
- **HealthCheck.connectTimeout**: 健康检查请求的连接超时时长
- **HealthCheck.readTimeout**: 健康检查请求的处理超时时长
- **HealthCheck.expectation**: 预期的响应实体内容
- **Thread.prefix**: 需要监控的线程前缀名，逗号分隔
- **Thread.{prefix}.idleStatus**: 指定前缀名的线程处于何种状态时(WAITING/TIMED_WAITING)代表该线程处于空闲
- **Thread.alertOnBusyRate**: 线程繁忙率阈值，当受监控的线程繁忙率大于等于此值时，触发告警
- **Thread.alertOnBlockTime**: 线程阻塞时长阈值(ms)，当某线程进入阻塞(BLOCKED)状态超过此时长时，触发告警
- **Thread.dumpThreadsOnAlert**: 当Thread任务触发告警时，是否自动抓取threadDump。true/false
- **FullGC.alertOnUsageAfterGC**: 上一次Full GC完成后，Old区内存的使用比阈值。当上一次Full GC完成时Old区内存使用比仍高于此数值，触发告警
- **FullGC.alertOnCostTime**: 上一次Full GC耗时阈值(ms)，当上一次Full GC消耗时间大于此数值时，触发告警
- **Memory.alertOnHeapUsage**: 堆区内存使用比阈值
- **Memory.alertOnNonHeapUsage**: 非堆取内存使用比阈值
- **RequestQueue.cron**: RequestQueue监控任务的cron表达式
- **HealthCheck.cron**: HealthCheck监控任务的cron表达式
- **Thread.cron**: Thread监控任务的cron表达式
- **FullGC.cron**: FullGC监控任务的cron表达式
- **Memory.cron**: Memory监控任务的cron表达式

上述所有配置项都可以配置于"global"域下，或某个具体的tomcat实例下，后者的配置（如果有）将会覆盖前者 

- **lang**: 语言，支持enUS和zhCN
- **golbalReceiver**: 告警的全局接收人，可以配置手机号、Email地址等，逗号分隔
- **global.configurationReloadJob.cron**: 配置重载任务的cron表达式
- **global.minThreadDumpInterval**: 两组threadDump间的最小时间间隔（秒）

- **targetServers**: 部署了Apache Tomcat实例的服务器主机名（或者其他任何能够区分不同主机的标识），例如： targetServers=server1,server2 
- **{server}.host**: 某特定主机的hostname或IP地址，例如：server1.host=192.168.0.100
- **{server}.targetApps**: 某服务器主机中需要被监控的Apache Tomcat实例名（或其他任何能够区分不同Tomcat实例的标识），例如：server1.targetApps=tomcat1,tomcat2
- **{server}.{app}.JMXPort**: 某个具体Tomcat实例的JMX端口
- **{server}.{app}.port**: 某个具体Tomcat实例的HTTP端口
- **{server}.{app}.monitorJobs**: 对某个具体Tomcat实例启用的监控任务，逗号分隔。合法的监控任务包括：[RequestQueue, Thread, HealthCheck, FullGC, Memory]

可以为特定Tomcat实例的特定监控任务指定额外的告警接受者，当该监控任务触发告警时，会发送给全局接受者，以及额外接受者。例如：

	server1.tomcat1.RequestQueue.receiver=13800138004


### 告警发到哪里了？
所有被触发的告警都会写入logs/alarm.log中，格式为"<告警文本>|<空格分隔的接受者列表>"。可以部署程序监控此日志，并以其他形式（短信、邮件等）发出告警。

也可以实现kelgon.monitor.jmx.tomcat.util.AlarmSender类下的如下方法：

	public static boolean sendAlarm(String text, Set<String> receiver)

直接以特定形式发出告警

### threadDump在哪里？
在 dumps/ 目录下，每个dump文件的文件名标识了该dump所属的实例和抓取时间