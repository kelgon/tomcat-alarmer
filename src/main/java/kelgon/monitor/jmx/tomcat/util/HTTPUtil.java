package kelgon.monitor.jmx.tomcat.util;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class HTTPUtil {
	public static Response get(String url, int connectTimeout, int readTimeout) throws ClientProtocolException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(readTimeout)
				.setConnectTimeout(connectTimeout).build();
		HttpGet httpget = new HttpGet(url);
		httpget.setConfig(requestConfig);
		CloseableHttpResponse response = httpclient.execute(httpget);
		try {
			Response res = new HTTPUtil.Response();
			res.setStatusCode(response.getStatusLine().getStatusCode());
			res.setReasonPhrase(response.getStatusLine().getReasonPhrase());
			res.setEntity(EntityUtils.toString(response.getEntity()));
			return res;
		} finally {
			response.close();
		}
	}
	
	public static class Response {
		private int statusCode;
		private String reasonPhrase;
		private String entity;
		public int getStatusCode() {
			return statusCode;
		}
		public void setStatusCode(int statusCode) {
			this.statusCode = statusCode;
		}
		public String getReasonPhrase() {
			return reasonPhrase;
		}
		public void setReasonPhrase(String reasonPhrase) {
			this.reasonPhrase = reasonPhrase;
		}
		public String getEntity() {
			return entity;
		}
		public void setEntity(String entity) {
			this.entity = entity;
		}
		
	}
}
