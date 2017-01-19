package kelgon.monitor.jmx.tomcat.util;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import kelgon.monitor.jmx.tomcat.exception.IllegalConfigException;
import kelgon.monitor.jmx.tomcat.metadata.InstanceHolder;
import kelgon.monitor.jmx.tomcat.metadata.JobConfig;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

public class JobConfigLoader {
	private static Logger log = Logger.getLogger(JobConfigLoader.class);
	
	private static String[] validJobType = {"Memory","RequestQueue","HealthCheck","FullGC","Thread"};
	private static Map<String, String> props = new HashMap<String, String>();
	private static String[] globalReceiver = null;
	private static Map<String, String> templates = new HashMap<String, String>();
	
	public static void load(Properties in) throws Exception {
		for(Entry<Object, Object> e : in.entrySet()) {
			props.put(e.getKey().toString(), e.getValue().toString());
		}
		Map<String, JobConfig> jobs = new HashMap<String, JobConfig>();
		
		String lang = props.get("lang");
		InputStream is = JobConfigLoader.class.getClassLoader().getResourceAsStream("alarmTemplate_"
				+ lang + ".properties");
		if(is != null) {
			Properties temp = new Properties();
			temp.load(is);
			for(Entry<Object, Object> e : temp.entrySet()) {
				templates.put(e.getKey().toString(), e.getValue().toString());
			}
		} else {
			throw new IllegalConfigException("properties file not found: " + "alarmTemplate_" + lang + ".properties");
		}
		
		String interval = props.get("global.minThreadDumpInterval");
		if(StringUtils.isNotBlank(interval)) {
			try {
				InstanceHolder.minThreadDumpInterval = Integer.parseInt(interval);
			} catch(NumberFormatException e) {
				throw new IllegalConfigException("invalid value of 'global.minThreadDumpInterval': " + interval);
			}
		}
		
		String reloadCron = props.get("global.configurationReloadJob.cron");
		if(StringUtils.isNotBlank(reloadCron)) {
			InstanceHolder.reloadJobCron = reloadCron;
		} else {
			throw new IllegalConfigException("invalid value of 'global.configurationReloadJob.cron'");
		}
		
		String gRecv = props.get("globalReceiver");
		if(StringUtils.isNotBlank(gRecv)) {
			globalReceiver = gRecv.split(",");
		}
		
		String targetServers = props.get("targetServers");
		if(StringUtils.isBlank(targetServers)) {
			throw new IllegalConfigException("invalid value of 'targetServers'");
		}
		for(String server : targetServers.split(",")) {
			String host = props.get(server+".host");
			if(StringUtils.isBlank(host)) {
				throw new IllegalConfigException("invalid value of '"+server+".host'");
			}
			String targetApps = props.get(server+".targetApps");
			if(StringUtils.isBlank(targetApps)) {
				log.warn("cannot find valid conf '"+server+".targetApps', skip loading conf under "+server);
				continue;
			}
			for(String app : targetApps.split(",")) {
				int JmxPort = 0;
				try {
					JmxPort = Integer.parseInt(props.get(server+"."+app+".JMXPort"));
				} catch(NumberFormatException e) {
					throw new IllegalConfigException("invalid value of '"+server+"."+app+".JMXPort': "
							+ props.get(server+"."+app+".JMXPort"));
				}
				int servicePort = 0;
				try {
					servicePort = Integer.parseInt(props.get(server+"."+app+".port"));
				} catch(NumberFormatException e) {
					throw new IllegalConfigException("invalid value of '"+server+"."+app+".port: "
							+ props.get(server+"."+app+".port"));
				}
				String monitorjobs = props.get(server+"."+app+".monitorJobs");
				if(StringUtils.isBlank(monitorjobs)) {
					log.warn("cannot find valid conf '"+server+"."+app+".monitorJob', skip loading jobs under "
							+ server + "." + app);
					continue;
				}
				for(String job : monitorjobs.split(",")) {
					JobConfig jc = new JobConfig();
					jc.setServerIp(host);
					jc.setTargetApp(app);
					jc.setTargetServer(server);
					jc.setJxmPort(JmxPort);
					jc.setServicePort(servicePort);
					if(isValidJobType(job)) {
						jc.setJobType(job);
						jc.setAlarmReceiver(getReceiver(server, app, job));
						jc.setCron(getValidConfig(server, app, job, "cron"));
					} else {
						log.warn("invalid jobType under '"+server+"."+app+".monitorJob', skipped");
						continue;
					}
					Map<String, String> conf = new HashMap<String, String>();
					if(job.equals("Memory")) {
						conf.put("alertOnHeapUsage", getValidConfig(server, app, job, "alertOnHeapUsage"));
						conf.put("alertOnNonHeapUsage", getValidConfig(server, app, job, "alertOnNonHeapUsage"));
					} else if(job.equals("RequestQueue")) {
						conf.put("executorName", getValidConfig(server, app, job, "executorName"));
						conf.put("alertOnQueueSize", getValidConfig(server, app, job, "alertOnQueueSize"));
						conf.put("dumpThreadsOnAlert", getValidConfig(server, app, job, "dumpThreadsOnAlert"));
					} else if(job.equals("FullGC")) {
						conf.put("alertOnUsageAfterGC", getValidConfig(server, app, job, "alertOnUsageAfterGC"));
						conf.put("alertOnCostTime", getValidConfig(server, app, job, "alertOnCostTime"));
					} else if(job.equals("Thread")) {
						conf.put("alertOnBusyRate", getValidConfig(server, app, job, "alertOnBusyRate"));
						conf.put("alertOnBlockTime", getValidConfig(server, app, job, "alertOnBlockTime"));
						conf.put("dumpThreadsOnAlert", getValidConfig(server, app, job, "dumpThreadsOnAlert"));
						String prefix = getValidConfig(server, app, job, "prefix");
						conf.put("prefix", prefix);
						for(String p : prefix.split(",")) {
							conf.put(p+".idleStatus", getValidConfig(server, app, job, p+".idleStatus"));
						}
					} else if(job.equals("HealthCheck")) {
						conf.put("url", getValidConfig(server, app, job, "url"));
						conf.put("connectTimeout", getValidConfig(server, app, job, "connectTimeout"));
						conf.put("readTimeout", getValidConfig(server, app, job, "readTimeout"));
						conf.put("expectation", getValidConfig(server, app, job, "expectation"));
					}
					jc.setConf(conf);
					jobs.put(server+"."+app+"."+job, jc);
				}
			}
		}
		InstanceHolder.jobs = jobs;
		log.info("loaded jobs: ");
		for(Entry<String, JobConfig> e : InstanceHolder.jobs.entrySet()) {
			log.info(e.getValue().toString());
		}
	}
	

