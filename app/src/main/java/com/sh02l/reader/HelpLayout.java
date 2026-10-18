package com.sh02l.reader;
import android.graphics.Paint;
import java.util.*;
final class HelpLayout {
    static final class Box {
        final float left,top,right,bottom;
        Box(float left,float top,float right,float bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
        float centerX(){return (left+right)/2;}float centerY(){return (top+bottom)/2;}float width(){return right-left;}
    }
    static final class KeyBox {
        final String id; final Box bounds; final float digitBaseline,labelBaseline;
        KeyBox(String id,Box bounds,float digitBaseline,float labelBaseline){this.id=id;this.bounds=bounds;this.digitBaseline=digitBaseline;this.labelBaseline=labelBaseline;}
    }
    final List<KeyBox> keys;final float centerX,centerY,padRadius,height;
    HelpLayout(List<KeyBox> keys,float centerX,float centerY,float padRadius,float height){this.keys=Collections.unmodifiableList(keys);this.centerX=centerX;this.centerY=centerY;this.padRadius=padRadius;this.height=height;}
    KeyBox key(String id){for(KeyBox key:keys)if(key.id.equals(id))return key;return null;}
    static HelpLayout measure(int widthPx,float density,float scaledDensity){
        float inset=6*density,gap=6*density,cx=widthPx/2f;
        Paint digit=new Paint();digit.setTextSize(16*scaledDensity);Paint label=new Paint();label.setTextSize(12*scaledDensity);
        Paint.FontMetrics df=digit.getFontMetrics(),lf=label.getFontMetrics();
        float numeric=Math.max(54*density,df.bottom-df.top+lf.bottom-lf.top+12*density);
        float function=Math.max(34*density,2*(lf.bottom-lf.top)+8*density);
        float diameter=Math.max(96*density,2*function+gap),top=inset,cy=top+diameter/2,radius=diameter/2;
        float sideRight=cx-radius-gap,sideLeft=cx+radius+gap;
        List<KeyBox> keys=new ArrayList<>();
        add(keys,"F1",new Box(inset,top,sideRight,top+function),lf,lf,density);
        add(keys,"F2",new Box(sideLeft,top,widthPx-inset,top+function),lf,lf,density);
        add(keys,"F3",new Box(inset,top+diameter-function,sideRight,top+diameter),lf,lf,density);
        add(keys,"F4",new Box(sideLeft,top+diameter-function,widthPx-inset,top+diameter),lf,lf,density);
        float column=(widthPx-2*inset-2*gap)/3,y=top+diameter+gap;
        String[][] rows={{"CALL","CLEAR","END"},{"1","2","3"},{"4","5","6"},{"7","8","9"},{"*","0","#"},{"I","II","III"}};
        for(int row=0;row<rows.length;row++){
            float height=row==0?Math.max(28*density,lf.bottom-lf.top+10*density):row==5?Math.max(25*density,lf.bottom-lf.top+8*density):numeric;
            for(int col=0;col<3;col++){
                Box box=new Box(inset+col*(column+gap),y,inset+col*(column+gap)+column,y+height);
                if(row==0||row==5){float baseline=box.centerY()-(lf.ascent+lf.descent)/2;keys.add(new KeyBox(rows[row][col],box,baseline,baseline));}
                else add(keys,rows[row][col],box,df,lf,density);
            }y+=height+gap;
        }
        return new HelpLayout(keys,cx,cy,radius,y-gap+inset);
    }
    private static void add(List<KeyBox> keys,String id,Box box,Paint.FontMetrics digit,Paint.FontMetrics label,float density){
        float total=digit.bottom-digit.top+label.bottom-label.top+2*density;
        float start=box.centerY()-total/2;keys.add(new KeyBox(id,box,start-digit.top,start+(digit.bottom-digit.top)+2*density-label.top));
    }
}
