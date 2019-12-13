package org.cobbzilla.restex;

import lombok.Cleanup;
import lombok.Delegate;
import org.apache.commons.io.IOUtils;
import org.apache.http.*;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.entity.BasicHttpEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

public class RestexClientConnection implements ManagedHttpClientConnection {

    private interface ConnectionIO {
        void sendRequestHeader(HttpRequest httpRequest) throws HttpException, IOException;
        void sendRequestEntity(HttpEntityEnclosingRequest httpEntityEnclosingRequest) throws HttpException, IOException;
        HttpResponse receiveResponseHeader() throws HttpException, IOException;
        void receiveResponseEntity(HttpResponse httpResponse) throws HttpException, IOException;
    }

    @Delegate(excludes=ConnectionIO.class, types={ManagedHttpClientConnection.class, HttpConnection.class})
    private final ManagedHttpClientConnection delegate;

    private final RestexCaptureTarget target;

    public RestexClientConnection(ManagedHttpClientConnection connection, RestexCaptureTarget target) {
        this.delegate = connection;
        this.target = target;
    }

    @Override
    public void sendRequestHeader(HttpRequest httpRequest) throws HttpException, IOException {
        target.requestUri(httpRequest.getRequestLine().getMethod(), httpRequest.getRequestLine().getUri());
        final Header[] headers = httpRequest.getAllHeaders();
        for (Header header : headers) {
            target.requestHeader(header.getName(), header.getValue());
        }
        delegate.sendRequestHeader(httpRequest);
    }

    @Override
    public void sendRequestEntity(HttpEntityEnclosingRequest httpEntityEnclosingRequest) throws HttpException, IOException {

        // Read the entire request into a String
        StringWriter writer = new StringWriter();
        final HttpEntity entity = httpEntityEnclosingRequest.getEntity();
        final String entityData;
        if (entity != null && entity.getContent() != null) {
            @Cleanup final InputStreamReader in = new InputStreamReader(entity.getContent());
            IOUtils.copyLarge(in, writer);
            entityData = writer.toString();
        } else {
            entityData = "";
        }
        target.requestEntity(entityData);

        // Re-create the entity since we already read the entire InputStream
        httpEntityEnclosingRequest.setEntity(buildEntity(entityData));

        delegate.sendRequestEntity(httpEntityEnclosingRequest);
    }

    @Override
    public HttpResponse receiveResponseHeader() throws HttpException, IOException {
        final HttpResponse httpResponse = delegate.receiveResponseHeader();

        target.responseStatus(httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase(), httpResponse.getStatusLine().getProtocolVersion().toString());

        final Header[] headers = httpResponse.getAllHeaders();
        for (Header header : headers) {
            target.responseHeader(header.getName(), header.getValue());
        }
        return httpResponse;
    }

    @Override
    public void receiveResponseEntity(HttpResponse response) throws HttpException, IOException {

        delegate.receiveResponseEntity(response);

        // Read the entire response into a String
        StringWriter writer = new StringWriter();
        final HttpEntity entity = response.getEntity();
        final String entityData;
        if (entity != null && entity.getContent() != null) {
            @Cleanup final InputStreamReader in = new InputStreamReader(entity.getContent());
            IOUtils.copyLarge(in, writer);
            entityData = writer.toString();
        } else {
            entityData = null;
        }
        target.responseEntity(entityData);

        // Re-create the entity since we already read the entire InputStream
        response.setEntity(buildEntity(entityData));
    }

    private BasicHttpEntity buildEntity(String entityData) {
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentLength(entityData == null ? 0 : entityData.length());
        entity.setContent(new ByteArrayInputStream(entityData == null ? "".getBytes() : entityData.getBytes()));
        return entity;
    }

}
