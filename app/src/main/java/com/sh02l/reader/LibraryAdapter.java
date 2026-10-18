package com.sh02l.reader;

import android.content.Context;
import android.view.*;
import android.widget.BaseAdapter;
import com.sh02l.reader.core.*;
import java.util.*;
import java.util.function.*;

final class LibraryAdapter extends BaseAdapter {
    static final class Item {
        final BookRepository.Book book; final boolean continuing;final long offset;
        Item(BookRepository.Book book,boolean continuing,long offset) {this.book=book;this.continuing=continuing;this.offset=offset;}
    }
    private final Context context;private final IntSupplier selection;private final BooleanSupplier active;
    private List<Item> items=Collections.emptyList();private int style=AppearanceState.ICON;private Palette palette=Palette.forTheme(0);
    LibraryAdapter(Context context,IntSupplier selection,BooleanSupplier active) {this.context=context;this.selection=selection;this.active=active;}
    void setItems(List<Item> items) {this.items=new ArrayList<>(items);notifyDataSetChanged();}
    void setStyle(int style,Palette palette) {this.style=style;this.palette=palette;notifyDataSetChanged();}
    int positionOfBook(String id) {for(int i=0;i<items.size();i++)if(items.get(i).book!=null&&items.get(i).book.id.equals(id))return i;return -1;}
    public int getCount(){return items.size();}
    public Item getItem(int position){return items.get(position);}
    public long getItemId(int position){return position;}
    public View getView(int position,View convert,ViewGroup parent) {
        BookRowView row=convert instanceof BookRowView?(BookRowView)convert:new BookRowView(context);
        boolean selected=position==selection.getAsInt();row.bind(getItem(position),style,palette,selected);row.setActive(selected&&active.getAsBoolean());return row;
    }
}
