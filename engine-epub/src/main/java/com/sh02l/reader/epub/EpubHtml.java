package com.sh02l.reader.epub;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/** Shared character decoding; the display copy also fixes the old WebView's viewport. */
final class EpubHtml {
    private static final Pattern ENCODING =
            Pattern.compile("(?i)encoding\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern ENTITY = Pattern.compile("&([A-Za-z][A-Za-z0-9]+);");
    private static final Pattern META = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern VIEWPORT =
            Pattern.compile("(?i)\\bname\\s*=\\s*(?:\"viewport\"|'viewport'|viewport\\b)");
    private static final Map<String, String> ENTITIES = entities();

    private static Map<String, String> entities() {
        Properties table = new Properties();
        try (InputStream in = EpubHtml.class.getResourceAsStream("/epub-entities.properties")) {
            if (in == null) throw new IOException("Missing EPUB character reference table");
            table.load(in);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        Map<String, String> result = new HashMap<>();
        for (String name : table.stringPropertyNames()) {
            StringBuilder reference = new StringBuilder();
            for (String point : table.getProperty(name).split(","))
                reference.append("&#x").append(point).append(';');
            result.put(name, reference.toString());
        }
        return Collections.unmodifiableMap(result);
    }

    static String source(byte[] bytes) throws IOException {
        String prefix =
                new String(bytes, 0, Math.min(bytes.length, 512), StandardCharsets.ISO_8859_1)
                        .replace("\u0000", "");
        Charset charset = StandardCharsets.UTF_8;
        if (bytes.length >= 2) {
            if ((bytes[0] & 255) == 255 && (bytes[1] & 255) == 254
                    || bytes[0] == '<' && bytes[1] == 0) charset = StandardCharsets.UTF_16LE;
            else if ((bytes[0] & 255) == 254 && (bytes[1] & 255) == 255
                    || bytes[0] == 0 && bytes[1] == '<') charset = StandardCharsets.UTF_16BE;
            else {
                Matcher declared = ENCODING.matcher(prefix);
                if (prefix.contains("<?xml") && declared.find())
                    try {
                        charset = Charset.forName(declared.group(1));
                    } catch (IllegalArgumentException bad) {
                        throw new IOException("Unsupported EPUB character encoding", bad);
                    }
            }
        }
        String text = new String(bytes, charset);
        if (text.startsWith("\ufeff")) text = text.substring(1);
        if (text.toUpperCase(Locale.ROOT).contains("<!ENTITY"))
            throw new IOException("EPUB entity declarations are unsupported");
        return text.replaceFirst("(?is)^\\s*<\\?xml.*?\\?>", "");
    }

    static String forXml(byte[] bytes) throws IOException {
        Matcher names = ENTITY.matcher(source(bytes));
        StringBuffer normalized = new StringBuffer();
        while (names.find()) {
            String reference = ENTITIES.get(names.group(1));
            names.appendReplacement(
                    normalized,
                    Matcher.quoteReplacement(reference == null ? names.group() : reference));
        }
        names.appendTail(normalized);
        return normalized.toString();
    }

    static byte[] forDisplay(byte[] bytes) throws IOException {
        String text = source(bytes).replaceAll("(?s)<!--.*?-->", "");
        Matcher tags = META.matcher(text);
        StringBuffer clean = new StringBuffer();
        while (tags.find())
            tags.appendReplacement(
                    clean,
                    Matcher.quoteReplacement(
                            VIEWPORT.matcher(tags.group()).find() ? "" : tags.group()));
        tags.appendTail(clean);
        text = clean.toString();
        String viewport =
                "<meta name=\"viewport\""
                    + " content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\"/>";
        Matcher head = Pattern.compile("(?i)<head\\b[^>]*>").matcher(text);
        if (head.find())
            text = text.substring(0, head.end()) + viewport + text.substring(head.end());
        else {
            Matcher html = Pattern.compile("(?i)<html\\b[^>]*>").matcher(text);
            int at = html.find() ? html.end() : 0;
            text = text.substring(0, at) + "<head>" + viewport + "</head>" + text.substring(at);
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
