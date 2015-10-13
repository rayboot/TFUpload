package cn.timeface.tfupload;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

/**
 * author: rayboot  Created on 15/9/28.
 * email : sy0725work@gmail.com
 */
public abstract class UploadFileObj implements Parcelable {
    protected String filePath;
    protected String folder;

    public UploadFileObj(String filePath, String folder) {
        this.filePath = filePath;
        this.folder = folder;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public File getFinalUploadFile() {
        return new File(filePath);
    }

    public String getFilePath() {
        return filePath;
    }

    public File getFile() {
        return new File(filePath);
    }

    public abstract String getObjectKey();

}
