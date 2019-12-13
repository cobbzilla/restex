package org.cobbzilla.restex.targets;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.restex.RestexCaptureTarget;

import java.io.*;
import java.util.*;

@Slf4j
public class TemplateCaptureTarget implements RestexCaptureTarget {

    public static final String SCOPE_HTTP = "http";
    public static final String SCOPE_ANCHOR = "anchor";
    public static final String SCOPE_FILES = "files";
    public static final String HTML_SUFFIX = ".html";

    public static final String FOOTER_START = "@@FOOTER@@";
    public static final String INDEX_INSERTION_POINT = "@@MORE-INDEX-FILES@@";

    public static final String DEFAULT_INDEX_TEMPLATE = "defaultIndex";
    public static final String DEFAULT_INDEX_MORE_TEMPLATE = "defaultIndexMore";
    public static final String DEFAULT_HEADER_TEMPLATE = "defaultHeader";
    public static final String DEFAULT_FOOTER_TEMPLATE = "defaultFooter";
    public static final String DEFAULT_ENTRY_TEMPLATE = "defaultEntry";

    @Getter private File baseDir;

    private final Template indexTemplate;
    private final Template indexMoreTemplate;
    private final Template headerTemplate;
    private final Template footerTemplate;
    private final Template entryTemplate;

    private SortedSet<ContextFile> contextFiles = new TreeSet<>();
    private Map<String, ContextFile> contextFileMap = new HashMap<>();
    private final Set<File> filesOpen = new HashSet<>();

    // current state, initialized in startRecording, reset in commit
    private SimpleCaptureTarget currentCapture = null;
    @Getter private final List<SimpleCaptureTarget> captures = new ArrayList<>();
    private boolean recording = false;
    @Getter private String context = "";
    @Getter private String comment = "";

    public TemplateCaptureTarget (String baseDir) {
        this(new File(baseDir), DEFAULT_INDEX_TEMPLATE, DEFAULT_INDEX_MORE_TEMPLATE, DEFAULT_HEADER_TEMPLATE, DEFAULT_FOOTER_TEMPLATE, DEFAULT_ENTRY_TEMPLATE);
    }

    public TemplateCaptureTarget (File baseDir) {
        this(baseDir, DEFAULT_INDEX_TEMPLATE, DEFAULT_INDEX_MORE_TEMPLATE, DEFAULT_HEADER_TEMPLATE, DEFAULT_FOOTER_TEMPLATE, DEFAULT_ENTRY_TEMPLATE);
    }

    public TemplateCaptureTarget (File baseDir,
                                  String indexTemplate,
                                  String indexMoreTemplate,
                                  String headerTemplate,
                                  String footerTemplate,
                                  String entryTemplate) {
        this.baseDir = baseDir;
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw new IllegalArgumentException("baseDir does not exist and could not be created: "+baseDir.getAbsolutePath());
        }

