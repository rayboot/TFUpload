package cn.timeface.tfupload.sample;

import android.os.Parcel;

import cn.timeface.tfupload.UploadFileObj;

/**
 * author: rayboot  Created on 15/10/10.
 * email : sy0725work@gmail.com
 */
public class UploadFile extends UploadFileObj {
    public UploadFile(String filePath, String folder) {
        super(filePath, folder);
    }

    @Override
    public String getObjectKey() {
        return folder + "/" + this.filePath.hashCode() + ".jpg";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.filePath);
        dest.writeString(this.folder);
    }

    protected UploadFile(Parcel in) {
        super(in.readString(), in.readString());
    }

    public static final Creator<UploadFileObj> CREATOR = new Creator<UploadFileObj>() {
        public UploadFileObj createFromParcel(Parcel source) {
            return new UploadFile(source);
        }

        public UploadFileObj[] newArray(int size) {
            return new UploadFileObj[size];
        }
    };

}
