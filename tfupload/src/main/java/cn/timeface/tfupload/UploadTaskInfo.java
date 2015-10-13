package cn.timeface.tfupload;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * author: rayboot  Created on 15/10/10.
 * email : sy0725work@gmail.com
 */
public class UploadTaskInfo {

    Context context;
    String infoId;
    ArrayList<UploadFileObj> fileObjs = new ArrayList<>(10);
    private UploadNotificationConfig notificationConfig;

    public UploadTaskInfo(String infoId, ArrayList<UploadFileObj> fileObjs, UploadNotificationConfig uploadNotificationConfig) {
        this.infoId = infoId;
        this.fileObjs = fileObjs;
        this.notificationConfig = uploadNotificationConfig;
    }
    public UploadTaskInfo(String infoId, ArrayList<UploadFileObj> fileObjs) {
        this.infoId = infoId;
        this.fileObjs = fileObjs;
    }


    /**
     * Start the background file upload service.
     */
    public void startUpload() {
        final Intent intent = new Intent(this.getContext(), TFUploadService.class);
        this.initializeIntent(intent);
        intent.setAction(TFUploadService.getActionUpload());
        getContext().startService(intent);
    }

    /**
     * Write any upload request data to the intent used to start the upload service.
     *
     * @param intent the intent used to start the upload service
     */
    protected void initializeIntent(Intent intent) {
        intent.setAction(TFUploadService.getActionUpload());
        intent.putExtra(TFUploadService.PARAM_NOTIFICATION_CONFIG, getNotificationConfig());
        intent.putExtra(TFUploadService.PARAM_ID, getInfoId());
        intent.putParcelableArrayListExtra(TFUploadService.PARAM_FILES, getFileObjs());
    }

    public String getInfoId() {
        return infoId;
    }

    public void setInfoId(String infoId) {
        this.infoId = infoId;
    }

    public ArrayList<UploadFileObj> getFileObjs() {
        return fileObjs;
    }

    public void setFileObjs(ArrayList<UploadFileObj> fileObjs) {
        this.fileObjs = fileObjs;
    }


    /**
     * Sets custom notification configuration.
     *
     * @param iconResourceID     ID of the notification icon. You can use your own app's R.drawable.your_resource
     * @param title              Notification title
     * @param message            Text displayed in the notification when the upload is in progress
     * @param completed          Text displayed in the notification when the upload is completed successfully
     * @param error              Text displayed in the notification when an error occurs
     * @param autoClearOnSuccess true if you want to automatically clear the notification when the upload gets completed
     *                           successfully
     */
    public void setNotificationConfig(final int iconResourceID, final String title, final String message,
                                      final String completed, final String error, final boolean autoClearOnSuccess) {
        notificationConfig = new UploadNotificationConfig(iconResourceID, title, message, completed, error,
                autoClearOnSuccess);
    }

    /**
     * Gets the upload notification configuration.
     *
     * @return
     */
    protected UploadNotificationConfig getNotificationConfig() {
        return notificationConfig;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }
}
