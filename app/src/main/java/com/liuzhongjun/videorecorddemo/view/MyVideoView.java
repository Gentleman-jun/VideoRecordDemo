package com.liuzhongjun.videorecorddemo.view;

/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageButton;

import com.liuzhongjun.videorecorddemo.R;
import com.liuzhongjun.videorecorddemo.util.mMediaController;

import java.io.IOException;

/**
 * Displays a video file. The VideoView class can load images from various
 * sources (such as resources or content providers), takes care of computing its
 * measurement from the video so that it can be used in any layout manager, and
 * provides various display options such as scaling and tinting.
 */
public class MyVideoView extends SurfaceView implements mMediaController.MediaPlayerControl, View.OnTouchListener {
    private String TAG = "VideoView";

    private Context mContext;

    // settable by the client
    private Uri mUri;
    private int mDuration;

    // All the stuff we need for playing and showing a video
    private SurfaceHolder mSurfaceHolder = null;
    private MediaPlayer mMediaPlayer = null;
    private boolean mIsPrepared;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private mMediaController mmMediaController;
    private OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private int mCurrentBufferPercentage;
    private OnErrorListener mOnErrorListener;
    private boolean mStartWhenPrepared;
    private int mSeekWhenPrepared;
    private ImageButton mPauseButton;
    private MySizeChangeLinstener mMyChangeLinstener;
    private Handler handler = new Handler();

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public void setVideoScale(int width, int height) {
        LayoutParams lp = getLayoutParams();
        lp.height = height;
        lp.width = width;
        setLayoutParams(lp);
    }

