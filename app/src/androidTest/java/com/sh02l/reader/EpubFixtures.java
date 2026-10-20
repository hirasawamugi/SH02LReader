package com.sh02l.reader;

import java.io.*;
import java.util.zip.*;

final class EpubFixtures {
    static File create(File root) throws IOException {
        File file = new File(root, "m25-epub-fixture.epub");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            entry(zip, "mimetype", "application/epub+zip");
            entry(
                    zip,
                    "META-INF/container.xml",
                    "<container"
                        + " xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile"
                        + " full-path='OPS/package.opf'/></rootfiles></container>");
            entry(
                    zip,
                    "OPS/package.opf",
                    "<package xmlns='http://www.idpf.org/2007/opf' version='3.0'><metadata"
                        + " xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>EPUB"
                        + " 迭代验证</dc:title></metadata><manifest><item id='one' href='one.xhtml'"
                        + " media-type='application/xhtml+xml'/><item id='two' href='two.xhtml'"
                        + " media-type='application/xhtml+xml'/><item id='nav' href='nav.xhtml'"
                        + " properties='nav' media-type='application/xhtml+xml'/><item id='cover'"
                        + " href='cover.png' media-type='image/png'"
                        + " properties='cover-image'/></manifest><spine><itemref"
                        + " idref='one'/><itemref idref='two'/></spine></package>");
            entry(
                    zip,
                    "OPS/nav.xhtml",
                    "<html xmlns='http://www.w3.org/1999/xhtml'"
                        + " xmlns:epub='http://www.idpf.org/2007/ops'><body><nav"
                        + " epub:type='toc'><ol><li><a href='one.xhtml'>第一章</a></li><li><a"
                        + " href='two.xhtml#target'>第二章</a></li></ol></nav></body></html>");
            StringBuilder one =
                    new StringBuilder(
                            "<html xmlns='http://www.w3.org/1999/xhtml'><head><meta name='viewport'"
                                + " content='width=device-width,"
                                + " initial-scale=1'/><script>window.addEventListener('load',function(){document.body.innerHTML='SCRIPT_RAN';});</script></head><body><h1>第一章"
                                + " 开始</h1><p><a href='two.xhtml#target'>前往第二章目标</a></p><img"
                                + " src='cover.png'/>");
            for (int i = 0; i < 140; i++)
                one.append("<p>第一章段落 ")
                        .append(i)
                        .append("。中文 EPUB 内容锚点与 English 排版验证，保持书签和阅读位置。</p>");
            entry(zip, "OPS/one.xhtml", one.append("</body></html>").toString());
            StringBuilder two =
                    new StringBuilder(
                            "<html xmlns='http://www.w3.org/1999/xhtml'><body><h1"
                                + " id='target'>第二章目标</h1>");
            for (int i = 0; i < 80; i++)
                two.append("<p>第二章段落 ").append(i).append("。搜索独有词星河漫游，章节导航与重新打开。</p>");
            entry(zip, "OPS/two.xhtml", two.append("</body></html>").toString());
            zip.putNextEntry(new ZipEntry("OPS/cover.png"));
            zip.write(CoverFixtures.png(100, 140));
            zip.closeEntry();
        }
        return file;
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes("UTF-8"));
        zip.closeEntry();
    }
}
