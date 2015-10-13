package cn.timeface.tfupload;

/**
 * author: rayboot  Created on 15/10/10.
 * email : sy0725work@gmail.com
 */
public abstract class Recorder {

    //删除所有记录
    public abstract void clear();

    //删除记录
    public abstract void deleteTask(String taskId);

    //添加任务
    public abstract void addTask(UploadTaskInfo uploadTaskInfo);

    //完成某一个file,返回还剩几个文件没传完
    public abstract int oneFileCompleted(String taskId, String objectKey);

    //某一个file的进度
    public abstract void oneFileProgress(String objectKey, int byteCount, int totalSize);

    //某一个file失败
    public abstract void oneFileFailure(String objectKey);

    public abstract String[] getTaskIDs(String objectKey);

    public abstract int getTaskFileCount(String taskId);

    public abstract String getCurrentTaskId();
}
