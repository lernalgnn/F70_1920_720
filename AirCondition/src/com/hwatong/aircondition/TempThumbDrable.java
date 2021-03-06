package com.hwatong.aircondition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;

public class TempThumbDrable extends Drawable{
	
	private static final String TAG = "TempThumbDrable" ;
	
	
	RectF rectF = null; 
	
	int left;
	int top;
	int right;
	int bottom ;
	
	/**
	 * 默认温度
	 */
	private String temp= "0" ;
	
    public void setTemp(String temp) {
		this.temp = temp;
		invalidateSelf() ;
	}
   
    private Context context;
    
	private Paint mPaint;  
    private Bitmap mBitmap;


	private float textSize;


	private float textLeft;


	private float textTop;  
 
  
    public TempThumbDrable(Bitmap bitmap, Context context) {  
    	this.context = context;
        mBitmap = bitmap;    
        mPaint = new Paint();  
        mPaint.setAntiAlias(true);   
        
        TypedValue typeSize = new TypedValue();
        context.getResources().getValue(R.dimen.thumb_text_size, typeSize, true);
        textSize = typeSize.getFloat();
        
        TypedValue typeLeft = new TypedValue();
        context.getResources().getValue(R.dimen.thumb_text_left, typeLeft, true);
        textLeft = typeLeft.getFloat();
        
        TypedValue typeTop = new TypedValue();
        context.getResources().getValue(R.dimen.thumb_text_top, typeTop, true);
        textTop = typeTop.getFloat();
        
        Log.d("kongtiao", "size : " + textSize + " left : " + textLeft + " top : " + textTop);
        
    }  
  
    @Override  
    public void setBounds(int left, int top, int right, int bottom) {  
    	Log.d(TAG, "left="+left +"top="+top +"right="+right +"bottom="+bottom);
    	rectF = new RectF(left, top, right, bottom);
    	this.left = left ; 
    	this.top = top ;
    	this.right = right ;
    	this.bottom = bottom ;
        super.setBounds(left, top, right , bottom);
    }  
  
    @Override  
    public void draw(Canvas canvas) {  
        canvas.drawBitmap(mBitmap, null, rectF, mPaint);
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        
        
        paint.setTextSize(textSize);
        canvas.drawText(temp, getBounds().centerX() - textLeft, getBounds().centerY() + textTop, paint);
    }  
  
    @Override  
    public int getIntrinsicWidth() {  
    	return DensityUtils.dp2px(context, mBitmap.getWidth()); // + 20 + 20 + 10 + 10 + 10;  
    }  
  
    @Override  
    public int getIntrinsicHeight() {  
        return DensityUtils.dp2px(context, mBitmap.getHeight()); // + 20;  
    }  
  
    @Override  
    public void setAlpha(int alpha) {  
        mPaint.setAlpha(alpha);  
    }  
  
    @Override  
    public void setColorFilter(ColorFilter cf) {  
        mPaint.setColorFilter(cf);  
    }  
  
    @Override  
    public int getOpacity() {  
        return PixelFormat.TRANSLUCENT;  
    } 


}
