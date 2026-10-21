package com.sh02l.reader.epub;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.webkit.*;

import com.sh02l.reader.android.*;
import com.sh02l.reader.core.*;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Platform HTML/CSS renderer with archive-only resources and no publication script access. */
public final class EpubReadingSurface implements ReadingSurface {
    private final WebView view;
    private final String host = "book-" + UUID.randomUUID().toString() + ".invalid";
    private final String bootstrap;
    private EpubDocumentSession document;
    private ReadingStyle style;
    private Listener listener;
    private DocumentLocation wanted, current, end;
    private String path = "", visibleText = "";
    private long epoch;
    private boolean closed, ready, loading, pageLoaded;
    private int pending;

    @SuppressLint("SetJavaScriptEnabled")
    public EpubReadingSurface(Context context) throws IOException {
        try (InputStream in = context.getAssets().open("reader.js");
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            bootstrap = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        view =
                new WebView(context) {
                    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                        super.onSizeChanged(w, h, oldw, oldh);
                        if (w > 0 && h > 0 && !closed)
                            post(
                                    () -> {
                                        if (pageLoaded) reflow(wanted);
                                    });
                    }
                };
        view.setTag("reading-content");
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setDefaultFocusHighlightEnabled(false);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setTextZoom(100); // ReadingStyle already includes the user's system font scale.
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setSupportZoom(false);
        settings.setBlockNetworkLoads(true);
        settings.setDomStorageEnabled(false);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setVerticalScrollBarEnabled(false);
        view.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(
                            WebView web, WebResourceRequest request) {
                        return resource(request.getUrl());
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView web, WebResourceRequest request) {
                        Uri uri = request.getUrl();
                        // Same-document fragment clicks can scroll Chromium without a normal
                        // navigation callback. The trusted reader script forwards them explicitly.
                        if ("reader-nav".equals(uri.getScheme())) {
                            if (!host.equals(uri.getHost())) return true;
                            String target = uri.getQueryParameter("target");
                            if (target == null) return true;
                            uri = Uri.parse(target);
                        }
                        if (!"https".equals(uri.getScheme()) || !host.equals(uri.getHost()))
                            return true;
                        try {
                            EpubArchive.Resource resource =
                                    document.archive.resolve(
                                            "",
                                            uri.getPath().substring(1)
                                                    + (uri.getFragment() == null
                                                            ? ""
                                                            : "#" + uri.getFragment()));
                            listener.onNavigate(
                                    EpubDocumentSession.location(
                                            resource.path, 0, resource.fragment));
                        } catch (Exception error) {
                            listener.onError(error);
                        }
                        return true;
                    }

                    @Override
                    public void onPageFinished(WebView web, String url) {
                        if (!closed && loading && !pageLoaded && url.equals(view.getUrl())) {
                            pageLoaded = true;
                            reflow(wanted);
                        }
                    }

                    @Override
                    public void onReceivedError(
                            WebView web, WebResourceRequest request, WebResourceError error) {
                        if (request.isForMainFrame() && !closed && listener != null) {
                            loading = false;
                            listener.onError(
                                    new IOException("EPUB display: " + error.getDescription()));
                        }
                    }
                });
    }

    public View view() {
        return view;
    }

    public boolean ready() {
        return ready && !loading && !closed;
    }

    public String text() {
        return visibleText;
    }

    public DocumentLocation endLocation() {
        return end == null ? wanted : end;
    }

    public void open(
            DocumentSession document,
            DocumentLocation location,
            ReadingStyle style,
            Listener listener) {
        this.document = (EpubDocumentSession) document;
        this.style = style;
        this.listener = listener;
        seek(location);
    }

    public void apply(ReadingStyle next) {
        if (next.equals(style)) return;
        style = next;
        if (pageLoaded) reflow(wanted);
    }

    public void seek(DocumentLocation location) {
        if (closed) return;
        try {
            String[] parts = EpubDocumentSession.parts(location);
            document.archive.resolve("", parts[0]);
            wanted = location;
            if (!parts[0].equals(path) || !pageLoaded) {
                path = parts[0];
                loading = true;
                ready = false;
                pageLoaded = false;
                epoch++;
                view.setAlpha(0f);
                final long request = epoch;
                view.loadUrl("https://" + host + "/" + Uri.encode(path, "/"));
                view.postDelayed(
                        () -> {
                            if (!closed && request == epoch && !pageLoaded) {
                                pageLoaded = true;
                                view.stopLoading();
                                reflow(wanted);
                            }
                        },
                        4000);
            } else {
                loading = true;
                evaluate(
                        "window.__reader.seek(" + parts[1] + "," + JSONObject.quote(parts[2]) + ")",
                        ++epoch);
            }
        } catch (Exception error) {
            listener.onError(error);
        }
    }

    private void reflow(DocumentLocation location) {
        if (closed
                || document == null
                || location == null
                || view.getWidth() < 1
                || view.getHeight() < 1) return;
        wanted = location;
        loading = true;
        ready = false;
        view.setAlpha(0f);
        long token = ++epoch;
        try {
            float density = view.getResources().getDisplayMetrics().density;
            JSONObject css = new JSONObject();
            css.put("size", style.textSize / density);
            css.put("lineHeight", (style.textSize * 1.3f + style.lineExtra) / density);
            css.put("foreground", String.format(Locale.ROOT, "#%06x", style.foreground & 0xffffff));
            css.put("background", String.format(Locale.ROOT, "#%06x", style.background & 0xffffff));
            css.put("family", style.fontPath != null ? "ReaderCustom" : style.fontFamily);
            css.put("custom", style.fontPath != null);
            css.put("css", readerCss());
            String[] parts = EpubDocumentSession.parts(location);
            view.setBackgroundColor(style.background);
            String script =
                    bootstrap
                            + ";window.__reader.configure("
                            + css
                            + ");window.__reader.seek("
                            + parts[1]
                            + ","
                            + JSONObject.quote(parts[2])
                            + ")";
            evaluate(script, token);
        } catch (Exception error) {
            loading = false;
            listener.onError(error);
        }
    }

    public void move(int direction) {
        if (closed) return;
        pending = Math.max(-32, Math.min(32, pending + direction));
        pump();
    }

    private void pump() {
        if (!ready() || pending == 0) return;
        int direction = pending > 0 ? 1 : -1;
        pending -= direction;
        loading = true;
        evaluate("window.__reader.move(" + direction + ")", ++epoch, direction);
    }

    private void evaluate(String script, long token) {
        evaluate(script, token, 0);
    }

    private void evaluate(String script, long token, int direction) {
        evaluate(script, token, direction, 0);
    }

    private void evaluate(String script, long token, int direction, int attempt) {
        view.evaluateJavascript(
                script,
                raw -> {
                    if (closed || token != epoch) return;
                    try {
                        if (raw == null || raw.equals("null"))
                            throw new IOException("EPUB renderer not ready");
                        JSONObject result = new JSONObject(raw);
                        if (result.optBoolean("fontsLoading")
                                || result.optBoolean("imagesLoading")) {
                            String[] target = EpubDocumentSession.parts(wanted);
                            String retry =
                                    "window.__reader.seek("
                                            + target[1]
                                            + ","
                                            + JSONObject.quote(target[2])
                                            + ")";
                            if (attempt >= 80) {
                                if (result.optBoolean("fontsLoading"))
                                    throw new IOException("EPUB font loading timeout");
                                evaluate(
                                        "window.__reader.finishImages();" + retry,
                                        token,
                                        direction,
                                        0);
                                return;
                            }
                            view.postDelayed(
                                    () -> {
                                        if (!closed && token == epoch)
                                            evaluate(retry, token, direction, attempt + 1);
                                    },
                                    50);
                            return;
                        }
                        if (result.optBoolean("boundary") && direction != 0) {
                            int index = document.archive.spine.indexOf(path),
                                    next = index + direction;
                            if (next >= 0 && next < document.archive.spine.size()) {
                                seek(
                                        EpubDocumentSession.location(
                                                document.archive.spine.get(next),
                                                0,
                                                direction < 0 ? "@1" : ""));
                                return;
                            }
                        }
                        final DocumentLocation displayed =
                                EpubDocumentSession.location(
                                        path,
                                        result.getInt("offset"),
                                        "@" + result.getDouble("fraction"));
                        final DocumentLocation displayedEnd =
                                EpubDocumentSession.location(path, result.getInt("end"), "");
                        final String displayedText = result.optString("text");
                        view.postVisualStateCallback(
                                token,
                                new WebView.VisualStateCallback() {
                                    @Override
                                    public void onComplete(long requestId) {
                                        if (closed || token != epoch) return;
                                        current = displayed;
                                        wanted = current;
                                        end = displayedEnd;
                                        visibleText = displayedText;
                                        loading = false;
                                        ready = true;
                                        view.setAlpha(1f);
                                        view.setContentDescription("正文\n" + visibleText);
                                        listener.onLocation(
                                                current, document.progressionAt(current));
                                        listener.onReady();
                                        pump();
                                    }
                                });
                    } catch (Exception error) {
                        loading = false;
                        ready = false;
                        listener.onError(error);
                    }
                });
    }

    private String readerCss() {
        float density = view.getResources().getDisplayMetrics().density;
        int width = Math.max(1, Math.round(view.getWidth() / density));
        int height = Math.max(1, Math.round(view.getHeight() / density));
        String background = String.format(Locale.ROOT, "#%06x", style.background & 0xffffff);
        String foreground = String.format(Locale.ROOT, "#%06x", style.foreground & 0xffffff);
        String family = style.fontPath != null ? "ReaderCustom" : style.fontFamily;
        return (style.fontPath != null
                        ? "@font-face{font-family:ReaderCustom;src:url(\"/__reader_font\");}"
                        : "")
                + "html,body{margin:0!important;padding:0!important;background:"
                + background
                + "!important;}"
                + "html{overflow:hidden!important;}body{height:"
                + height
                + "px!important;width:"
                + width
                + "px!important;column-width:"
                + width
                + "px!important;column-gap:0!important;column-fill:auto!important;"
                + "overflow:visible!important;color:"
                + foreground
                + "!important;font-size:"
                + (style.textSize / density)
                + "px!important;line-height:"
                + ((style.textSize * 1.3f + style.lineExtra) / density)
                + "px!important;font-family:"
                + family
                + "!important;}body"
                + " *{color:inherit!important;background-color:transparent!important;font-family:inherit!important;line-height:inherit!important;}p,div,span,li{font-size:1em!important;}p{margin:.5em"
                + " 0!important;}h1,h2,h3,h4,h5,h6{line-height:1.35!important;margin:.5em"
                + " 0!important;}"
                + "h1{font-size:1.4em!important;}h2{font-size:1.25em!important;}h3,h4,h5,h6{font-size:1.1em!important;}"
                + "img,svg{max-width:"
                + width
                + "px!important;max-height:"
                + height
                + "px!important;object-fit:contain;break-inside:avoid;}"
                + "iframe,object,embed,form,video,audio{display:none!important;}";
    }

    private WebResourceResponse resource(Uri uri) {
        try {
            if (closed
                    || document == null
                    || !"https".equals(uri.getScheme())
                    || !host.equals(uri.getHost())) return denied();
            String resourcePath = uri.getPath().substring(1);
            byte[] bytes;
            String mime;
            if (resourcePath.equals("__reader_font") && style.fontPath != null) {
                File font = new File(style.fontPath);
                if (font.length() > 8 * 1024 * 1024) return denied();
                try (InputStream in = new FileInputStream(font);
                        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) != -1) {
                        if (out.size() + n > 8 * 1024 * 1024) return denied();
                        out.write(b, 0, n);
                    }
                    bytes = out.toByteArray();
                }
                mime = "font/ttf";
            } else {
                resourcePath = document.archive.resolve("", resourcePath).path;
                String lower = resourcePath.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".js") || document.archive.obfuscatedFont(resourcePath))
                    return denied();
                bytes = document.archive.read(resourcePath, EpubArchive.RESOURCE_LIMIT);
                mime =
                        lower.endsWith(".css")
                                ? "text/css"
                                : lower.endsWith(".png")
                                        ? "image/png"
                                        : lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                                                ? "image/jpeg"
                                                : lower.endsWith(".svg")
                                                        ? "image/svg+xml"
                                                        : lower.endsWith(".gif")
                                                                ? "image/gif"
                                                                : lower.endsWith(".webp")
                                                                        ? "image/webp"
                                                                        : lower.endsWith(".ttf")
                                                                                ? "font/ttf"
                                                                                : lower.endsWith(
                                                                                                ".otf")
                                                                                        ? "font/otf"
                                                                                        : "text/html";
                if (mime.equals("text/html") && bytes.length > EpubArchive.CONTENT_LIMIT)
                    return denied();
                if (mime.equals("text/html")) {
                    document.prepareContent(resourcePath, bytes);
                    bytes = EpubHtml.forDisplay(bytes, readerCss());
                }
                if (mime.startsWith("image/") && !mime.equals("image/svg+xml")) {
                    BitmapFactory.Options size = new BitmapFactory.Options();
                    size.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, size);
                    if ((long) size.outWidth * size.outHeight > 16000000) return denied();
                }
            }
            Map<String, String> headers = new HashMap<>();
            headers.put(
                    "Content-Security-Policy",
                    "default-src 'none'; script-src 'none'; style-src 'self' 'unsafe-inline';"
                            + " img-src 'self' data:; font-src 'self'; object-src 'none'; frame-src"
                            + " 'none'; base-uri 'none'");
            headers.put("Cache-Control", "no-store");
            headers.put("X-Content-Type-Options", "nosniff");
            return new WebResourceResponse(
                    mime, "UTF-8", 200, "OK", headers, new ByteArrayInputStream(bytes));
        } catch (Exception unavailable) {
            return denied();
        }
    }

    private static WebResourceResponse denied() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                403,
                "Forbidden",
                Collections.emptyMap(),
                new ByteArrayInputStream(new byte[0]));
    }

    public void close() {
        closed = true;
        epoch++;
        ready = false;
        view.stopLoading();
        view.loadUrl("about:blank");
        view.destroy();
    }
}
