package cn.timeface.tfupload;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.PowerManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.alibaba.sdk.android.oss.callback.SaveCallback;
import com.alibaba.sdk.android.oss.model.OSSException;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import cn.timeface.tfupload.oss.OSSManager;

/**
 * author: rayboot  Created on 15/10/10.
 * email : sy0725work@gmail.com
 */
public class TFUploadService extends IntentService {

    public static String NAMESPACE = "cn.timeface";
    private static final String ACTION_UPLOAD_SUFFIX = ".tfupload.action.upload";
    private static final String BROADCAST_ACTION_SUFFIX = ".tfupload.broadcast.status";
    protected static final String PARAM_NOTIFICATION_CONFIG = "param_notificationConfig";
    protected static final String PARAM_ID = "param_id";
    protected static final String PARAM_FILES = "param_files";

    private static final int UPLOAD_NOTIFICATION_ID = 1234; // Something unique
    private static final int UPLOAD_NOTIFICATION_ID_DONE = 1235; // Something unique

    /**
     * The minimum interval between progress reports in milliseconds.
     * If the upload Tasks report more frequently, we will throttle notifications.
     * We aim for 6 updates per second.
     */
    protected static final long PROGRESS_REPORT_INTERVAL = 166;

    private static final String TAG = TFUploadService.class.getName();
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notification;
    private UploadNotificationConfig notificationConfig;
    private PowerManager.WakeLock wakeLock;
    private long lastProgressNotificationTime;
    private static Recorder recorder;

    private final String SERVER_ADDRESS = "http://tftest.timeface.cn/tfFire";
    private final String END_POINT = "oss-cn-hangzhou.aliyuncs.com";
    private final String BUCKET_NAME = "fire-audio";

    public static final String UPLOAD_ID = "id";
    public static final String STATUS = "status";
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_ERROR = 3;
    public static final String PROGRESS = "progress";
    public static final String PROGRESS_UPLOADED_BYTES = "progressUploadedBytes";
    public static final String PROGRESS_TOTAL_BYTES = "progressTotalBytes";
    public static final String ERROR_EXCEPTION = "errorException";

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */
    public TFUploadService() {
        super(TAG);
    }

    public static String getActionUpload() {
        return NAMESPACE + ACTION_UPLOAD_SUFFIX;
    }

