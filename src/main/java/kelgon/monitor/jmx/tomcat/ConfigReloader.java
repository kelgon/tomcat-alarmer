package kelgon.monitor.jmx.tomcat;

import java.io.InputStream;
import java.util.Properties;

import kelgon.monitor.jmx.tomcat.util.JobConfigLoader;

import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ConfigReloader implements Job {
	private static Logger log = Logger.getLogger(ConfigReloader.class);
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		log.info("configurationReloadJob start");
		log.info("reloading monitorJob.properties...");
		try {
			InputStream is = Monitor.class.getClassLoader().getResourceAsStream("monitorJob.properties");
			Properties props = new Properties();
			props.load(is);
			JobConfigLoader.load(props);
		} catch(Exception e) {
			log.error("error while loading monitorJob.properties", e);
			log.error("reload configuration failed, continue with old configurations...");
			return;
		}
	}

}
