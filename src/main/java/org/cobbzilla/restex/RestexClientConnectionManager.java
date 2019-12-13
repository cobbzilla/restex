package org.cobbzilla.restex;

import lombok.Delegate;
import lombok.Getter;
import org.apache.http.client.HttpClient;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

public class RestexClientConnectionManager implements HttpClientConnectionManager {

    @Delegate private final BasicHttpClientConnectionManager delegate;
    @Getter private final RestexCaptureTarget target;

    private static Registry<ConnectionSocketFactory> getDefaultRegistry() {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .build();
    }

    public RestexClientConnectionManager(RestexCaptureTarget target) {
        this.delegate = new BasicHttpClientConnectionManager(getDefaultRegistry(), new RestexClientConnectionFactory(this));
        this.target = target;
    }

    public HttpClient getHttpClient () {
        return HttpClientBuilder.create().setConnectionManager(new RestexClientConnectionManager(target)).build();
    }
}
