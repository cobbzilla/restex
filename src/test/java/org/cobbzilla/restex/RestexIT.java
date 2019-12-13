package org.cobbzilla.restex;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.cobbzilla.restex.targets.SimpleCaptureTarget;
import org.cobbzilla.restex.targets.TemplateCaptureTarget;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Slf4j
public class RestexIT {

    public static final int TEST_PORT = 19091;

    private Server server;

    class RestexTestHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            // do nothing
            log.info("handle called.");
            response.setStatus(200);
            final Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                response.addHeader(headerName, request.getHeader(headerName));
            }
            response.getWriter().write("foo");
            response.getWriter().flush();
            response.setContentLength("foo".length());
        }
    }

    @Before
    public void setUp () throws Exception {
        server = new Server(TEST_PORT);
        server.setHandler(new RestexTestHandler());
        server.start();
    }

    @After public void tearDown () throws Exception { server.stop(); }

    @Test
    public void testSimpleCapture () throws Exception {
        RestexCaptureTarget target = new SimpleCaptureTarget();
        HttpClient httpClient = new RestexClientConnectionManager(target).getHttpClient();
        HttpGet httpGet = new HttpGet("http://127.0.0.1:"+TEST_PORT+"/test");
        String requestHeaderName1 = "foo-"+System.currentTimeMillis();
        String requestHeaderValue1 = "bar-"+System.currentTimeMillis();
        httpGet.addHeader(new BasicHeader(requestHeaderName1, requestHeaderValue1));

        final HttpResponse response = httpClient.execute(httpGet);
        final Map<String, String> responseHeaderMap = buildResponseHeaderMap(response);
        assertTrue("didn't find response header", responseHeaderMap.containsKey(requestHeaderName1));
        assertEquals("wrong request header value for "+requestHeaderName1, requestHeaderValue1, responseHeaderMap.get(requestHeaderName1));
    }

    @Test
    public void testTemplateCapture () throws Exception {

        File tempDir = createTempDir(new File(System.getProperty("java.io.tmpdir")), getClass().getSimpleName());
        log.info("Writing to tempDir="+tempDir.getAbsolutePath());

        TemplateCaptureTarget target = new TemplateCaptureTarget(tempDir, "testTemplateIndex", "testTemplateIndexMore", "testTemplateHeader", "testTemplateFooter", "testTemplateEntry");
        HttpClient httpClient = new RestexClientConnectionManager(target).getHttpClient();

        String header1;
        String value1;
        HttpGet httpGet;
        HttpResponse response;

        target.startRecording("test1", "a single request of /test goes like this");
        httpGet = new HttpGet("http://127.0.0.1:"+TEST_PORT+"/test");
        header1 = "foo-"+System.currentTimeMillis();
        value1 = "bar-"+System.currentTimeMillis();
        httpGet.addHeader(new BasicHeader(header1, value1));
        response = httpClient.execute(httpGet);
        httpGet.releaseConnection();
        target.commit();

        target.startRecording("test2", "a request of /test and then /test2 goes like this");
        httpGet = new HttpGet("http://127.0.0.1:"+TEST_PORT+"/test");
        header1 = "foo2-"+System.currentTimeMillis();
        value1 = "bar2-"+System.currentTimeMillis();
        httpGet.addHeader(new BasicHeader(header1, value1));
        response = httpClient.execute(httpGet);
        httpGet.releaseConnection();

        httpGet = new HttpGet("http://127.0.0.1:"+TEST_PORT+"/test2");
        header1 = "foo-"+System.currentTimeMillis();
        value1 = "bar-"+System.currentTimeMillis();
        httpGet.addHeader(new BasicHeader(header1, value1));
        response = httpClient.execute(httpGet);
        httpGet.releaseConnection();

        target.close();
    }

    private Map<String, String> buildResponseHeaderMap(HttpResponse response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : response.getAllHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        return headers;
    }

    public static File createTempDir(File parentDir, String prefix) throws IOException {
        Path parent = FileSystems.getDefault().getPath(parentDir.getAbsolutePath());
        return new File(Files.createTempDirectory(parent, prefix).toAbsolutePath().toString());
    }

}
