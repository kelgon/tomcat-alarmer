package kelgon.monitor.jmx.tomcat;

import org.apache.log4j.Logger;

public class ShutdownCleaner extends Thread {
	private static final Logger log = Logger.getLogger(ShutdownCleaner.class);
	
	public void run() {
		log.info("stopping tomcat alarmer, waiting for ongoing jobs to complete...");
		try {
			Monitor.sched.shutdown(true);
			log.info("all jobs completed, shutdown complete");
		} catch(Exception e) {
			log.error("error while stopping tomcat alarmer", e);
		}
	}
}
