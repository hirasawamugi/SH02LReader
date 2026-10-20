package com.sh02l.reader;

import android.view.KeyEvent;

import com.sh02l.reader.core.*;

import java.util.List;

public final class EpubIntegrationTest extends NativeTestBase {
    private void epub() throws Exception {
        java.io.File file = EpubFixtures.create(activity.getCacheDir());
        ui(() -> activity.importFile(file));
        await(this::ready, 20000);
        ui(() -> activity.seekTo(activity.reading().document.locationAt(0)));
        await(() -> ready() && text().contains("第一章"), 10000);
    }

    private DocumentLocation location() {
        final DocumentLocation[] value = {null};
        ui(() -> value[0] = activity.reading().position.location);
        return value[0];
    }

    private String text() {
        final String[] value = {""};
        ui(() -> value[0] = activity.readingText());
        return value[0];
    }

    public void testResourceBoundaryAndInternalLinks() throws Exception {
        epub();
        assertTrue(text().contains("第一章"));
        assertFalse(text().contains("SCRIPT_RAN"));
        final ReaderSession[] session = {null};
        ui(() -> session[0] = activity.reading());
        assertEquals("epub", session[0].book.format);
        assertEquals(3, session[0].document.contents().size());
        assertNotNull(new CoverStore(activity).get(session[0].book.id));
        ui(
                () -> {
                    android.webkit.WebView web = (android.webkit.WebView) find("reading-content");
                    for (String url :
                            new String[] {
                                "https://outside.invalid/leak",
                                "file:///data/local/secret",
                                web.getUrl().replace("/OPS/one.xhtml", "/%2e%2e/outside")
                            }) {
                        android.webkit.WebResourceRequest request =
                                new android.webkit.WebResourceRequest() {
                                    public android.net.Uri getUrl() {
                                        return android.net.Uri.parse(url);
                                    }

                                    public boolean isForMainFrame() {
                                        return false;
                                    }

                                    public boolean isRedirect() {
                                        return false;
                                    }

                                    public boolean hasGesture() {
                                        return false;
                                    }

                                    public String getMethod() {
                                        return "GET";
                                    }

                                    public java.util.Map<String, String> getRequestHeaders() {
                                        return java.util.Collections.emptyMap();
                                    }
                                };
                        assertEquals(
                                403,
                                web.getWebViewClient()
                                        .shouldInterceptRequest(web, request)
                                        .getStatusCode());
                    }
                });
        press(KeyEvent.KEYCODE_F1);
        assertNull(find("item:encoding"));
        select("links");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> listCount() > 1, 10000);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> ready() && location().payload.startsWith("OPS/two.xhtml\u001f"), 10000);
        press(KeyEvent.KEYCODE_F1);
        select("return-jump");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> ready() && text().contains("第一章"), 10000);
        ui(
                () ->
                        ((android.webkit.WebView) find("reading-content"))
                                .evaluateJavascript("document.querySelector('a').click()", null));
        // The first chapter's link label also contains the target title. Wait for the actual
        // resource change so asynchronous DOM clicks cannot satisfy this with stale source text.
        await(() -> ready() && location().payload.startsWith("OPS/two.xhtml\u001f"), 10000);
        press(KeyEvent.KEYCODE_F1);
        select("return-jump");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> ready() && text().contains("第一章"), 10000);
        ui(
                () ->
                        ((android.webkit.WebView) find("reading-content"))
                                .evaluateJavascript(
                                        "document.querySelector('a[href=\"#mid\"]').click()",
                                        null));
        await(() -> ready() && text().contains("第一章小节"), 10000);
        press(KeyEvent.KEYCODE_F1);
        select("return-jump");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> ready() && text().contains("第一章 开始"), 10000);
        SearchBatch batch = session[0].document.search("星河漫游", null, 2, () -> false);
        assertEquals(2, batch.matches.size());
        assertTrue(batch.hasMore);
        ui(() -> activity.seekTo(batch.matches.get(0).location));
        await(() -> ready() && text().contains("星河漫游"), 10000);
    }

    public void testReflowAndReopenLocation() throws Exception {
        epub();
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(this::ready, 10000);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        await(this::ready, 10000);
        DocumentLocation before = location();
        assertFalse(before.payload.contains("\u001f0\u001f"));
        String visible = text();
        press(KeyEvent.KEYCODE_6);
        await(this::ready, 10000);
        String changed = text();
        assertTrue(
                "Reflow lost content anchor",
                visible.contains(changed.substring(0, Math.min(8, changed.length())))
                        || changed.contains(visible.substring(0, Math.min(8, visible.length()))));
        press(KeyEvent.KEYCODE_POUND);
        select("bookmark-add");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(() -> "READER".equals(kind()), 10000);
        final String[] id = {""};
        ui(() -> id[0] = activity.reading().book.id);
        List<ContentEntry> marks = activity.repository().bookmarkEntries(id[0]);
        assertFalse(marks.isEmpty());
        DocumentLocation saved = location();
        press(KeyEvent.KEYCODE_F4);
        awaitLibrary();
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        await(this::ready, 15000);
        assertEquals(saved, location());
        assertTrue(text().length() > 0);
        ui(() -> activity.seekTo(activity.reading().document.locationAt(1)));
        try {
            await(() -> ready() && text().contains("第二章段落 79"), 10000);
        } catch (AssertionError failure) {
            java.util.concurrent.CountDownLatch reported =
                    new java.util.concurrent.CountDownLatch(1);
            ui(
                    () ->
                            ((android.webkit.WebView) find("reading-content"))
                                    .evaluateJavascript(
                                            "JSON.stringify({report:window.__reader.report(),width:innerWidth,height:innerHeight,scroll:document.documentElement.scrollWidth,body:document.body.scrollWidth,x:pageXOffset,tail:document.body.innerText.slice(-140)})",
                                            result -> {
                                                android.util.Log.i("SH02LEpubTest", result);
                                                reported.countDown();
                                            }));
            reported.await(3, java.util.concurrent.TimeUnit.SECONDS);
            throw failure;
        }
    }

    public void testCustomFontWaitsForFinalMetrics() throws Exception {
        epub();
        java.io.File font = new java.io.File("/system/fonts/DroidSansMono.ttf");
        if (!font.isFile()) font = new java.io.File("/system/fonts/Roboto-Regular.ttf");
        assertTrue(font.isFile());
        new FontStore(activity).importFont(new java.io.FileInputStream(font), null);
        final java.util.concurrent.CountDownLatch
                started = new java.util.concurrent.CountDownLatch(1),
                release = new java.util.concurrent.CountDownLatch(1);
        ui(
                () -> {
                    android.webkit.WebView web = (android.webkit.WebView) find("reading-content");
                    android.webkit.WebViewClient original = web.getWebViewClient();
                    web.setWebViewClient(
                            new android.webkit.WebViewClient() {
                                public android.webkit.WebResourceResponse shouldInterceptRequest(
                                        android.webkit.WebView view,
                                        android.webkit.WebResourceRequest request) {
                                    if (request.getUrl().getPath().equals("/__reader_font")) {
                                        started.countDown();
                                        try {
                                            release.await(4, java.util.concurrent.TimeUnit.SECONDS);
                                        } catch (InterruptedException stop) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                    return original.shouldInterceptRequest(view, request);
                                }
                            });
                    activity.readingSettings().font = 4;
                    activity.readingSettings().invalidateFont();
                    activity.layoutChanged();
                });
        try {
            assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse("Must not publish fallback font positions", ready());
        } finally {
            release.countDown();
        }
        await(this::ready, 10000);
        assertTrue(text().length() > 0);
    }

    public void testSubsectionKeysFollowSameResourceAnchors() throws Exception {
        epub();
        press(KeyEvent.KEYCODE_3);
        await(() -> ready() && text().contains("第一章小节"), 10000);
        press(KeyEvent.KEYCODE_3);
        await(() -> ready() && text().contains("第二章目标"), 10000);
        press(KeyEvent.KEYCODE_1);
        await(() -> ready() && text().contains("第一章小节"), 10000);
    }
}