    private float startX;
    private float endX;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                break;
            case MotionEvent.ACTION_MOVE:
                endX = event.getX();
                float range = Math.abs(startX - endX);
                if (startX < endX) {
                    //forward
//                    canSeekForward();
                } else {
                    canSeekBackward();
                }
                break;
        }
        return false;
    }

    public interface MySizeChangeLinstener {
        public void doMyThings();
    }

    public void setMySizeChangeLinstener(MySizeChangeLinstener l) {
        mMyChangeLinstener = l;
    }

    public MyVideoView(Context context) {
        super(context);
        mContext = context;
        initVideoView();
        setOnTouchListener(this);
    }

    public MyVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        mContext = context;
        initVideoView();
        setOnTouchListener(this);
    }

    public MyVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        initVideoView();
        setOnTouchListener(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Log.i("@@@@", "onMeasure");
        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
            /*
             * Parent says we can be as big as we want. Just don't be larger
			 * than max size imposed on ourselves.
			 */
                result = desiredSize;
                break;

            case MeasureSpec.AT_MOST:
            /*
             * Parent says we can be as big as we want, up to specSize. Don't be
			 * larger than specSize, and don't be larger than the max size
			 * imposed on ourselves.
			 */
                result = Math.min(desiredSize, specSize);
                break;

            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
        }
        return result;
    }

    private void initVideoView() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        getHolder().addCallback(mSHCallback);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final View v = inflater.inflate(R.layout.now_media_controller, null);
        mPauseButton = (ImageButton) v.findViewById(R.id.pause);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    public void setVideoPath(String path) {
        setVideoURI(Uri.parse(path));
    }

    public void setVideoURI(Uri uri) {
        mUri = uri;
        mStartWhenPrepared = false;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @SuppressLint("NewApi")
    private void openVideo() {
        if (mUri == null || mSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            return;
        }
        // Tell the music playback service to pause
        // TODO: these constants need to be published somewhere in the
        // framework.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        mContext.sendBroadcast(i);

        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mIsPrepared = true;
            Log.v(TAG, "reset duration to -1 in openVideo");
            mDuration = -1;
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mContext, mUri);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();

            attachMediaController();
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            return;
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            return;
        }
    }

    public void setMediaController(mMediaController controller) {
        if (mmMediaController != null) {
            mmMediaController.hide();
        }
        mmMediaController = controller;
        attachMediaController();
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mmMediaController != null) {
            mmMediaController.setMediaPlayer(this);
            ViewGroup anchorView = (ViewGroup) (this.getParent() instanceof View ? (View) this
                    .getParent() : this);
            mmMediaController.setAnchorView(anchorView);
            mmMediaController.setEnabled(mIsPrepared);
        }
    }

    @SuppressLint("NewApi")
    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener = new MediaPlayer.OnVideoSizeChangedListener() {
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

            if (mMyChangeLinstener != null) {
                mMyChangeLinstener.doMyThings();
            }

            if (mVideoWidth != 0 && mVideoHeight != 0) {
                getHolder().setFixedSize(mVideoWidth, mVideoHeight);
            }
        }
    };

    MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            // briefly show the mediacontroller
            mIsPrepared = true;
            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }
            if (mmMediaController != null) {
                mmMediaController.setEnabled(true);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                // Log.i("@@@@", "video size: " + mVideoWidth +"/"+
                // mVideoHeight);
                getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                if (mSurfaceWidth == mVideoWidth
                        && mSurfaceHeight == mVideoHeight) {
                    // We didn't actually change the size (it was already at the
                    // size
                    // we need), so we won't get a "surface changed" callback,
                    // so
                    // start the video here instead of in the callback.
                    if (mSeekWhenPrepared != 0) {
                        mMediaPlayer.seekTo(mSeekWhenPrepared);
                        mSeekWhenPrepared = 0;
                    }
                    if (mStartWhenPrepared) {
                        mMediaPlayer.start();
                        mStartWhenPrepared = false;
                        if (mmMediaController != null) {
                            mmMediaController.show();
                        }
                    } else if (!isPlaying()
                            && (mSeekWhenPrepared != 0 || getCurrentPosition() > 0)) {
                        if (mmMediaController != null) {
                            // Show the media controls when we're paused into a
                            // video and make 'em stick.
                            mmMediaController.show(0);
                        }
                    }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mSeekWhenPrepared != 0) {
                    mMediaPlayer.seekTo(mSeekWhenPrepared);
                    mSeekWhenPrepared = 0;
                }
                if (mStartWhenPrepared) {
                    mMediaPlayer.start();
                    mStartWhenPrepared = false;
                }
            }
        }
    };

    private OnCompletionListener mCompletionListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mp) {
            if (mmMediaController != null) {
                mmMediaController.hide();
            }
            if (mOnCompletionListener != null) {
                mOnCompletionListener.onCompletion(mMediaPlayer);
            }
        }
    };

    private OnErrorListener mErrorListener = new OnErrorListener() {
        public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
            Log.d(TAG, "Error: " + framework_err + "," + impl_err);
            if (mmMediaController != null) {
                mmMediaController.hide();
            }

			/* If an error handler has been supplied, use it and finish. */
            if (mOnErrorListener != null) {
                if (mOnErrorListener.onError(mMediaPlayer, framework_err,
                        impl_err)) {
                    return true;
                }
            }

			/*
             * Otherwise, pop up an error dialog so the user knows that
			 * something bad has happened. Only try and pop up the dialog if
			 * we're attached to a window. When we're going away and no longer
			 * have a window, don't bother showing the user an error.
			 */
            if (getWindowToken() != null) {
                Resources r = mContext.getResources();
                int messageId;

				/*
                 * if (framework_err ==
				 * MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
				 * messageId = com.android.internal.R.string.
				 * VideoView_error_text_invalid_progressive_playback; } else {
				 * messageId =
				 * com.android.internal.R.string.VideoView_error_text_unknown; }
				 * 
				 * new AlertDialog.Builder(mContext)
				 * .setTitle(com.android.internal
				 * .R.string.VideoView_error_title) .setMessage(messageId)
				 * .setPositiveButton
				 * (com.android.internal.R.string.VideoView_error_button, new
				 * DialogInterface.OnClickListener() { public void
				 * onClick(DialogInterface dialog, int whichButton) { If we get
				 * here, there is no onError listener, so at least inform them
				 * that the video is over.
				 * 
				 * if (mOnCompletionListener != null) {
				 * mOnCompletionListener.onCompletion(mMediaPlayer); } } })
				 * .setCancelable(false) .show();
				 */
            }
            return true;
        }
    };

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            mCurrentBufferPercentage = percent;
        }
    };

    /**
     * Register a callback to be invoked when the media file is loaded and ready
     * to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file has been
     * reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs during playback or
     * setup. If no listener is specified, or if the listener returned false,
     * VideoView will inform the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(OnErrorListener l) {
        mOnErrorListener = l;
    }

    SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format, int w,
                                   int h) {
            Log.d(TAG, "surfaceChanged 2");
            mSurfaceWidth = w;
            mSurfaceHeight = h;

            if (mMediaPlayer != null && mIsPrepared && mVideoWidth == w
                    && mVideoHeight == h) {
                if (mSeekWhenPrepared != 0) {
                    mMediaPlayer.seekTo(mSeekWhenPrepared);
                    mSeekWhenPrepared = 0;
                }


            }
        }

        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated 1");
            mSurfaceHolder = holder;
            openVideo();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // after we return from this we can't use the surface any more
            Log.d(TAG, "surfaceDestroyed 3");
            mSurfaceHolder = null;
            if (mmMediaController != null)
                mmMediaController.hide();
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mIsPrepared && mMediaPlayer != null && mmMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (mIsPrepared && mMediaPlayer != null && mmMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mIsPrepared && keyCode != KeyEvent.KEYCODE_BACK
                && keyCode != KeyEvent.KEYCODE_VOLUME_UP
                && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
                && keyCode != KeyEvent.KEYCODE_MENU
                && keyCode != KeyEvent.KEYCODE_CALL
                && keyCode != KeyEvent.KEYCODE_ENDCALL && mMediaPlayer != null
                && mmMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mmMediaController.show();
                } else {
                    start();
                    mmMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    && mMediaPlayer.isPlaying()) {
                pause();
                mmMediaController.show();
            } else {
                toggleMediaControlsVisiblity();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisiblity() {
        if (mmMediaController.isShowing()) {
            mmMediaController.hide();
        } else {
            mmMediaController.show();
        }
    }

    public void start() {
        if (mMediaPlayer != null && mIsPrepared) {
            mMediaPlayer.start();
            mmMediaController = new mMediaController(getContext());
            mmMediaController.updatePausePlay();
            mStartWhenPrepared = false;
        } else {
            mStartWhenPrepared = true;
        }
    }

    public void pause() {
        if (mMediaPlayer != null && mIsPrepared) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
            }
        }
        mStartWhenPrepared = false;
    }

    public int getDuration() {
        if (mMediaPlayer != null && mIsPrepared) {
            if (mDuration > 0) {
                return mDuration;
            }
            mDuration = mMediaPlayer.getDuration();
            return mDuration;
        }
        mDuration = -1;
        return mDuration;
    }

    public int getCurrentPosition() {
        if (mMediaPlayer != null && mIsPrepared) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void seekTo(int msec) {
        if (mMediaPlayer != null && mIsPrepared) {
            mMediaPlayer.seekTo(msec);

        } else {
            mSeekWhenPrepared = msec;
        }
    }

    //!
    public boolean isPlaying() {
        if (mMediaPlayer != null && mIsPrepared) {
            Log.d(TAG, "mMediaPlayer.isPlaying():" + mMediaPlayer.isPlaying());
            return mMediaPlayer.isPlaying();
        }
        return true;
    }

    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    @Override
    public boolean canPause() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public boolean canSeekForward() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public boolean isFullScreen() {
        return false;
    }

    @Override
    public void toggleFullScreen() {

    }

}
