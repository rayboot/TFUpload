package cn.timeface.tfupload.oss;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.alibaba.sdk.android.oss.OSSService;
import com.alibaba.sdk.android.oss.OSSServiceProvider;
import com.alibaba.sdk.android.oss.callback.DeleteCallback;
import com.alibaba.sdk.android.oss.callback.GetFileCallback;
import com.alibaba.sdk.android.oss.callback.SaveCallback;
import com.alibaba.sdk.android.oss.model.AccessControlList;
import com.alibaba.sdk.android.oss.model.AuthenticationType;
import com.alibaba.sdk.android.oss.model.ClientConfiguration;
import com.alibaba.sdk.android.oss.model.OSSException;
import com.alibaba.sdk.android.oss.model.OSSFederationToken;
import com.alibaba.sdk.android.oss.model.StsTokenGetter;
import com.alibaba.sdk.android.oss.storage.OSSBucket;
import com.alibaba.sdk.android.oss.storage.OSSFile;
import com.alibaba.sdk.android.oss.util.OSSLog;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.FileNotFoundException;
import java.io.IOException;

import cn.timeface.tfupload.BuildConfig;
import cn.timeface.tfupload.UploadFileObj;
import cn.timeface.tfupload.oss.token.FederationToken;
import cn.timeface.tfupload.oss.token.FederationTokenGetter;

/**
 * author: rayboot  Created on 15/9/23.
 * email : sy0725work@gmail.com
 */
public class OSSManager {

    protected final String TAG = "OSSManager";

    protected Context context;
    protected String serverAddress;
    protected String endPoint;
    protected String bucketName;
    protected OSSService ossService;
    protected OSSBucket bucket;


    public OSSManager(Context context, String serverAddress, String endPoint, String bucketName) {
        this.context = context;
        this.serverAddress = serverAddress;
        this.endPoint = endPoint;
        this.bucketName = bucketName;

        initOssService(context);
        bucket = ossService.getOssBucket(this.bucketName);
    }

    /**
     * 初始化OSSService
     * @param context
     */
    private void initOssService(Context context) {
        ossService = OSSServiceProvider.getService();

        ossService.setApplicationContext(context);
        ossService.setGlobalDefaultACL(AccessControlList.PRIVATE); // 默认为private
        ossService.setAuthenticationType(AuthenticationType.FEDERATION_TOKEN);
        ossService.setGlobalDefaultHostId(endPoint);

        // 打开调试log
        if (BuildConfig.DEBUG) {
            OSSLog.enableLog();
        }

        ossService.setGlobalDefaultStsTokenGetter(new StsTokenGetter() {
            @Override
            public OSSFederationToken getFederationToken() {
                // 为指定的用户拿取服务其授权需求的FederationToken
                FederationToken token = FederationTokenGetter.getToken(serverAddress);
                if (token == null) {
                    Log.e(TAG, "获取FederationToken失败!!!");
                    Toast.makeText(OSSManager.this.context, "获取FederationToken失败!!!", Toast.LENGTH_SHORT).show();
                    return null;
                }
                return new OSSFederationToken(token.getAk(), token.getSk(), token.getToken(), token.getExpiration());
                // 将FederationToken设置到OSSService中
            }
        });
        ossService.setCustomStandardTimeWithEpochSec(System.currentTimeMillis() / 1000);


        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectTimeout(30 * 1000); // 设置全局网络连接超时时间，默认30s
        conf.setSocketTimeout(30 * 1000); // 设置全局socket超时时间，默认30s
        conf.setMaxConcurrentTaskNum(1); // 替换设置最大连接数接口，设置全局最大并发任务数，默认为6
        conf.setIsSecurityTunnelRequired(false); // 是否使用https，默认为false
        ossService.setClientConfiguration(conf);
    }

    /**
     * 异步删除指定key的文件
     * @param objectKey 阿里云上的objectKey
     * @param deleteCallback 回调参数
     */
    public void delete(String objectKey, DeleteCallback deleteCallback) {
        OSSFile ossFile = ossService.getOssFile(bucket, objectKey);
        ossFile.deleteInBackground(deleteCallback);
    }

    /**
     * 异步上传文件
     * @param objectKey 阿里云objectKey
     * @param uploadFilePath 上传文件的路径
     * @param saveCallback 上传的回调事件
     * @throws FileNotFoundException
     */
    public void upload(String objectKey, String uploadFilePath, SaveCallback saveCallback) throws FileNotFoundException {
        OSSFile ossFile = ossService.getOssFile(bucket, objectKey);
        ossFile.setUploadFilePath(uploadFilePath, "application/octet-stream");
        ossFile.ResumableUploadInBackground(saveCallback);
    }

    /**
     * 同步上传文件
     * @param objectKey  阿里云objectKey
     * @param uploadFilePath 上传文件的路径
     * @throws FileNotFoundException
     * @throws OSSException
     */
    public void upload(String objectKey, String uploadFilePath) throws FileNotFoundException, OSSException {
        OSSFile ossFile = ossService.getOssFile(bucket, objectKey);
        ossFile.setUploadFilePath(uploadFilePath, "application/octet-stream");
        ossFile.upload();
    }

    /**
     * 同步检测该文件是否在阿里云上存在
     *
     * @param objectKey object key
     * @return true 已存在
     */
    public boolean checkFileExist(String objectKey) {
        OkHttpClient httpClient = new OkHttpClient();
        String url = String.format("http://%s.%s/%s", this.bucketName, this.endPoint, objectKey);
        Request request = new Request.Builder().head()
                .url(url)
                .build();
        Response response = null;
        try {
            response = httpClient.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return response != null && (response.code() == 200);
    }

    /**
     * 同步下载
     *
     * @param objectKey 阿里云objectKey
     * @param downloadFilePath 文件下载路径
     * @throws OSSException
     */
    public void download(String objectKey, String downloadFilePath) throws OSSException {
        OSSFile ossFile = ossService.getOssFile(bucket, objectKey);
        ossFile.downloadTo(downloadFilePath);
    }

    /**
     * 异步下载
     *
     * @param objectKey 阿里云objectKey
     * @param downloadFilePath 文件下载路径
     * @param getFileCallback 回调事件
     */
    public void download(String objectKey, String downloadFilePath, GetFileCallback getFileCallback) {
        OSSFile ossFile = ossService.getOssFile(bucket, objectKey);
        ossFile.downloadToInBackground(downloadFilePath, getFileCallback);
    }

}
