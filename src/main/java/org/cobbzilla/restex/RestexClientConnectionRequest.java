package org.cobbzilla.restex;

import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.ManagedHttpClientConnection;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class RestexClientConnectionRequest implements ConnectionRequest {

    private final ConnectionRequest delegate;
    private final RestexCaptureTarget target;

    public RestexClientConnectionRequest(ConnectionRequest clientConnectionRequest, RestexCaptureTarget target) {
        this.delegate = clientConnectionRequest;
        this.target = target;
    }

    @Override public boolean cancel() { return delegate.cancel(); }

    @Override
    public ManagedHttpClientConnection get(long timeout, TimeUnit tunit) throws InterruptedException, ExecutionException, ConnectionPoolTimeoutException {
        final ManagedHttpClientConnection connection = (ManagedHttpClientConnection) delegate.get(timeout, tunit);
        return new RestexClientConnection(connection, target);
    }

}
