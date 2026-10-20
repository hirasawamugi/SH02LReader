package com.sh02l.reader;

import android.view.KeyEvent;
import android.widget.TextView;

public final class M25InteractionTest extends NativeTestBase {
    public void testMenuSwitchAndInputOwnership() throws Exception {
        fixture();
        press(KeyEvent.KEYCODE_F4);
        awaitLibrary();
        press(KeyEvent.KEYCODE_F1);
        assertEquals("MENU", kind());
        assertEquals("item:cover", selected());
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals("item:back", selected());
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals("item:delete-book", selected());
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals("item:back", selected());
        press(KeyEvent.KEYCODE_F3);
        assertEquals("MENU", kind());
        assertEquals("item:settings", selected());
        assertNull(find("item:delete-book"));
        select("help");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("HELP", kind());
        press(KeyEvent.KEYCODE_F4);
        assertEquals("MENU", kind());
        press(KeyEvent.KEYCODE_F3);
        awaitLibrary();
        press(KeyEvent.KEYCODE_F1);
        select("rename-book");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("INPUT", kind());
        press(KeyEvent.KEYCODE_F2);
        assertEquals("INPUT", kind());
        press(KeyEvent.KEYCODE_F4);
        assertEquals("MENU", kind());
        press(KeyEvent.KEYCODE_F4);
        awaitLibrary();
        press(KeyEvent.KEYCODE_F2);
        assertEquals("SOURCES", kind());
        press(KeyEvent.KEYCODE_F4);
        awaitLibrary();
        select("import");
        press(KeyEvent.KEYCODE_F1);
        assertEquals("LIBRARY", kind());
        press(KeyEvent.KEYCODE_F3);
        assertEquals("MENU", kind());
    }

    public void testReaderMenuAndInputConfirm() throws Exception {
        fixture();
        press(KeyEvent.KEYCODE_F1);
        assertEquals("item:contents", selected());
        assertNull(find("item:import"));
        select("jump");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        ui(() -> ((TextView) find("reader-input")).setText("35"));
        press(KeyEvent.KEYCODE_F3);
        await(this::ready, 10000);
        assertEquals("READER", kind());
        assertTrue(offset() > 0);
        press(KeyEvent.KEYCODE_F2);
        assertEquals("SOURCES", kind());
        press(KeyEvent.KEYCODE_F4);
        await(this::ready, 10000);
        assertEquals("READER", kind());
        assertTrue(offset() > 0);
    }
}
