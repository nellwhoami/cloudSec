package com.common.qiniu;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.http.HttpUtil;
import com.common.qiniu.base.BaseAuth;
import com.common.qiniu.base.model.BucketModel;
import com.domain.Key;
import com.domain.Task;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.*;
import com.qiniu.storage.model.BucketInfo;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.storage.model.FileListing;
import com.qiniu.util.Auth;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class Bucket {
    private static final Configuration configuration = new Configuration();

    public static String downLoadFile(Auth auth, com.domain.Bucket bucket, String keyName, boolean useHttps) {
        String endPoint = bucket.getEndPoint();
        String[] domainLists = endPoint.substring(1, endPoint.length() - 1).split(",");
        Random random = new Random();
        String randomElement = domainLists[random.nextInt(domainLists.length)];
        DownloadUrl url = new DownloadUrl(StrUtil.trim(randomElement), useHttps, keyName);
        try {
            String s = url.buildURL(auth, System.currentTimeMillis() / 1000 + 3600);
            return s;
        } catch (QiniuException e) {
            return "创建下载链接失败";
        }
    }

    public static Task downAllFile(Key key, com.domain.Bucket bucket, Task task){
        Auth auth = BaseAuth.getAuth(key);
        long current = DateUtil.current();
        String path = "../../" + current;
        File dir = FileUtil.mkdir(path);
        List<FileListing> fileLists = getFileLists(bucket, auth, null);
        for (FileListing fileList : fileLists) {
            for (FileInfo item : fileList.items) {
                String s = downLoadFile(auth, bucket, item.key, false);
                File file = new File(dir + "\\" + item.key.replace("/", "."));
                try {
                    HttpUtil.downloadFile(s,file);
                }catch (Exception e){
                    System.out.println(item.key + "---下载失败原因：" + e.getMessage());
                }
            }
        }
        File zip = ZipUtil.zip(dir.getPath(), FileUtil.createTempFile(bucket.getName(), ".zip", true).getPath());
        task.setStatus("成功");
        task.setFilename(zip.getName());
        task.setFilePath(zip.getAbsolutePath());
        FileUtil.del(dir);
        return task;
    }

    public static List<BucketModel>  getBucketLists(Auth auth){
        List<BucketModel> bucketModels = new ArrayList<>();
        BucketManager bucketManager = new BucketManager(auth,configuration);
        try {
            String[] buckets = bucketManager.buckets();
            for (String bucket : buckets) {
                BucketInfo bucketInfo = bucketManager.getBucketInfo(bucket);
                String[] domainList = bucketManager.domainList(bucket);
                BucketModel bucketModel = new BucketModel(bucket, bucketInfo.getRegion(), domainList);
                bucketModels.add(bucketModel);
            }
        } catch (QiniuException e) {
            System.out.println(e.getMessage());
        }
        return bucketModels;
    }
    public static boolean uploadFile(Auth auth, com.domain.Bucket bucket, String fileName, File file) throws QiniuException {
        String token = auth.uploadToken(bucket.getName());
        UploadManager uploadManager = new UploadManager(configuration);
        Response put = uploadManager.put(file, StrUtil.isBlankIfStr(fileName) ? "" : file.getName(), token);
        if (put.statusCode == 200) {
            System.out.println(put.bodyString());
            return true;
        }
        return false;
    }

    public static List<FileListing> getFileLists(com.domain.Bucket bucket,Auth auth,String prefix){
        BucketManager bucketManager = new BucketManager(auth,configuration);
        List<FileListing> listings = new ArrayList<>();
        while (true){
            try {
                FileListing fileListing = bucketManager.listFiles(bucket.getName(), StrUtil.isBlankIfStr(prefix) ? "" : prefix, "", 1000, "");
                listings.add(fileListing);
                if (fileListing.isEOF()){
                    return listings;
                }
            } catch (QiniuException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
