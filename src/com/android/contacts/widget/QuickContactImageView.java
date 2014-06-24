package com.android.contacts.widget;


import com.android.contacts.common.lettertiles.LetterTileDrawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * An {@link ImageView} designed to display QuickContact's contact photo. In addition to
 * supporting {@link ImageView#setColorFilter} this also performs a second color blending with
 * the tint set in {@link #setTint}. This requires a second draw pass.
 */
public class QuickContactImageView extends ImageView {

    private Xfermode mMode = new PorterDuffXfermode(Mode.MULTIPLY);
    private int mTintColor;
    private BitmapDrawable mBitmapDrawable;
    private Drawable mOriginalDrawable;

    public QuickContactImageView(Context context) {
        this(context, null);
    }

    public QuickContactImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickContactImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public QuickContactImageView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setTint(int color) {
        mTintColor = color;
        postInvalidate();
    }

    public boolean isBasedOffLetterTile() {
        return mOriginalDrawable instanceof LetterTileDrawable;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        // There is no way to avoid all this casting. Blending modes aren't equally
        // supported for all drawable types.
        if (drawable == null || drawable instanceof BitmapDrawable) {
            mBitmapDrawable = (BitmapDrawable) drawable;
        } else if (drawable instanceof LetterTileDrawable) {
            // TODO: set a desired hardcoded BitmapDrawable here
            mBitmapDrawable = null;
        } else {
            throw new IllegalArgumentException("Does not support this type of drawable");

        }
        mOriginalDrawable = drawable;
        super.setImageDrawable(mBitmapDrawable);
    }

    @Override
    public Drawable getDrawable() {
        return mOriginalDrawable;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isBasedOffLetterTile()) {
            // The LetterTileDrawable's bitmaps have a lot of pixels with alpha=0. These
            // look stupid unless we fill in the background and use a different blending mode.
            canvas.drawColor(((LetterTileDrawable) mOriginalDrawable).getColor());
        }
        super.onDraw(canvas);
    }
}
