package com.sh02l.reader;

import android.content.Context;
import android.view.*;
import android.widget.BaseAdapter;

import com.sh02l.reader.core.*;

import java.util.*;
import java.util.function.*;

final class LibraryAdapter extends BaseAdapter implements AutoCloseable {
    static final class Item {
        final BookRepository.Book book;
        final boolean continuing;
        final long offset;
        final String actionId, label;

        Item(BookRepository.Book book, boolean continuing, long offset) {
            this(book, continuing, offset, "import", "导入 TXT / EPUB");
        }

        Item(
                BookRepository.Book book,
                boolean continuing,
                long offset,
                String actionId,
                String label) {
            this.book = book;
            this.continuing = continuing;
            this.offset = offset;
            this.actionId = actionId;
            this.label = label;
        }
    }

    private final Context context;
    private final IntSupplier selection;
    private final BooleanSupplier active;
    private List<Item> items = Collections.emptyList();
    private int style = AppearanceState.ICON;
    private Palette palette = Palette.forTheme(0);
    private final CoverLoader covers;
    private Set<String> visible = Collections.emptySet();
    private long token;
    private String selectedBook = "";

    LibraryAdapter(Context context, IntSupplier selection, BooleanSupplier active) {
        this.context = context;
        this.selection = selection;
        this.active = active;
        covers = new CoverLoader(new CoverStore(context));
    }

    void setItems(List<Item> items) {
        this.items = new ArrayList<>(items);
        notifyDataSetChanged();
    }

    void setStyle(int style, Palette palette) {
        this.style = style;
        this.palette = palette;
        if (style != AppearanceState.COVER) {
            visible = Collections.emptySet();
            covers.retain(visible);
        }
        notifyDataSetChanged();
    }

    void setSelectedBook(String id) {
        selectedBook = id;
    }

    void setVisibleRange(int first, int count) {
        Set<String> ids = new LinkedHashSet<>();
        if (style == AppearanceState.COVER && count > 0)
            for (int i = Math.max(0, first - 1);
                    i < Math.min(items.size(), first + count + 1);
                    i++) {
                BookRepository.Book book = items.get(i).book;
                if (book != null) ids.add(book.id);
            }
        if (!ids.equals(visible)) {
            visible = ids;
            covers.retain(ids);
            notifyDataSetChanged();
        }
    }

    public int getCount() {
        return items.size();
    }

    public Item getItem(int position) {
        return items.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convert, ViewGroup parent) {
        BookRowView row =
                convert instanceof BookRowView ? (BookRowView) convert : new BookRowView(context);
        Item item = getItem(position);
        boolean selected =
                selectedBook.length() == 64
                        ? item.book != null && item.book.id.equals(selectedBook)
                        : position == selection.getAsInt();
        row.bind(item, style, palette, selected);
        row.setActive(selected && active.getAsBoolean());
        long binding = ++token;
        row.setBinding(binding);
        if (style == AppearanceState.COVER && item.book != null) {
            float density = context.getResources().getDisplayMetrics().density;
            covers.load(
                    item.book.id,
                    Math.round(56 * density),
                    Math.round(84 * density),
                    binding,
                    (book, revision, bindToken, bitmap) -> {
                        if (row.matches(book, bindToken)) row.setCover(bitmap);
                    });
        }
        return row;
    }

    public void close() {
        covers.close();
    }
}
