package com.sh02l.reader.epub;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

import javax.xml.parsers.DocumentBuilderFactory;

/** Bounded EPUB package model. Never extracts ZIP entries to the filesystem. */
public final class EpubArchive implements AutoCloseable {
    public static final int CONTENT_LIMIT = 4 * 1024 * 1024, RESOURCE_LIMIT = 8 * 1024 * 1024;

    public static final class Resource {
        public final String path, fragment;

        Resource(String path, String fragment) {
            this.path = path;
            this.fragment = fragment == null ? "" : fragment;
        }
    }

    public static final class Part {
        public final String title, href, fragment;

        Part(String title, Resource resource) {
            this.title = title;
            href = resource.path;
            fragment = resource.fragment;
        }
    }

    private final ZipFile zip;
    private final Map<String, ZipEntry> entries = new HashMap<>();
    public final List<String> spine;
    public final List<Part> navigation;
    public final String title, cover;

    public EpubArchive(File file) throws IOException {
        zip = new ZipFile(file);
        try {
            long total = 0;
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                String name = entry.getName();
                if (name.startsWith("/")
                        || name.contains("\\")
                        || name.indexOf('\u001f') >= 0
                        || Arrays.asList(name.split("/")).contains(".."))
                    throw new IOException("Unsafe EPUB archive path");
                if (entry.isDirectory()) continue;
                if (entries.put(name, entry) != null
                        || entries.size() > 20000
                        || entry.getSize() < 0)
                    throw new IOException("Invalid EPUB archive entries");
                total += entry.getSize();
                if (total > 256L * 1024 * 1024)
                    throw new IOException("EPUB expanded content exceeds 256 MiB");
            }
            if (entries.containsKey("META-INF/encryption.xml"))
                throw new IOException("Encrypted EPUB resources are unsupported");
            Document container = xml(read("META-INF/container.xml", 1024 * 1024));
            NodeList roots = container.getElementsByTagNameNS("*", "rootfile");
            if (roots.getLength() == 0) throw new IOException("Missing EPUB package");
            String packagePath =
                    resolve("", ((Element) roots.item(0)).getAttribute("full-path")).path;
            Document packageDoc = xml(read(packagePath, 1024 * 1024));
            title = firstText(packageDoc, "title", "未命名 EPUB");
            Map<String, String> items = new HashMap<>();
            String nav = null, ncx = null, coverPath = null, coverId = null;
            NodeList metadata = packageDoc.getElementsByTagNameNS("*", "meta");
            for (int i = 0; i < metadata.getLength(); i++) {
                Element meta = (Element) metadata.item(i);
                if ("cover".equals(meta.getAttribute("name")))
                    coverId = meta.getAttribute("content");
                if ("rendition:layout".equals(meta.getAttribute("property"))
                        && "pre-paginated".equals(meta.getTextContent().trim()))
                    throw new IOException("Fixed-layout EPUB is unsupported");
            }
            NodeList manifest = packageDoc.getElementsByTagNameNS("*", "item");
            for (int i = 0; i < manifest.getLength(); i++) {
                Element item = (Element) manifest.item(i);
                String path = resolve(packagePath, item.getAttribute("href")).path;
                items.put(item.getAttribute("id"), path);
                List<String> properties =
                        Arrays.asList(item.getAttribute("properties").split("\\s+"));
                if (properties.contains("nav")) nav = path;
                if (properties.contains("cover-image")) coverPath = path;
                if ("application/x-dtbncx+xml".equals(item.getAttribute("media-type"))) ncx = path;
            }
            if (coverPath == null && coverId != null) coverPath = items.get(coverId);
            cover = coverPath;
            List<String> readingOrder = new ArrayList<>();
            NodeList refs = packageDoc.getElementsByTagNameNS("*", "itemref");
            for (int i = 0; i < refs.getLength(); i++) {
                String href = items.get(((Element) refs.item(i)).getAttribute("idref"));
                if (href == null || !entries.containsKey(href))
                    throw new IOException("Missing EPUB spine resource");
                readingOrder.add(href);
            }
            if (readingOrder.isEmpty()) throw new IOException("Empty EPUB spine");
            spine = Collections.unmodifiableList(readingOrder);
            List<Part> toc = new ArrayList<>();
            if (nav != null)
                try {
                    Document doc = xml(read(nav, CONTENT_LIMIT));
                    NodeList navs = doc.getElementsByTagNameNS("*", "nav");
                    Element scope = null;
                    for (int i = 0; i < navs.getLength(); i++) {
                        Element n = (Element) navs.item(i);
                        if (n.getAttributeNS("http://www.idpf.org/2007/ops", "type")
                                .contains("toc")) {
                            scope = n;
                            break;
                        }
                    }
                    NodeList anchors =
                            scope == null
                                    ? doc.getElementsByTagNameNS("*", "a")
                                    : scope.getElementsByTagNameNS("*", "a");
                    for (int i = 0; i < anchors.getLength(); i++) {
                        Element a = (Element) anchors.item(i);
                        addPart(toc, a.getTextContent(), nav, a.getAttribute("href"));
                    }
                } catch (IOException ignored) {
                    /* Use the EPUB2 NCX or reading order when optional navigation is malformed. */
                }
            if (toc.isEmpty() && ncx != null)
                try {
                    Document doc = xml(read(ncx, CONTENT_LIMIT));
                    NodeList points = doc.getElementsByTagNameNS("*", "navPoint");
                    for (int i = 0; i < points.getLength(); i++) {
                        Element point = (Element) points.item(i);
                        NodeList targets = point.getElementsByTagNameNS("*", "content");
                        if (targets.getLength() > 0)
                            addPart(
                                    toc,
                                    firstText(point, "text", "章节"),
                                    ncx,
                                    ((Element) targets.item(0)).getAttribute("src"));
                    }
                } catch (IOException ignored) {
                }
            if (toc.isEmpty())
                for (int i = 0; i < spine.size(); i++)
                    toc.add(new Part("第 " + (i + 1) + " 节", new Resource(spine.get(i), "")));
            navigation = Collections.unmodifiableList(toc);
        } catch (IOException | RuntimeException error) {
            try {
                zip.close();
            } catch (IOException close) {
                error.addSuppressed(close);
            }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Invalid EPUB package", error);
        }
    }

    private void addPart(List<Part> result, String title, String base, String href) {
        try {
            Resource target = resolve(base, href);
            if (entries.containsKey(target.path) && result.size() < 10000)
                result.add(new Part(title.trim().replaceAll("\\s+", " "), target));
        } catch (IOException ignored) {
        }
    }

    public Resource resolve(String base, String href) throws IOException {
        try {
            URI reference = new URI(href.replace(" ", "%20"));
            if (reference.isAbsolute()
                    || reference.getRawAuthority() != null
                    || reference.getQuery() != null)
                throw new IOException("External EPUB resource");
            String path = reference.getPath();
            if (path == null || path.isEmpty()) return new Resource(base, reference.getFragment());
            if (path.startsWith("/")
                    || path.contains("\\")
                    || path.matches("(?s).*[:\\x00-\\x1f].*"))
                throw new IOException("Unsafe EPUB path");
            String joined =
                    (base.contains("/") ? base.substring(0, base.lastIndexOf('/') + 1) : "") + path;
            Deque<String> parts = new ArrayDeque<>();
            for (String part : joined.split("/")) {
                if (part.isEmpty() || part.equals(".")) continue;
                if (part.equals("..")) {
                    if (parts.isEmpty()) throw new IOException("EPUB path outside archive");
                    parts.removeLast();
                } else parts.addLast(part);
            }
            return new Resource(String.join("/", parts), reference.getFragment());
        } catch (java.net.URISyntaxException error) {
            throw new IOException("Invalid EPUB link", error);
        }
    }

    public synchronized byte[] read(String path, int limit) throws IOException {
        ZipEntry entry = entries.get(path);
        if (entry == null || entry.isDirectory())
            throw new IOException("EPUB resource unavailable");
        if (entry.getSize() > limit) throw new IOException("EPUB resource exceeds limit");
        try (InputStream in = zip.getInputStream(entry);
                ByteArrayOutputStream out =
                        new ByteArrayOutputStream((int) Math.min(entry.getSize(), 16384))) {
            byte[] bytes = new byte[8192];
            int count;
            while ((count = in.read(bytes)) != -1) {
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedIOException("EPUB cancelled");
                if (out.size() > limit - count)
                    throw new IOException("EPUB resource exceeds limit");
                out.write(bytes, 0, count);
            }
            return out.toByteArray();
        }
    }

    public String text(String href) throws IOException {
        byte[] bytes = read(href, CONTENT_LIMIT);
        try {
            Document doc = xml(bytes);
            NodeList bodies = doc.getElementsByTagNameNS("*", "body");
            StringBuilder text = new StringBuilder();
            appendText(bodies.getLength() == 0 ? doc.getDocumentElement() : bodies.item(0), text);
            return text.toString();
        } catch (IOException malformed) {
            return new String(bytes, StandardCharsets.UTF_8)
                    .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                    .replaceAll("(?s)<[^>]*>", " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
        }
    }

    public List<Part> links(String href) throws IOException {
        List<Part> links = new ArrayList<>();
        try {
            Document doc = xml(read(href, CONTENT_LIMIT));
            NodeList nodes = doc.getElementsByTagNameNS("*", "a");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element a = (Element) nodes.item(i);
                addPart(links, a.getTextContent(), href, a.getAttribute("href"));
            }
        } catch (IOException ignored) {
        }
        return links;
    }

    private static void appendText(Node node, StringBuilder out) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            out.append(node.getNodeValue());
            return;
        }
        String name = node.getLocalName();
        if ("script".equalsIgnoreCase(name)
                || "style".equalsIgnoreCase(name)
                || "noscript".equalsIgnoreCase(name)) return;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
            appendText(child, out);
    }

    private static Document xml(byte[] bytes) throws IOException {
        try {
            // Android 8's Harmony factory lacks the SAX feature switches. Never accept
            // publication-defined entities; the resolver also prevents external DTD I/O.
            String declarations =
                    new String(bytes, StandardCharsets.UTF_8)
                            .replace("\u0000", "")
                            .toUpperCase(Locale.ROOT);
            if (declarations.contains("<!ENTITY"))
                throw new IOException("EPUB entity declarations are unsupported");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            for (String feature :
                    new String[] {
                        "http://xml.org/sax/features/external-general-entities",
                        "http://xml.org/sax/features/external-parameter-entities"
                    }) {
                try {
                    factory.setFeature(feature, false);
                } catch (javax.xml.parsers.ParserConfigurationException unsupported) {
                    /* Entity rejection + resolver remain mandatory on Harmony. */
                }
            }
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(
                    (publicId, systemId) -> new InputSource(new StringReader("")));
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (Exception error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Invalid EPUB XML", error);
        }
    }

    private static String firstText(Node node, String name, String fallback) {
        NodeList nodes =
                node instanceof Document
                        ? ((Document) node).getElementsByTagNameNS("*", name)
                        : ((Element) node).getElementsByTagNameNS("*", name);
        return nodes.getLength() == 0 ? fallback : nodes.item(0).getTextContent().trim();
    }

    @Override
    public void close() {
        try {
            zip.close();
        } catch (IOException ignored) {
        }
    }
}
