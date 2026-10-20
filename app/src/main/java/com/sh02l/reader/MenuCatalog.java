package com.sh02l.reader;

import com.sh02l.reader.core.*;

import java.util.ArrayList;
import java.util.List;

/** Stable menu IDs and order, independent of view construction and asynchronous jobs. */
final class MenuCatalog {
    static List<UiAction> items(InputContext context) {
        List<UiAction> result = new ArrayList<>();
        add(result, "back", "关闭菜单");
        if (context.general) {
            add(result, "settings", "阅读与界面设置");
            add(result, "import", "导入图书（TXT / EPUB）");
            add(result, "storage", "存储管理");
            add(result, "help", "按键说明");
            add(result, "diagnostics", "按键诊断");
            add(result, "exit", "退出应用");
        } else if (context.base == NavigationState.Kind.READER) {
            add(result, "contents", "目录");
            add(result, "bookmarks", "书签");
            add(result, "library", "返回书架");
            add(result, "search", "搜索正文");
            add(result, "jump", "跳转阅读进度");
            if (context.capabilities.contains(DocumentCapability.LINKS))
                add(result, "links", "本章内部链接");
            if (context.canReturn) add(result, "return-jump", "返回跳转前位置");
            if (context.capabilities.contains(DocumentCapability.ENCODING))
                add(result, "encoding", "纠正文本编码");
        } else if (context.bookSelected) {
            add(result, "cover", "设置 / 更换封面");
            add(result, "rename-book", "重命名图书");
            add(result, "export-text", "导出原 " + context.format.toUpperCase(java.util.Locale.ROOT));
            if (context.cover) {
                add(result, "export-cover", "导出封面原图");
                add(result, "remove-cover", "移除自定义封面");
            }
            add(result, "delete-book", "删除图书及私有数据");
        }
        return result;
    }

    private static void add(List<UiAction> list, String id, String title) {
        list.add(new UiAction(id, title));
    }
}
