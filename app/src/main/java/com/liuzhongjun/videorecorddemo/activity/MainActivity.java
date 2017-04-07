package com.liuzhongjun.videorecorddemo.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;
import android.widget.VideoView;

import com.liuzhongjun.videorecorddemo.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends AppCompatActivity {
    public static final int RECORD_SYSTEM_VIDEO = 1;
    public static final int RECORD_CUSTOM_VIDEO = 2;
    private VideoView mVideoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mVideoView = (VideoView) findViewById(R.id.videoView);
    }

    /**
     * 启用系统相机录制
     *
     * @param view
     */
    public void reconverIntent(View view) {
        Uri fileUri = Uri.fromFile(getOutputMediaFile());
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10);     //限制的录制时长 以秒为单位
//        intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 1024);        //限制视频文件大小 以字节为单位
//        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);      //设置拍摄的质量0~1
//        intent.putExtra(MediaStore.EXTRA_FULL_SCREEN, false);        // 全屏设置
        startActivityForResult(intent, RECORD_SYSTEM_VIDEO);

    }

    /**
     * 启动自定义相机录制
     *
     * @param view
     */
    public void customVideo(View view) {
        Intent intent = new Intent(this, CustomRecordActivity.class);
        startActivityForResult(intent, RECORD_CUSTOM_VIDEO);
    }


    /**
     * Create a File for saving an video
     */
    private File getOutputMediaFile() {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Toast.makeText(this, "请检查SDCard！", Toast.LENGTH_SHORT).show();
            return null;
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "MyCameraApp");
        if (!mediaStorageDir.exists()) {
            mediaStorageDir.mkdirs();
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
        return mediaFile;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case RECORD_SYSTEM_VIDEO:
                mVideoView.setVideoURI(data.getData());
                mVideoView.start();
                break;

            case RECORD_CUSTOM_VIDEO:
                String filePath = data.getStringExtra("videoPath");
                mVideoView.setVideoPath(filePath);
                mVideoView.start();
                break;
        }
    }
}
