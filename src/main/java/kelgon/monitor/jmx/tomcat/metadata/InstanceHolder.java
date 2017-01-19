package kelgon.monitor.jmx.tomcat.metadata;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InstanceHolder {
	public static Map<String, JobConfig> jobs;
	
	public static int minThreadDumpInterval;
	public static ConcurrentHashMap<String, Date> lastDumpDate = new ConcurrentHashMap<String, Date>();
	
	public static String reloadJobCron;
	
}
