package cn.timeface.tfupload.sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.tumblr.remember.Remember;

import java.util.ArrayList;
import java.util.UUID;

import cn.timeface.tfupload.AbstractUploadServiceReceiver;
import cn.timeface.tfupload.TFUploadService;
import cn.timeface.tfupload.UploadFileObj;
import cn.timeface.tfupload.UploadTaskInfo;

public class MainActivity extends AppCompatActivity {
    String TAG = "MainActivity";
    ArrayList<UploadFileObj> files = new ArrayList<>(10);
    SimpleRecorder recorder = new SimpleRecorder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Remember.init(getApplicationContext(), BuildConfig.APPLICATION_ID);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
//        files.add(new UploadFile("/mnt/sdcard/Download/IMG_0778.JPG", "uploads"));
//        files.add(new UploadFile("/mnt/sdcard/Download/IMG_1359.JPG", "uploads"));
        files.add(new UploadFile("/mnt/sdcard/Download/IMG_7116.JPG", "uploads"));

        recorder.clear();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                final UploadTaskInfo uploadTaskInfo = new UploadTaskInfo(UUID.randomUUID().toString(), files);
                uploadTaskInfo.setContext(getApplicationContext());

                uploadTaskInfo.setNotificationConfig(R.mipmap.ic_launcher, getString(R.string.app_name),
                        getString(R.string.uploading), getString(R.string.upload_success),
                        getString(R.string.upload_error), false);

//                request.setNotificationClickIntent(new Intent(this, MainActivity.class));


                try {
                    TFUploadService.setRecorder(recorder);
                    TFUploadService.startUpload(uploadTaskInfo);
                } catch (Exception exc) {
                    Toast.makeText(MainActivity.this, "Malformed upload request. " + exc.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                }


            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private final AbstractUploadServiceReceiver uploadReceiver = new AbstractUploadServiceReceiver() {

        @Override
        public void onProgress(String uploadId, int progress) {
            Log.i(TAG, "The progress of the upload with ID " + uploadId + " is: " + progress);
        }

        @Override
        public void onError(String uploadId, Exception exception) {
            String message = "Error in upload with ID: " + uploadId + ". " + exception.getLocalizedMessage();
            Log.e(TAG, message, exception);
        }

        @Override
        public void onCompleted(String uploadId) {
            String message = "Upload with ID " + uploadId;
            Log.i(TAG, message);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        uploadReceiver.register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uploadReceiver.unregister(this);
    }
}