	private static Set<String> getReceiver(String server, String app, String jobType) {
		Set<String> receivers = new HashSet<String>();
		String extraRecv = props.get(server+"."+app+"."+jobType+".receiver");
		if(globalReceiver != null) {
			for(String gRecv : globalReceiver) {
				receivers.add(gRecv);
			}
		}
		if(StringUtils.isNotBlank(extraRecv)) {
			for(String recv : extraRecv.split(",")) {
				receivers.add(recv);
			}
		}
		return receivers;
	}
	
	private static boolean isValidJobType(String jobType) {
		for(String j : validJobType) {
			if(j.equals(jobType)) {
				return true;
			}
		}
		return false;
	}
	
	private static String getValidConfig(String server, String app, String jobType, String config) throws IllegalConfigException {
		String jobConf = props.get(server+"."+app+"."+jobType+"."+config);
		String globalConf = props.get("global."+jobType+"."+config);
		if(StringUtils.isBlank(jobConf) && StringUtils.isBlank(globalConf)) {
			throw new IllegalConfigException("cannot find ["+jobType+"."+config+"] under local or global context");
		}
		else if(StringUtils.isBlank(jobConf)) {
			return globalConf;
		} else {
			return jobConf;
		}
	}
	
	public static String getAlarmMsg(String key, String current, String threshold, String total, String extra) {
		String msg = templates.get(key);
		if(current != null) {
			msg = msg.replace("{current}", current);
		}
		if(threshold != null) {
			msg = msg.replace("{threshold}", threshold);
		}
		if(total != null) {
			msg = msg.replace("{total}", total);
		}
		if(extra != null) {
			msg = msg.replace("{extra}", extra);
		}
		return msg;
	}
	
	public static String getAlarmMsg(String key) {
		return templates.get(key);
	}
}
