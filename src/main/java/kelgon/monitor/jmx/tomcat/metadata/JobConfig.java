package kelgon.monitor.jmx.tomcat.metadata;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class JobConfig {
	private String targetServer;
	private String serverIp;
	private String targetApp;
	private int jxmPort;
	private int servicePort;
	private String userName;
	private String password;
	private String instanceName;
	private String jobType;
	private Set<String> alarmReceiver;
	private String cron;
	private Map<String, String> conf;
	public String getTargetServer() {
		return targetServer;
	}
	public void setTargetServer(String targetServer) {
		this.targetServer = targetServer;
	}
	public String getServerIp() {
		return serverIp;
	}
	public void setServerIp(String serverIp) {
		this.serverIp = serverIp;
	}
	public String getTargetApp() {
		return targetApp;
	}
	public void setTargetApp(String targetApp) {
		this.targetApp = targetApp;
	}
	public int getJxmPort() {
		return jxmPort;
	}
	public void setJxmPort(int jxmPort) {
		this.jxmPort = jxmPort;
	}
	public String getInstanceName() {
		return instanceName;
	}
	public void setInstanceName(String instanceName) {
		this.instanceName = instanceName;
	}
	public String getJobType() {
		return jobType;
	}
	public void setJobType(String jobType) {
		this.jobType = jobType;
	}
	public Set<String> getAlarmReceiver() {
		return alarmReceiver;
	}
	public void setAlarmReceiver(Set<String> alarmReceiver) {
		this.alarmReceiver = alarmReceiver;
	}
	public String getCron() {
		return cron;
	}
	public void setCron(String cron) {
		this.cron = cron;
	}
	public Map<String, String> getConf() {
		return conf;
	}
	public void setConf(Map<String, String> conf) {
		this.conf = conf;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public int getServicePort() {
		return servicePort;
	}
	public void setServicePort(int servicePort) {
		this.servicePort = servicePort;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(this.getTargetServer()).append("(").append(this.getServerIp()).append("),").append(this.getTargetApp())
				.append("(jmxPort:").append(this.getJxmPort()).append(",port:").append(this.getServicePort())
				.append("),jobType:").append(this.getJobType()).append(",cron:").append(this.getCron()).append(",conf:[");
		for(Entry<String, String> e : conf.entrySet()) {
			sb.append(e.getKey()).append("-").append(e.getValue()).append(",");
		}
		sb.deleteCharAt(sb.length()-1).append("] receiver:[");
		for(String recv : this.getAlarmReceiver()) {
			sb.append(recv).append(",");
		}
		sb.deleteCharAt(sb.length()-1).append("]");
		return sb.toString();
	}
	
}
