package org.cobbzilla.restex;

import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;

public class RestexClientConnectionFactory implements HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> {

    private RestexClientConnectionManager connectionManager;
    private ManagedHttpClientConnectionFactory connectionFactory = new ManagedHttpClientConnectionFactory();

    public RestexClientConnectionFactory(RestexClientConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public ManagedHttpClientConnection create(HttpRoute route, ConnectionConfig config) {
        return new RestexClientConnection(connectionFactory.create(route, config), connectionManager.getTarget());
    }
}