        final Handlebars handlebars = new Handlebars(new ClassPathTemplateLoader("/"));
        handlebars.registerHelper("nl2br", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                return src == null || src.toString().isEmpty() ? "" : new Handlebars.SafeString(src.toString().replace("\n", "<br/>"));
            }
        });

        this.indexTemplate = compileOrDie(indexTemplate, handlebars);
        this.indexMoreTemplate = compileOrDie(indexMoreTemplate, handlebars);
        this.headerTemplate = compileOrDie(headerTemplate, handlebars);
        this.footerTemplate = compileOrDie(footerTemplate, handlebars);
        this.entryTemplate = compileOrDie(entryTemplate, handlebars);
    }

    public Template compileOrDie(String template, Handlebars handlebars) {
        try {
            return handlebars.compile(template);
        } catch (IOException e) {
            throw new IllegalStateException("Error compiling template '"+ template+"': "+e, e);
        }
    }

    public synchronized void startRecording (String context, String comment) {
        if (recording) {
            log.warn("startRecording: cannot start "+context+"/"+comment+", already recording "+this.context+"/"+this.comment);
            try {
                if (!captures.isEmpty()) commit();
            } catch (IOException e) {
                log.error("startRecording: error committing docs: "+e);
            } finally {
                reset();
            }
        }
        this.context = context;
        this.comment = comment;
        currentCapture = new SimpleCaptureTarget();
        recording = true;
    }

    @Override public void requestUri(String method, String uri) {
        if (currentCapture != null) currentCapture.requestUri(method, uri);
    }

    @Override public void requestHeader(String name, String value) { if (recording) currentCapture.requestHeader(name, value); }
    @Override public void requestEntity(String entityData) { if (recording) currentCapture.requestEntity(entityData); }
    @Override public void responseStatus(int statusCode, String reasonPhrase, String protocolVersion) {
        if (recording) currentCapture.responseStatus(statusCode, reasonPhrase, protocolVersion);
    }
    @Override public void responseHeader(String name, String value) { if (recording) currentCapture.responseHeader(name, value); }
    @Override public synchronized void responseEntity(String entityData) {
        if (recording) {
            currentCapture.responseEntity(entityData);
            captures.add(currentCapture);
            currentCapture = new SimpleCaptureTarget();
        }
    }

    public void addNote (String note) { if (recording) currentCapture.appendNote(note); }

    @Override public void setBinaryRequest (String type) { if (recording) currentCapture.setBinaryRequest(type); }
    @Override public void setBinaryResponse (String type) { if (recording) currentCapture.setBinaryResponse(type); }

    public synchronized void commit () throws IOException {

        final String fileBaseName = context.replaceAll("[^A-Za-z0-9]", "_");
        if (fileBaseName == null || fileBaseName.length() == 0) {
            log.warn("No context name set (not committing). currentCapture="+currentCapture);
            return;
        }
        final String uriFileName = fileBaseName + HTML_SUFFIX;

        ContextFile contextFile = contextFileMap.get(context);
        if (contextFile == null) {
            contextFile = new ContextFile(context, uriFileName);
            contextFileMap.put(context, contextFile);
        }
        contextFiles.add(contextFile);

        String anchor = comment.replaceAll("[^A-Za-z0-9]", "_");
        contextFile.add(new ContextExample(anchor, comment));

        final File uriFile = new File(baseDir, uriFileName);
        final boolean exists = uriFile.exists();

        synchronized (filesOpen) {
            if (!exists) {
                // first time writing to the file, so write the header
                filesOpen.add(uriFile);
                try (FileWriter writer = new FileWriter(uriFile)) {
                    render(headerTemplate, writer);
                }
            } else {
                // file exists -- have we written to it yet?
                if (!filesOpen.contains(uriFile)) {
                    // file exists but we have not written to it, so rewrite the file without the footer
                    removeFooter(uriFile);
                }
            }
        }

        try (FileWriter writer = new FileWriter(uriFile, true)) {
            // append the entry
            renderEntry(entryTemplate, writer, anchor);
        }

        reset();
    }

    public synchronized void reset() {
        recording = false;
        context = "";
        comment = "";
        captures.clear();
        currentCapture = null;
    }

    public synchronized void close () throws IOException {
        if (recording) commit();
        for (File f : filesOpen) {
            try (FileWriter writer = new FileWriter(f, true)) {
                render(footerTemplate, writer);
            }
        }
        final File indexFile = new File(baseDir, "index.html");
        if (!indexFile.exists()) {
            try (FileWriter writer = new FileWriter(indexFile)) {
                renderIndex(indexTemplate, writer);
            }
        } else {
            StringWriter writer = new StringWriter();
            renderIndex(indexMoreTemplate, writer);
            replaceInFile(indexFile, INDEX_INSERTION_POINT, writer.toString());
        }
        filesOpen.clear();
        contextFiles.clear();
        contextFileMap.clear();
    }

    protected synchronized void apply(final Template template, Writer writer, Map<String, Object> scope) {
        synchronized (captures) {
            try {
                template.apply(scope, writer);
            } catch (IOException e) {
                throw new IllegalStateException("Error applying template '" + template.filename() + "': " + e, e);
            }
        }
    }

    protected void renderEntry(Template template, Writer writer, String anchor) {
        Map<String, Object> scope = new HashMap<>();
        scope.put(SCOPE_HTTP, this);
        scope.put(SCOPE_ANCHOR, anchor);
        apply(template, writer, scope);
    }

    protected void render(Template template, Writer writer) {
        Map<String, Object> scope = new HashMap<>();
        scope.put(SCOPE_HTTP, this);
        apply(template, writer, scope);
    }

    protected void renderIndex(Template template, Writer writer) {
        Map<String, Object> scope = new HashMap<>();
        scope.put(SCOPE_FILES, contextFiles);
        apply(template, writer, scope);
    }

    private void removeFooter(File uriFile) throws IOException {
        File temp = File.createTempFile(getClass().getSimpleName(), HTML_SUFFIX, uriFile.getParentFile());
        try (BufferedReader reader = new BufferedReader(new FileReader(uriFile))) {
            try (FileWriter writer = new FileWriter(temp)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(FOOTER_START)) break;
                    writer.write(line + "\n");
                }
            }
        }
        if (!temp.renameTo(uriFile)) {
            throw new IllegalStateException("Error rewriting footer in file: "+uriFile.getAbsolutePath());
        }
    }

    private void replaceInFile(File file, String insertionPoint, String data) throws IOException {
        File temp = File.createTempFile(getClass().getSimpleName(), HTML_SUFFIX, file.getParentFile());
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            try (FileWriter writer = new FileWriter(temp)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(insertionPoint)) {
                        writer.write(data);
                    }
                    writer.write(line + "\n");
                }
            }
        }
        if (!temp.renameTo(file)) {
            throw new IllegalStateException("Error rewriting file: "+file.getAbsolutePath());
        }
    }

    @AllArgsConstructor @EqualsAndHashCode(of="context")
    class ContextFile implements Comparable {

        @Getter @Setter public String context;
        @Getter @Setter public String fsPath;

        @Getter public final List<ContextExample> examples = new ArrayList<>();
        public void add(ContextExample contextExample) { examples.add(contextExample); }

        @Override
        public int compareTo(Object o) {
            return (o instanceof ContextFile) ? context.compareTo(((ContextFile) o).getContext()) : 0;
        }
    }

    @AllArgsConstructor
    class ContextExample {
        @Getter @Setter public String anchor;
        @Getter @Setter public String description;
    }
}
