package kelgon.monitor.jmx.tomcat.util;

import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class JMXUtil {
	private JMXConnector connector = null;
	private MBeanServerConnection mbsc = null;
	
	public JMXUtil(String host, int port) throws Exception {
		super();
		String jmxURL = "service:jmx:rmi:///jndi/rmi://"+host+":"+port+"/jmxrmi";
		JMXServiceURL serviceURL = new JMXServiceURL(jmxURL);
		connector = JMXConnectorFactory.connect(serviceURL);
		mbsc = connector.getMBeanServerConnection();
	}
	
	public int getQueueInfo(String executorName) throws Exception {
		ObjectName mbean = new ObjectName("Catalina:type=Executor,name="+executorName);
		return (Integer)mbsc.getAttribute(mbean, "queueSize");
	}
	
	public Map<String, String> getMemoryInfo() throws Exception {
		Map<String, String> map = new HashMap<String, String>();
		ObjectName serverMbean = new ObjectName("java.lang:type=Memory");
		CompositeDataSupport data = (CompositeDataSupport)mbsc.getAttribute(serverMbean, "HeapMemoryUsage");
		map.put("heapUsed", byte2MB((Long)data.get("used")));
		map.put("heapMax",  byte2MB((Long)data.get("max")));
		data = (CompositeDataSupport)mbsc.getAttribute(serverMbean, "NonHeapMemoryUsage");
		map.put("nonHeapUsed", byte2MB((Long)data.get("used")));
		map.put("nonHeapMax",  byte2MB((Long)data.get("max")));
		
		return map;
	}
	
	public Set<Map<String, Object>> getGCInfo() throws Exception {
		Set<Map<String, Object>> gcInfoSet = new HashSet<Map<String, Object>>();
		ObjectName serverMbean = new ObjectName("java.lang:type=GarbageCollector,name=*");
		Set<ObjectInstance> set = mbsc.queryMBeans(serverMbean, null);
		for(ObjectInstance oi : set) {
			
			Map<String, Object> map = new HashMap<String,Object>();
			String gcName = (String)mbsc.getAttribute(oi.getObjectName(), "Name");
			map.put("gcName", gcName);
			
			String[] poolNames = (String[])mbsc.getAttribute(oi.getObjectName(), "MemoryPoolNames");
			boolean isFullGC = false;
			for(String pool : poolNames) {
				if(pool.contains("Old") || pool.contains("old")) {
					isFullGC = true;
					break;
				}
			}
			map.put("isFullGC", isFullGC);
			
			CompositeDataSupport data = (CompositeDataSupport)mbsc.getAttribute(oi.getObjectName(), "LastGcInfo");
			map.put("duration", data.get("duration"));
			TabularDataSupport before = (TabularDataSupport)data.get("memoryUsageBeforeGc");
			for(Entry<Object, Object> e : before.entrySet()) {
				if(e.getKey().toString().contains("Old")) {
					CompositeDataSupport mem = (CompositeDataSupport)((CompositeDataSupport)e.getValue()).get("value");
					map.put("oldUsedBefore", byte2MB((Long)mem.get("used")));
					map.put("oldTotal", byte2MB((Long)mem.get("max")));
				}
				if(e.getKey().toString().contains("Eden")) {
					CompositeDataSupport mem = (CompositeDataSupport)((CompositeDataSupport)e.getValue()).get("value");
					map.put("edenUsedBefore", byte2MB((Long)mem.get("used")));
					map.put("edenTotal", byte2MB((Long)mem.get("max")));
				}
				if(e.getKey().toString().contains("Survivor")) {
					CompositeDataSupport mem = (CompositeDataSupport)((CompositeDataSupport)e.getValue()).get("value");
					map.put("survivorUsedBefore", byte2MB((Long)mem.get("used")));
					map.put("survivorTotal", byte2MB((Long)mem.get("max")));
				}
				if(e.getKey().toString().contains("Perm")) {
					CompositeDataSupport mem = (CompositeDataSupport)((CompositeDataSupport)e.getValue()).get("value");
					map.put("permUsedBefore", byte2MB((Long)mem.get("used")));
					map.put("permTotal", byte2MB((Long)mem.get("max")));
				}
			}
			TabularDataSupport after = (TabularDataSupport)data.get("memoryUsageAfterGc");
			for(Entry<Object, Object> e : after.entrySet()) {
				if(e.getKey().toString().contains("Old")) {
					CompositeDataSupport mem = (CompositeDataSupport)((CompositeDataSupport)e.getValue()).get("value");
					map.put("oldUsedAfter", byte2MB((Long)mem.get("used")));
				}
				if(e.getKey().toString().contains("Eden")) {
					CompositeDataSupport mem = (CompositeDataSupport)((CompositeDataSupport)e.getValue()).get("value");
					map.put("edenUsedAfter", byte2MB((Long)mem.get("used")));
				}
				if(e.getKey().toString().contains("Survivor")) {
					CompositeDataSupport mem = (CompositeDataSupport)((CompositeDataSupport)e.getValue()).get("value");
					map.put("survivorUsedAfter", byte2MB((Long)mem.get("used")));
				}
				if(e.getKey().toString().contains("Perm")) {
					CompositeDataSupport mem = (CompositeDataSupport)((CompositeDataSupport)e.getValue()).get("value");
					map.put("permUsedAfter", byte2MB((Long)mem.get("used")));
				}
			}
			gcInfoSet.add(map);
			
		}
		return gcInfoSet;
	}
	
	public List<ThreadInfo> getThreadsInfo() throws Exception {
		ObjectName serverMbean = new ObjectName("java.lang:type=Threading");
		Object o = mbsc.getAttribute(serverMbean, "AllThreadIds");
		Object[] param = new Object[1];
		param[0] = o;
		CompositeData[] cds = (CompositeData[])mbsc.invoke(serverMbean, "getThreadInfo", new Object[] {o}, new String[] {long[].class.getName()});
		List<ThreadInfo> threads = new ArrayList<ThreadInfo>();
		for(CompositeData cd : cds) {
			threads.add(ThreadInfo.from(cd));
		}
		return threads;
	}
	
	public List<String> dumpAllThreads() throws Exception {
		ObjectName serverMbean = new ObjectName("java.lang:type=Threading");
		CompositeData[] cds = (CompositeData[])mbsc.invoke(serverMbean, "dumpAllThreads", new Object[] {true,false}, new String[] {boolean.class.getName(),boolean.class.getName()});
		List<String> dumps = new ArrayList<String>(cds.length);
		for(CompositeData cd : cds) {
			ThreadInfo t = ThreadInfo.from(cd);
			dumps.add(toString(t));
		}
		return dumps;
	}
	
	private String toString(ThreadInfo t) {
        StringBuilder sb = new StringBuilder("\"" + t.getThreadName() + "\"" +
                                             " Id=" + t.getThreadId() + " " +
                                             t.getThreadState());
        if (t.getLockName() != null) {
            sb.append(" on " + t.getLockName());
        }
        if (t.getLockOwnerName() != null) {
            sb.append(" owned by \"" + t.getLockOwnerName() +
                      "\" Id=" + t.getLockOwnerId());
        }
        if (t.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (t.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        int i = 0;
        StackTraceElement[] stackTrace = t.getStackTrace();
        for (; i < stackTrace.length && i < 50; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && t.getLockInfo() != null) {
                Thread.State ts = t.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on " + t.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on " + t.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on " + t.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : t.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
       }
       if (i < stackTrace.length) {
           sb.append("\t...");
           sb.append('\n');
       }

       LockInfo[] locks = t.getLockedSynchronizers();
       if (locks.length > 0) {
           sb.append("\n\tNumber of locked synchronizers = " + locks.length);
           sb.append('\n');
           for (LockInfo li : locks) {
               sb.append("\t- " + li);
               sb.append('\n');
           }
       }
       sb.append('\n');
       return sb.toString();
    }
	
	public void close() {
		try {
			connector.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private String byte2MB(Long bytes) {
		double mb = bytes/(1024*1024d);
		DecimalFormat df = new DecimalFormat("0.00");
		return df.format(mb);
	}
	
}
