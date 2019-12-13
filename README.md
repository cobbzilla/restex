restex
======

Capture your REST integration tests and present them as examples for API consumers.

Use this in your JUnit-style test cases to capture request/response conversations with context.

    protected static TemplateCaptureTarget apiDocs = new TemplateCaptureTarget("target/api-examples");

    @Override
    protected HttpClient getHttpClient() {
        return new DefaultHttpClient(new RestexClientConnectionManager(apiDocs));
    }

    @After
    public void commitDocCapture () throws Exception {
        apiDocs.commit();
    }

    @AfterClass
    public static void finalizeDocCapture () throws Exception {
        apiDocs.close();
    }

    @Test
    public void someTest() throws Exception {
        apiDocs.startRecording("some class of operations", "this particular operation");
        apiDocs.addNote("going to do step #1");
        ... do step 1, something that will use the HttpClient from getHttpClient() ...
        apiDocs.addNote("going to do step #2");
        ... do step 2, something that will use the HttpClient from getHttpClient() ...
    }

    @Test
    public void anotherTest() throws Exception {
        apiDocs.startRecording("some class of operations", "another particular operation");
        apiDocs.addNote("going to do step #1");
        ... do step 1, something that will use the HttpClient from getHttpClient() ...
        apiDocs.addNote("going to do step #2");
        ... do step 2, something that will use the HttpClient from getHttpClient() ...
    }