    public static String getActionBroadcast() {
        return NAMESPACE + BROADCAST_ACTION_SUFFIX;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notification = new NotificationCompat.Builder(this);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    public static void setRecorder(Recorder myRecord) {
        recorder = myRecord;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        if (getActionUpload().equals(intent.getAction())) {
            notificationConfig = intent.getParcelableExtra(PARAM_NOTIFICATION_CONFIG);

            lastProgressNotificationTime = 0;
            wakeLock.acquire();

            createNotification();

//            doUpload(new UploadTaskInfo(intent.getStringExtra(PARAM_ID),
//                    (ArrayList) intent.getParcelableArrayListExtra(PARAM_FILES),
//                    notificationConfig));
        }
    }

    public static void startUpload(UploadTaskInfo request) {
        request.startUpload();
    }

    public void doUpload(UploadTaskInfo uploadTaskInfo) {
        recorder.addTask(uploadTaskInfo);
        OSSManager ossManager = new OSSManager(this.getApplicationContext(), SERVER_ADDRESS, END_POINT, BUCKET_NAME, uploadTaskInfo);
        try {
            ossManager.upload(new SaveCallback() {
                @Override
                public void onSuccess(String objectKey) {
                    Log.d(TAG, "[onSuccess] - " + objectKey + " upload success!");
                    String[] taskIds = recorder.getTaskIDs(objectKey);
                    for (String taskId : taskIds) {
                        int totalCount = recorder.getTaskFileCount(taskId);
                        int unuploadCount = recorder.oneFileCompleted(taskId, objectKey);
                        Log.d(TAG, "onSuccess  taskid = " + taskId + "      totalCount = " + totalCount + "      unuploadCount = " + unuploadCount);
                        if (unuploadCount == 0) {
                            recorder.deleteTask(taskId);
                            broadcastCompleted(taskId);
                        } else {
                            broadcastProgress(taskId, unuploadCount, totalCount);
                        }
                    }
                }

                @Override
                public void onProgress(String objectKey, int byteCount, int totalSize) {
                    Log.d(TAG, "[onProgress] - current upload " + objectKey + " bytes: " + byteCount + " in total: " + totalSize);
                    recorder.oneFileProgress(objectKey, byteCount, totalSize);
                    String[] taskIds = recorder.getTaskIDs(objectKey);
                    for (String taskId : taskIds) {
                        int totalCount = recorder.getTaskFileCount(taskId);
                        Log.d(TAG, "onProgress  taskid = " + taskId + "      totalCount = " + totalCount);
                        if (totalCount == 1) {
                            broadcastProgress(taskId, byteCount, totalSize);
                        }
                    }
                }

                @Override
                public void onFailure(String objectKey, OSSException ossException) {
                    Log.e(TAG, "[onFailure] - upload " + objectKey + " failed!\n" + ossException.toString());
                    recorder.oneFileFailure(objectKey);
                    String[] taskIds = recorder.getTaskIDs(objectKey);
                    for (String taskId : taskIds) {
                        broadcastError(taskId, ossException);
                    }
                }
            });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    void broadcastProgress(final String uploadId, final long uploadedCount, final long total) {

        long currentTime = System.currentTimeMillis();
        if (currentTime < lastProgressNotificationTime + PROGRESS_REPORT_INTERVAL) {
            return;
        }

        lastProgressNotificationTime = currentTime;

        updateNotificationProgress((int) uploadedCount, (int) total);

        final Intent intent = new Intent(getActionBroadcast());
        intent.putExtra(UPLOAD_ID, uploadId);
        intent.putExtra(STATUS, STATUS_IN_PROGRESS);

        final int percentsProgress = (int) (uploadedCount * 100 / total);
        intent.putExtra(PROGRESS, percentsProgress);

        intent.putExtra(PROGRESS_UPLOADED_BYTES, uploadedCount);
        intent.putExtra(PROGRESS_TOTAL_BYTES, total);
        sendBroadcast(intent);
    }

    void broadcastCompleted(final String uploadId) {
        updateNotificationCompleted();
        final Intent intent = new Intent(getActionBroadcast());
        intent.putExtra(UPLOAD_ID, uploadId);
        intent.putExtra(STATUS, STATUS_COMPLETED);
        sendBroadcast(intent);
        wakeLock.release();
    }

    void broadcastError(final String uploadId, final Exception exception) {
        updateNotificationError();

        final Intent intent = new Intent(getActionBroadcast());
        intent.setAction(getActionBroadcast());
        intent.putExtra(UPLOAD_ID, uploadId);
        intent.putExtra(STATUS, STATUS_ERROR);
        intent.putExtra(ERROR_EXCEPTION, exception);
        sendBroadcast(intent);
        wakeLock.release();
    }


    private void createNotification() {
        Log.d(TAG, "createNotification() called with: " + "");
        notification.setContentTitle(notificationConfig.getTitle()).setContentText(notificationConfig.getMessage())
                .setContentIntent(notificationConfig.getPendingIntent(this))
                .setSmallIcon(notificationConfig.getIconResourceID()).setProgress(100, 0, true).setOngoing(true);

        startForeground(UPLOAD_NOTIFICATION_ID, notification.build());
    }

    private void updateNotificationProgress(int uploadedBytes, int totalBytes) {
        Log.d(TAG, "updateNotificationProgress() called with: " + "uploadedBytes = [" + uploadedBytes + "], totalBytes = [" + totalBytes + "]");
        notification.setContentTitle(notificationConfig.getTitle()).setContentText(notificationConfig.getMessage())
                .setContentIntent(notificationConfig.getPendingIntent(this))
                .setSmallIcon(notificationConfig.getIconResourceID()).setProgress(totalBytes, uploadedBytes, false)
                .setOngoing(true);
        startForeground(UPLOAD_NOTIFICATION_ID, notification.build());
    }

    private void updateNotificationCompleted() {
        Log.d(TAG, "updateNotificationCompleted() called with: " + "");
        stopForeground(notificationConfig.isAutoClearOnSuccess());

        if (!notificationConfig.isAutoClearOnSuccess()) {
            notification.setContentTitle(notificationConfig.getTitle())
                    .setContentText(notificationConfig.getCompleted())
                    .setContentIntent(notificationConfig.getPendingIntent(this))
                    .setSmallIcon(notificationConfig.getIconResourceID()).setProgress(0, 0, false).setOngoing(false);
            setRingtone();
            notificationManager.notify(UPLOAD_NOTIFICATION_ID_DONE, notification.build());
        }
    }

    private void updateNotificationError() {
        Log.d(TAG, "updateNotificationError() called with: " + "");
        stopForeground(false);

        notification.setContentTitle(notificationConfig.getTitle()).setContentText(notificationConfig.getError())
                .setContentIntent(notificationConfig.getPendingIntent(this))
                .setSmallIcon(notificationConfig.getIconResourceID()).setProgress(0, 0, false).setOngoing(false);
        setRingtone();
        notificationManager.notify(UPLOAD_NOTIFICATION_ID_DONE, notification.build());
    }

    private void setRingtone() {
        Log.d(TAG, "setRingtone() called with: " + "");
        if (notificationConfig.isRingTone()) {
            notification.setSound(RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION));
            notification.setOnlyAlertOnce(false);
        }
    }
}
