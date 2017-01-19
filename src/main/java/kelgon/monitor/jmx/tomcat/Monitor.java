package kelgon.monitor.jmx.tomcat;

import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Properties;

import kelgon.monitor.jmx.tomcat.metadata.InstanceHolder;
import kelgon.monitor.jmx.tomcat.metadata.JobConfig;
import kelgon.monitor.jmx.tomcat.util.JobConfigLoader;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

public class Monitor {
	private static Logger log = Logger.getLogger(Monitor.class);
	public static Scheduler sched = null;
	
	private static void loadJobs() throws Exception {
		InputStream is = Monitor.class.getClassLoader().getResourceAsStream("monitorJob.properties");
		Properties props = new Properties();
		props.load(is);
		JobConfigLoader.load(props);
	}
	
	private static void startJobs() throws SchedulerException {
		SchedulerFactory sf = new StdSchedulerFactory();
		sched = sf.getScheduler();
		
		for(Entry<String, JobConfig> e : InstanceHolder.jobs.entrySet()) {
			JobConfig jc = e.getValue();
			JobDetail job = JobBuilder.newJob(JobRunner.class).withIdentity(e.getKey(), "group1").build();
		    CronTrigger trigger = (CronTrigger)TriggerBuilder.newTrigger().withIdentity(e.getKey()+"Trigger", "group1")
		    		.withSchedule(CronScheduleBuilder.cronSchedule(jc.getCron())).build();
		    job.getJobDataMap().put("jobKey", e.getKey());
		    sched.scheduleJob(job, trigger);
			log.info("job registered: " + e.getKey());
		}

		JobDetail job = JobBuilder.newJob(ConfigReloader.class).withIdentity("configReloader", "group2").build();
	    CronTrigger trigger = (CronTrigger)TriggerBuilder.newTrigger().withIdentity("configReloaderTrigger", "group2")
	    		.withSchedule(CronScheduleBuilder.cronSchedule(InstanceHolder.reloadJobCron)).build();
	    sched.scheduleJob(job, trigger);
	    
		sched.start();
	}
	
	public static void main(String[] args) {
		PropertyConfigurator.configure(Monitor.class.getClassLoader().getResource("log4j.properties"));
		log.info("initializing tomcat alarmer...");
		log.info("loading monitorJob.properties...");
		try {
			loadJobs();
		} catch(Throwable e) {
			log.error("error while loading monitorJob.properties", e);
			log.error("initializing procedure aborted");
			return;
		}
		log.info("load monitorJob.properties completed");
		log.info("registering jobs...");
		try {
			startJobs();
		} catch (SchedulerException e) {
			log.error("error while registering jobs", e);
			log.error("initializing procedure aborted");
			return;
		}
		log.info("register jobs completed");
		log.info("registering ShutdownHook...");
		Runtime.getRuntime().addShutdownHook(new ShutdownCleaner());
		log.info("tomcat alarmer started");
	}
}
