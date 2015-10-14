package cn.timeface.tfupload;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.PowerManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

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
        notification = new NotificationCompat.Builder(getApplicationContext());
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

            doUpload(new UploadTaskInfo(intent.getStringExtra(PARAM_ID),
                    (ArrayList) intent.getParcelableArrayListExtra(PARAM_FILES),
                    notificationConfig));
        }
    }

    public static void startUploadService(UploadTaskInfo uploadTask) {
        Intent intent = new Intent(uploadTask.getContext(), TFUploadService.class);
        intent.setAction(TFUploadService.getActionUpload());
        intent.putExtra(TFUploadService.PARAM_NOTIFICATION_CONFIG, uploadTask.getNotificationConfig());
        intent.putExtra(TFUploadService.PARAM_ID, uploadTask.getInfoId());
        intent.putParcelableArrayListExtra(TFUploadService.PARAM_FILES, uploadTask.getFileObjs());
        intent.setAction(TFUploadService.getActionUpload());
        uploadTask.getContext().startService(intent);
    }

    public void doUpload(UploadTaskInfo uploadTaskInfo) {
        //添加任务列表
        recorder.addTask(uploadTaskInfo);

        OSSManager ossManager = new OSSManager(this.getApplicationContext(), SERVER_ADDRESS, END_POINT, BUCKET_NAME);
        int totalCount = uploadTaskInfo.fileObjs.size();
        for (int i = 0; i < totalCount; i++) {
            //更新进度条
            broadcastProgress(uploadTaskInfo.getInfoId(), i, totalCount);

            //获取上传文件
            UploadFileObj uploadFileObj = uploadTaskInfo.fileObjs.get(i);

            //上传操作
            try {
                //判断服务器是否已存在该文件
                if (!ossManager.checkFileExist(uploadFileObj)) {
                    //如果不存在则上传
                    ossManager.upload(uploadFileObj);
                }
                recorder.oneFileCompleted(uploadTaskInfo.getInfoId(), uploadFileObj.getObjectKey());
            } catch (OSSException e) {
                broadcastError(uploadTaskInfo.getInfoId(), e);
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                broadcastError(uploadTaskInfo.getInfoId(), e);
            }
        }

        //上传完成，清空任务列表
        recorder.deleteTask(uploadTaskInfo.getInfoId());
        broadcastCompleted(uploadTaskInfo.getInfoId());
    }

    void broadcastProgress(final String taskId, final long uploaded, final long total) {

        long currentTime = System.currentTimeMillis();
        if (currentTime < lastProgressNotificationTime + PROGRESS_REPORT_INTERVAL) {
            return;
        }

        lastProgressNotificationTime = currentTime;

        updateNotificationProgress((int) uploaded, (int) total);

        final Intent intent = new Intent(getActionBroadcast());
        intent.putExtra(UPLOAD_ID, taskId);
        intent.putExtra(STATUS, STATUS_IN_PROGRESS);

        final int percentsProgress = (int) (uploaded * 100 / total);
        intent.putExtra(PROGRESS, percentsProgress);

        intent.putExtra(PROGRESS_UPLOADED_BYTES, uploaded);
        intent.putExtra(PROGRESS_TOTAL_BYTES, total);
        sendBroadcast(intent);
    }

    void broadcastCompleted(final String taskId) {
        updateNotificationCompleted();
        final Intent intent = new Intent(getActionBroadcast());
        intent.putExtra(UPLOAD_ID, taskId);
        intent.putExtra(STATUS, STATUS_COMPLETED);
        sendBroadcast(intent);
        wakeLock.release();
    }

    void broadcastError(final String taskId, final Exception exception) {
        updateNotificationError();

        final Intent intent = new Intent(getActionBroadcast());
        intent.setAction(getActionBroadcast());
        intent.putExtra(UPLOAD_ID, taskId);
        intent.putExtra(STATUS, STATUS_ERROR);
        intent.putExtra(ERROR_EXCEPTION, exception);
        sendBroadcast(intent);
        wakeLock.release();
    }


    private void createNotification() {
        Log.d(TAG, "createNotification() called with: " + "");
        notification.setContentTitle(notificationConfig.getTitle())
                .setContentText(notificationConfig.getMessage())
                .setContentIntent(notificationConfig.getPendingIntent(this))
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setWhen(System.currentTimeMillis())  // the time stamp
                .setTicker("")
                .setProgress(100, 0, true)
                .setOngoing(true);

        startForeground(UPLOAD_NOTIFICATION_ID, notification.build());
    }

    private void updateNotificationProgress(int uploaded, int total) {
        Log.d(TAG, "updateNotificationProgress() called with: " + "uploadedBytes = [" + uploaded + "], totalBytes = [" + total + "]");
        notification.setContentTitle(notificationConfig.getTitle())
                .setContentText(notificationConfig.getMessage())
                .setContentIntent(notificationConfig.getPendingIntent(this))
                .setSmallIcon(notificationConfig.getIconResourceID())
                .setWhen(System.currentTimeMillis())  // the time stamp
                .setTicker("")
                .setProgress(total, uploaded, false)
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
