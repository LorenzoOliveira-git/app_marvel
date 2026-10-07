package com.example.app_marvel.ui.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.ImageView;
import com.example.app_marvel.R;

/** Expressões do Marv derivadas do atlas do próprio app, salvas por conta neste aparelho. */
public final class MarvAvatars {
    public static final String[] LABELS={"Tranquilo","Acenando","Atento","Pensativo","Comemorando","Acolhedor","Apaixonado","Surpreso"};
    private MarvAvatars() { }
    public static int selected(Context context,String uid){return uid.isEmpty()?-1:context.getSharedPreferences("marv_avatars",Context.MODE_PRIVATE).getInt(uid,-1);}
    public static void select(Context context,String uid,int index){if(!uid.isEmpty()&&index>=0&&index<LABELS.length)context.getSharedPreferences("marv_avatars",Context.MODE_PRIVATE).edit().putInt(uid,index).apply();}
    public static void show(ImageView target,int index){target.setImageBitmap(bitmap(target.getContext(),index));target.setScaleType(ImageView.ScaleType.CENTER_CROP);}
    public static Bitmap bitmap(Context context,int index){
        Bitmap atlas=BitmapFactory.decodeResource(context.getResources(),R.drawable.marv_companion_atlas);
        int w=atlas.getWidth()/3,h=atlas.getHeight()/2,pose=index<6?index:index==6?4:2;
        Bitmap output=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(output);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);paint.setColor(index==6?Color.rgb(252,216,231):index==7?Color.rgb(251,234,183):Color.rgb(229,233,247));
        canvas.drawCircle(96,96,96,paint);paint.setColor(Color.WHITE);
        canvas.drawBitmap(atlas,new Rect(pose%3*w,pose/3*h,(pose%3+1)*w,(pose/3+1)*h),new RectF(3,3,189,189),paint);
        paint.setTextSize(44);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(index==6?Color.rgb(203,43,77):Color.rgb(57,65,98));
        if(index==6)canvas.drawText("♥",158,50,paint);
        if(index==7)canvas.drawText("!",160,56,paint);
        atlas.recycle();return output;
    }
}
