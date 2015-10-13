package cn.timeface.tfupload.sample;

import android.text.TextUtils;

import com.tumblr.remember.Remember;

import cn.timeface.tfupload.Recorder;
import cn.timeface.tfupload.UploadFileObj;
import cn.timeface.tfupload.UploadTaskInfo;

/**
 * current_task_id  -> taskid1
 * all_task ->  taskid1 , taskid2 , taskid3
 * taskid_total_count  -> 10
 * objkey1_key_task_list  ->  taskid1 , taskid2 , taskid3
 * taskid1_objkey1_key_info  ->  filepath , foldername
 * taskid1_task_key_list  -> objkey1 , objkey2 , objkey3
 *
 * author: rayboot  Created on 15/10/10.
 * email : sy0725work@gmail.com
 */
public class SimpleRecorder extends Recorder {

    static final String ALL_TASK = "all_task";
    static final String TOTAL_COUNT_SUFFIX = "_total_count";
    static final String TASK_KEY_LIST = "_task_key_list";
    static final String KEY_TASK_LIST = "_key_task_list";
    static final String KEY_INFO = "_key_info";
    static final String CURRENT_TASK_ID = "current_task_id";

    @Override
    public void clear() {
        String allTasks = Remember.getString(ALL_TASK, null);
        if (TextUtils.isEmpty(allTasks)) {
            return;
        }
        String[] tasks = allTasks.split(",");
        if (tasks.length == 0) {
            return;
        }
        for (String task : tasks) {
            deleteTask(task);
        }
        Remember.remove(ALL_TASK);
    }

    @Override
    public void deleteTask(String taskId) {
        //删除主队列中的taskId
        deleteValue(ALL_TASK, taskId);

        //删除任务包含文件的个数字段
        Remember.remove(taskId + TOTAL_COUNT_SUFFIX);

        String[] objkeys = Remember.getString(taskId+TASK_KEY_LIST, "").split(",");
        for (String key : objkeys) {
            Remember.remove(taskId + "_" + key + KEY_INFO);
            Remember.remove(key + KEY_TASK_LIST);
        }

        //删除任务对应的所有key
        Remember.remove(taskId + TASK_KEY_LIST);
    }

    @Override
    public void addTask(UploadTaskInfo uploadTaskInfo) {
        //all_task
        appendValue(ALL_TASK, uploadTaskInfo.getInfoId());

        //id_total_count -> 任务包含多少文件上传子任务
        Remember.putInt(uploadTaskInfo.getInfoId() + TOTAL_COUNT_SUFFIX, uploadTaskInfo.getFileObjs().size());
        //id_task_key_list -> 单任务包含的所有文件objectkey(多个key   ,   分割)
        StringBuilder allKeys = new StringBuilder();
        for (UploadFileObj fileObj : uploadTaskInfo.getFileObjs()) {
            allKeys.append(fileObj.getObjectKey());
            allKeys.append(",");

            //key_key_task_list -> 所有有该key的任务
            appendValue(fileObj.getObjectKey() + KEY_TASK_LIST, uploadTaskInfo.getInfoId());

            //taskid_key_info -> key的信息
            Remember.putString(uploadTaskInfo.getInfoId() + "_" + fileObj.getObjectKey() + KEY_INFO, fileObj.getFilePath() + "," + fileObj.getFolder());

        }
        if (allKeys.length() > 0) {
            allKeys.deleteCharAt(allKeys.length() - 1);
        }
        Remember.putString(uploadTaskInfo.getInfoId() + TASK_KEY_LIST, allKeys.toString());
    }

    @Override
    public int oneFileCompleted(String taskId, String objectKey) {
        //删除key 对应的任务
        deleteValue(objectKey + KEY_TASK_LIST, taskId);

        //删除文件信息 taskid_key_info
        Remember.remove(taskId + "_" + objectKey + KEY_INFO);

        //删除任务队列里的objkey
        return deleteValue(taskId + TASK_KEY_LIST, objectKey);
    }

    @Override
    public void oneFileProgress(String objectKey, int byteCount, int totalSize) {

    }

    @Override
    public void oneFileFailure(String objectKey) {

    }

    @Override
    public String[] getTaskIDs(String objectKey) {
        String allTasks = Remember.getString(objectKey + KEY_TASK_LIST, null);
        if (TextUtils.isEmpty(allTasks)) {
            return null;
        }
        return allTasks.split(",");
    }

    @Override
    public int getTaskFileCount(String taskId) {
        return Remember.getInt(taskId + TOTAL_COUNT_SUFFIX, 1);
    }

    @Override
    public String getCurrentTaskId() {
        return Remember.getString(CURRENT_TASK_ID, null);
    }

    /**
     * 对key追加value
     * 多个value使用 , 分割
     *
     * @param key
     * @param value
     */
    private void appendValue(String key, String value) {
        if (checkValue(key, value)) {
            return;
        }

        String result = Remember.getString(key, "");
        if (TextUtils.isEmpty(result)) {
            result = value;
        } else {
            result = result + "," + value;
        }
        Remember.putString(key, result);
    }

    /**
     * 对key删除value
     * 多个value使用 , 分割
     *
     * @param key
     * @param value
     * @return 该key还有多少个value
     */
    private int deleteValue(String key, String value) {
        String allValues = Remember.getString(key, null);
        if (TextUtils.isEmpty(allValues)) {
            return 0;
        }
        String[] values = allValues.split(",");
        if (!checkValue(key, value)) {
            return values.length;
        }

        int count = 0;
        StringBuilder result = new StringBuilder();
        for (String val : values) {
            if (val.equals(value)) {
                continue;
            }
            result.append(val);
            result.append(",");
            count++;
        }

        if (count == 0) {
            Remember.remove(key);
            return 0;
        }

        if (count > 0) {
            result.deleteCharAt(result.length() - 1);
        }
        Remember.putString(key, result.toString());
        return count;
    }

    /**
     * 对key存储的对象，检测是否有value
     * 多个value使用 , 分割
     *
     * @param key
     * @param value
     */
    private boolean checkValue(String key, String value) {
        String allValues = Remember.getString(key, null);
        if (TextUtils.isEmpty(allValues)) {
            return false;
        }
        String[] values = allValues.split(",");
        if (values.length <= 0) {
            return false;
        }
        for (String val : values) {
            if (val.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
