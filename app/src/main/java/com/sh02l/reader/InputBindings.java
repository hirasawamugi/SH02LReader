package com.sh02l.reader;

import static com.sh02l.reader.NavigationState.Kind.*;

import com.sh02l.reader.core.*;

import java.util.EnumMap;
import java.util.Map;

/** Contextual actions and their F-key hints always come from the same rule. */
final class InputBindings {
    static Action resolve(InputContext context, Key key) {
        NavigationState.Kind kind = context.kind;
        if (kind == INPUT)
            return key == Key.F4 ? Action.BACK : key == Key.F3 ? Action.ACTIVATE : Action.NONE;
        if (kind == HELP) {
            switch (key) {
                case F4:
                    return Action.BACK;
                case ZERO:
                    return Action.HELP;
                case UP:
                    return Action.UP;
                case DOWN:
                    return Action.DOWN;
                default:
                    return Action.NONE;
            }
        }
        if (key == Key.F4) return kind == LIBRARY ? Action.NONE : Action.BACK;
        boolean root = kind == LIBRARY || kind == READER || kind == MENU;
        if (key == Key.F1) return root ? Action.MENU : Action.NONE;
        if (key == Key.F3) return root ? Action.GENERAL_MENU : Action.NONE;
        if (key == Key.F2) return root ? Action.IMPORT : Action.NONE;
        if (key == Key.ZERO) return Action.HELP;
        if (kind == READER) {
            switch (key) {
                case UP:
                case LEFT:
                    return Action.PREVIOUS_PAGE;
                case DOWN:
                case RIGHT:
                    return Action.NEXT_PAGE;
                case CENTER:
                    return Action.ACTIVATE;
                case ONE:
                    return Action.PREVIOUS_CHAPTER;
                case TWO:
                    return Action.CONTENTS;
                case THREE:
                    return Action.NEXT_CHAPTER;
                case FOUR:
                    return Action.SMALLER;
                case SIX:
                    return Action.LARGER;
                case FIVE:
                    return Action.THEME;
                case SEVEN:
                    return Action.DIMMER;
                case NINE:
                    return Action.BRIGHTER;
                case EIGHT:
                    return Action.INFO;
                case STAR:
                    return Action.SETTINGS;
                case POUND:
                    return Action.BOOKMARKS;
                default:
                    return Action.NONE;
            }
        }
        switch (key) {
            case UP:
                return Action.UP;
            case DOWN:
                return Action.DOWN;
            case LEFT:
                return Action.LEFT;
            case RIGHT:
                return Action.RIGHT;
            case CENTER:
                return Action.ACTIVATE;
            default:
                return Action.NONE;
        }
    }

    static String label(Action action, InputContext context) {
        switch (action) {
            case MENU:
                return context.base == READER ? "阅读菜单" : "书籍菜单";
            case GENERAL_MENU:
                return "通用菜单";
            case IMPORT:
                return "导入图书";
            case BACK:
                return context.kind == READER ? "返回书架" : "返回 / 关闭";
            case ACTIVATE:
                return "确认";
            default:
                return "—";
        }
    }

    static Map<Key, String> hints(InputContext context, DeviceKeyMap profile) {
        Map<Key, String> hints = new EnumMap<>(Key.class);
        for (Key key : new Key[] {Key.F1, Key.F2, Key.F3, Key.F4})
            hints.put(key, profile.label(key) + " " + label(resolve(context, key), context));
        return hints;
    }
}
