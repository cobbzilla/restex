package org.cobbzilla.restex.targets;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.cobbzilla.restex.RestexCaptureTarget;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ToString(of={"requestMethod", "requestUri", "requestEntity", "note"})
public class SimpleCaptureTarget implements RestexCaptureTarget {

    @Getter @Setter private int statusCode;
    @Getter @Setter private String reasonPhrase;
    @Getter @Setter private String protocolVersion;

    @Getter @Setter private String requestMethod;
    @Getter @Setter private String requestUri;
    @Getter @Setter private Map<String, String> requestHeaders = new LinkedHashMap<>();
    @Getter @Setter private Map<String, String> responseHeaders = new LinkedHashMap<>();

    @Getter @Setter private String requestEntity;
    @Getter @Setter private String responseEntity;

    @Getter @Setter private String binaryRequest;
    @Getter @Setter private String binaryResponse;

    @Getter @Setter private String note;
    public void appendNote (String n) { if (note == null) { note = n ; } else { note += "\n" + n; } }

    public List<String> getRequestHeaderList () { return buildHeaderList(requestHeaders); }
    public List<String> getResponseHeaderList () { return buildHeaderList(responseHeaders); }

    public String getResponseLine () { return statusCode + " " + (reasonPhrase == null ? "" : reasonPhrase) + " " + protocolVersion; }

    private List<String> buildHeaderList(Map<String, String> headers) {
        List<String> headerList = new ArrayList<>();
        for (String header : headers.keySet()) {
            headerList.add(header + ": " + headers.get(header));
        }
        return headerList;
    }

    @Override public void requestUri (String method, String uri) {
        setRequestMethod(method);
        setRequestUri(uri);
    }

    @Override public void requestHeader(String name, String value) { requestHeaders.put(name, value); }

    @Override public void requestEntity(String entityData) {
        if (binaryRequest != null) {
            requestEntity = "Binary request: " + binaryRequest;
        } else {
            requestEntity = entityData;
        }
    }

    @Override public void responseStatus(int statusCode, String reasonPhrase, String protocolVersion) {
        this.statusCode = statusCode; this.reasonPhrase = reasonPhrase; this.protocolVersion = protocolVersion;
    }

    @Override public void responseHeader(String name, String value) { responseHeaders.put(name, value); }

    @Override public void responseEntity(String entityData) {
        if (binaryResponse != null) {
            responseEntity = "Binary response: " + binaryResponse;
        } else {
            responseEntity = entityData;
        }
    }

    @Override public void commit() throws IOException {}

    public void reset () {
        requestUri = null;
        requestHeaders = new LinkedHashMap<>();
        requestEntity = null;

        statusCode = -1;
        reasonPhrase = null;
        protocolVersion = null;

        responseHeaders = new LinkedHashMap<>();
        responseEntity = null;
    }

}
