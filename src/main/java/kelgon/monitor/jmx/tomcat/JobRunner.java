package kelgon.monitor.jmx.tomcat;

import java.io.File;
import java.io.FileWriter;
import java.lang.management.ThreadInfo;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kelgon.monitor.jmx.tomcat.metadata.InstanceHolder;
import kelgon.monitor.jmx.tomcat.metadata.JobConfig;
import kelgon.monitor.jmx.tomcat.util.AlarmSender;
import kelgon.monitor.jmx.tomcat.util.HTTPUtil;
import kelgon.monitor.jmx.tomcat.util.HTTPUtil.Response;
import kelgon.monitor.jmx.tomcat.util.JMXUtil;
import kelgon.monitor.jmx.tomcat.util.JobConfigLoader;

import org.apache.commons.lang.StringUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class JobRunner implements Job {
	private static Logger log = Logger.getLogger(JobRunner.class);
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		String jobKey = String.valueOf(arg0.getJobDetail().getJobDataMap().get("jobKey"));
		log.info("launch job: "+jobKey);
		JobConfig jc = InstanceHolder.jobs.get(jobKey);
		if(jc.getJobType().equals("Memory")) {
			runMemoryJob(jc);
		} else if(jc.getJobType().equals("RequestQueue")) {
			runRequestQueueJob(jc);
		} else if(jc.getJobType().equals("Thread")) {
			runThreadJob(jc);
		} else if(jc.getJobType().equals("FullGC")) {
			runFullGCJob(jc);
		} else if(jc.getJobType().equals("HealthCheck")) {
			runHealthChechJob(jc);
		}
	}
	
	private void runMemoryJob(JobConfig jc) {
		JMXUtil jmx = null;
		try {
			jmx = new JMXUtil(jc.getServerIp(), jc.getJxmPort());
			Map<String, String> memUsage = jmx.getMemoryInfo();
			double heapUsage = Double.valueOf(memUsage.get("heapUsed")) /
					Double.valueOf(memUsage.get("heapMax")) * 100;
			double nonHeapUsage = Double.valueOf(memUsage.get("nonHeapUsed")) /
					Double.valueOf(memUsage.get("nonHeapMax")) * 100;
			double heapThreshold = Double.valueOf(jc.getConf().get("alertOnHeapUsage"));
			double nonHeapThreshold = Double.valueOf(jc.getConf().get("alertOnNonHeapUsage"));
			
			if(heapUsage >= heapThreshold) {
				String alarm = JobConfigLoader.getAlarmMsg("heapUsage", formatDouble(heapUsage, "0.00"), 
						formatDouble(heapThreshold, "0.00"), memUsage.get("heapMax"), null);
				AlarmSender.sendAlarm(getAlarmText(jc) + alarm, jc.getAlarmReceiver());
			} else {
				log.info(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType() + " current heap usage: "
						+ formatDouble(heapUsage, "0.00") + "%");
			}
			if(nonHeapUsage >= nonHeapThreshold) {
				String alarm = JobConfigLoader.getAlarmMsg("nonheapUsage", formatDouble(nonHeapUsage, "0.00"),
						formatDouble(nonHeapThreshold, "0.00"), memUsage.get("nonHeapMax"), null);
				AlarmSender.sendAlarm(getAlarmText(jc) + alarm, jc.getAlarmReceiver());
			} else {
				log.info(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType() + " current non-heap usage: "
						+ formatDouble(nonHeapUsage, "0.00") + "%");
			}
		} catch(Throwable t) {
			log.error(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType()+" error executing job", t);
			AlarmSender.sendAlarm(getAlarmText(jc) + JobConfigLoader.getAlarmMsg("error"), jc.getAlarmReceiver());
		} finally {
			if(jmx != null)
				jmx.close();
		}
	}
	
	private void runRequestQueueJob(JobConfig jc) {
		JMXUtil jmx = null;
		try {
			jmx = new JMXUtil(jc.getServerIp(), jc.getJxmPort());
			int qSize = jmx.getQueueInfo(jc.getConf().get("executorName"));
			int qSizeThreshold = Integer.parseInt(jc.getConf().get("alertOnQueueSize"));
			boolean dumpThread = Boolean.parseBoolean(jc.getConf().get("dumpThreadsOnAlert"));
			boolean dump = false;

			if(qSize >= qSizeThreshold) {
				dump = true;
				String alarm = JobConfigLoader.getAlarmMsg("requestQueue", String.valueOf(qSize),
						String.valueOf(qSizeThreshold), null, null);
				AlarmSender.sendAlarm(getAlarmText(jc) + alarm, jc.getAlarmReceiver());
			} else {
				log.info(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType() + " current requestQueue size: "
						+ qSize);
			}
			
			if(dump && dumpThread) {
				dumpThread(jc, jmx);
			}
		} catch(Throwable t) {
			log.error(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType()+" error executing job", t);
			AlarmSender.sendAlarm(getAlarmText(jc) + JobConfigLoader.getAlarmMsg("error"), jc.getAlarmReceiver());
		} finally {
			if(jmx != null)
				jmx.close();
		}
	}
	
	private void runThreadJob(JobConfig jc) {
		JMXUtil jmx = null;
		try {
			jmx = new JMXUtil(jc.getServerIp(), jc.getJxmPort());
			List<ThreadInfo> tList = jmx.getThreadsInfo();
			String prefixes = jc.getConf().get("prefix");
			int busyRateThreshold = Integer.parseInt(jc.getConf().get("alertOnBusyRate"));
			long blockTimeThreshold = Long.parseLong(jc.getConf().get("alertOnBlockTime"));
			boolean dumpThread = Boolean.parseBoolean(jc.getConf().get("dumpThreadsOnAlert"));

			boolean dump = false;
			for(String prefix : prefixes.split(",")) {
				if(StringUtils.isNotBlank(prefix)) {
					int count = 0;
					int busyCount = 0;
					for(ThreadInfo t : tList) {
						if(t == null || t.getThreadName() == null)
							continue;
						if(t.getThreadName().startsWith(prefix)) {
							count++;
							if(t.getBlockedTime() >= blockTimeThreshold) {
								dump = true;
								String alarm = JobConfigLoader.getAlarmMsg("threadBlock", String.valueOf(t.getBlockedTime()), 
										String.valueOf(blockTimeThreshold), null, t.getThreadName());
								AlarmSender.sendAlarm(getAlarmText(jc) + alarm, jc.getAlarmReceiver());
							}
							String idleStatus = jc.getConf().get(prefix+".idleStatus");
							String tStatus = t.getThreadState().toString();
							if(!tStatus.equalsIgnoreCase(idleStatus)) {
								busyCount++;
							}
						}
					}
					double busyRate = (double)busyCount/count;
					if(busyRate >= busyRateThreshold) {
						dump = true;
						String alarm = JobConfigLoader.getAlarmMsg("threadBusy", formatDouble(busyRate*100, "0.00"),
								String.valueOf(busyRateThreshold), String.valueOf(count), prefix);
						AlarmSender.sendAlarm(getAlarmText(jc) + alarm, jc.getAlarmReceiver());
					} else {
						log.info(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType()+" ["+prefix+
								"] thread current busy rate: "+busyCount);
					}
				}
			}
			if(dump && dumpThread) {
				dumpThread(jc, jmx);
			}
		} catch(Throwable t) {
			log.error(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType()+" error executing job", t);
			AlarmSender.sendAlarm(getAlarmText(jc) + JobConfigLoader.getAlarmMsg("error"), jc.getAlarmReceiver());
		} finally {
			if(jmx != null)
				jmx.close();
		}
	}
	
	private void runHealthChechJob(JobConfig jc) {
		String url = "http://" + jc.getServerIp() + ":" + jc.getServicePort() + jc.getConf().get("url");
		int connectTimeout =Integer.parseInt(jc.getConf().get("connectTimeout"));
		int readTimeout = Integer.parseInt(jc.getConf().get("readTimeout"));
		Date now = new Date();
		try {
			Response response = HTTPUtil.get(url, connectTimeout, readTimeout);
			if(response.getStatusCode() != 200) {
				String alarm = JobConfigLoader.getAlarmMsg("statusCode", String.valueOf(response.getStatusCode()),
						null, null, null);
				AlarmSender.sendAlarm(getAlarmText(jc) + alarm, jc.getAlarmReceiver());
			} else if(!jc.getConf().get("expectation").equals(response.getEntity())) {
				String alarm = JobConfigLoader.getAlarmMsg("statusCode", response.getEntity(), null, null, null);
				AlarmSender.sendAlarm(getAlarmText(jc) + alarm, jc.getAlarmReceiver());
			} else {
				long cost = new Date().getTime() - now.getTime();
				log.info(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType()+" cost: "+cost+"ms");
			}
		} catch (SocketTimeoutException e1) {
			AlarmSender.sendAlarm(getAlarmText(jc) + JobConfigLoader.getAlarmMsg("requestTimeout"),
					jc.getAlarmReceiver());
		} catch (ConnectTimeoutException e2) {
			AlarmSender.sendAlarm(getAlarmText(jc) + JobConfigLoader.getAlarmMsg("connectTimeout"),
					jc.getAlarmReceiver());
		} catch (Exception e3) {
			AlarmSender.sendAlarm(getAlarmText(jc) + JobConfigLoader.getAlarmMsg("error"), jc.getAlarmReceiver());
		}
	}
	
	private void runFullGCJob(JobConfig jc) {
		JMXUtil jmx = null;
		try {
			jmx = new JMXUtil(jc.getServerIp(), jc.getJxmPort());
			Set<Map<String, Object>> gcSet = jmx.getGCInfo();
			for(Map<String, Object> gcMap : gcSet) {
				if((Boolean)gcMap.get("isFullGC")) {
					long duration = (Long)gcMap.get("duration");
					double oldAfter = Double.parseDouble((String)gcMap.get("oldUsedAfter"));
					double oldTotal = Double.parseDouble((String)gcMap.get("oldTotal"));
					double alertOnUsageAfterGC = Double.parseDouble((String)jc.getConf().get("alertOnUsageAfterGC"));
					long durationThreshold = Long.parseLong((String)jc.getConf().get("alertOnCostTime"));
					
					if(duration >= durationThreshold) {
						String alarm = JobConfigLoader.getAlarmMsg("fullGCcost", String.valueOf(duration),
								String.valueOf(durationThreshold), null, null);
						AlarmSender.sendAlarm(getAlarmText(jc) + alarm, jc.getAlarmReceiver());
					} else {
						log.info(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType()+" last FullGC cost "
								+duration+"ms");
					}
					if(oldAfter/oldTotal > alertOnUsageAfterGC) {
						String alarm = JobConfigLoader.getAlarmMsg("oldGenUsage",
								formatDouble(oldAfter/oldTotal, "0.00"), String.valueOf(alertOnUsageAfterGC), null, null);
						AlarmSender.sendAlarm(getAlarmText(jc) + alarm, jc.getAlarmReceiver());
					} else {
						log.info(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType()+
								" OldGen usage after last FullGC: "+formatDouble(oldAfter/oldTotal*100, "0.00")+"%");
					}
				}
			}
		} catch(Throwable t) {
			log.error(jc.getTargetServer()+"."+jc.getTargetApp()+"."+jc.getJobType()+" error executing job", t);
			AlarmSender.sendAlarm(getAlarmText(jc) + JobConfigLoader.getAlarmMsg("error"), jc.getAlarmReceiver());
		} finally {
			if(jmx != null)
				jmx.close();
		}
	}
	
	private String getAlarmText(JobConfig jc) {
		StringBuffer sb = new StringBuffer("Tomcat alarm: ");
		sb.append("[").append(jc.getTargetServer()).append(".").append(jc.getTargetApp()).append(".")
			.append(jc.getJobType()).append("] ");
		return sb.toString();
	}
	
	private String formatDouble(double in, String format) {
		DecimalFormat df = new DecimalFormat(format);
		return df.format(in);
	}
	
	private void dumpThread(JobConfig jc, JMXUtil jmx) throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
		Date lastDump = InstanceHolder.lastDumpDate.get(jc.getTargetServer()+"."+jc.getTargetApp());
		if(lastDump != null) {
			if(InstanceHolder.minThreadDumpInterval == 0)
				InstanceHolder.minThreadDumpInterval = 300;
			if(lastDump.getTime()+InstanceHolder.minThreadDumpInterval*1000 > new Date().getTime()) {
				log.info(jc.getTargetServer()+"."+jc.getTargetApp()+
						" thread dump too often, skip this try. Last dump time: " + sdf.format(lastDump));
				return;
			}
		}
		int times = 3;
		int i=0;
		log.info("dumping thread of "+jc.getTargetServer()+"."+jc.getTargetApp());
		FileWriter fw = null;
		while(i++<times) {
			try {
				if(i>1)
					Thread.sleep(5000);
				String date = sdf.format(new Date());
				log.info("dumping thread of "+jc.getTargetServer()+"."+jc.getTargetApp()+" (no."+i+")...");
				List<String> dumps = jmx.dumpAllThreads();
				File file = new File("../dumps/"+jc.getTargetServer()+"."+jc.getTargetApp()+"_"+date+".dump");
				file.createNewFile();
				fw = new FileWriter(file);
				for(String t : dumps) {
					fw.write(t);
					fw.write("\r\n");
				}
			} catch(Exception e) {
				log.error("error dumping thread of "+jc.getTargetServer()+"."+jc.getTargetApp(), e);
			} finally {
				if(fw != null)
					fw.close();
			}
		}
		InstanceHolder.lastDumpDate.put(jc.getTargetServer()+"."+jc.getTargetApp(), new Date());
		log.info(jc.getTargetServer()+"."+jc.getTargetApp()+" thread  dump completed");
	}
}
