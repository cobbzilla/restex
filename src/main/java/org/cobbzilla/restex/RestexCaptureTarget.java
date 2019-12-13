package org.cobbzilla.restex;

import java.io.IOException;

public interface RestexCaptureTarget {

    public void requestUri (String method, String uri);

    public void requestHeader(String name, String value);

    public void requestEntity(String entityData);

    public void responseStatus(int statusCode, String reasonPhrase, String protocolVersion);

    public void responseHeader(String name, String value);

    public void responseEntity(String entityData);

    public void commit() throws IOException;

    public void setBinaryRequest(String hint);
    public void setBinaryResponse(String hint);
}
