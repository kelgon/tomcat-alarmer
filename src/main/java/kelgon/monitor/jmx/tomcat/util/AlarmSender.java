package kelgon.monitor.jmx.tomcat.util;

import java.util.Set;

import org.apache.log4j.Logger;

public class AlarmSender {
	private static Logger log = Logger.getLogger(AlarmSender.class);
	
	public static boolean sendAlarm(String text, Set<String> receiver) {
		String rs = "";
		for(String r : receiver) {
			rs += r + " ";
		}
		log.warn(text + "|" + rs);
		//TODO: implement your own alarm mechanism
		return true;
	}
}
