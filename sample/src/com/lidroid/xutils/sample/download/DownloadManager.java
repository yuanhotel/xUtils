package com.lidroid.xutils.sample.download;

import android.content.Context;
import android.database.Cursor;
import com.lidroid.xutils.DbUtils;
import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.db.converter.ColumnConverter;
import com.lidroid.xutils.db.converter.ColumnConverterFactory;
import com.lidroid.xutils.db.sqlite.Selector;
import com.lidroid.xutils.exception.DbException;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.HttpHandler;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.lidroid.xutils.util.LogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: wyouflf
 * Date: 13-11-10
 * Time: 下午8:10
 */
public class DownloadManager {

    private List<DownloadInfo> downloadInfoList;

    private int maxDownloadThread = 3;

    private Context mContext;
    private DbUtils db;

    public DownloadManager(Context appContext) {
        ColumnConverterFactory.registerColumnConverter(HttpHandler.State.class, new HttpHandlerStateConverter());
        mContext = appContext;
        db = DbUtils.create(mContext);
        try {
            downloadInfoList = db.findAll(Selector.from(DownloadInfo.class));
        } catch (DbException e) {
            LogUtils.e(e.getMessage(), e);
        }
        if (downloadInfoList == null) {
            downloadInfoList = new ArrayList<DownloadInfo>();
        }
    }

    public int getDownloadInfoListCount() {
        return downloadInfoList.size();
    }

    public DownloadInfo getDownloadInfo(int index) {
        return downloadInfoList.get(index);
    }

    public void addNewDownload(String url, String fileName, String target,
                               boolean autoResume, boolean autoRename, final RequestCallBack<File> callback) {
        final DownloadInfo downloadInfo = new DownloadInfo();
        downloadInfo.setDownloadUrl(url);
        downloadInfo.setAutoRename(autoRename);
        downloadInfo.setAutoResume(autoResume);
        downloadInfo.setFileName(fileName);
        downloadInfo.setFileSavePath(target);
        HttpUtils http = new HttpUtils();
        http.configRequestThreadPoolSize(maxDownloadThread);
        HttpHandler<File> handler = http.download(url, target, autoResume, autoRename, new DownloadCallBack(downloadInfo, callback));
        downloadInfo.setHandler(handler);
        downloadInfo.setState(handler.getState());
        downloadInfoList.add(downloadInfo);
    }

    public HttpHandler.State getDownloadState(int index) {
        return downloadInfoList.get(index).getState();
    }

    public void resumeDownload(int index, final RequestCallBack<File> callback) {
        final DownloadInfo downloadInfo = downloadInfoList.get(index);
        HttpUtils http = new HttpUtils();
        http.configRequestThreadPoolSize(maxDownloadThread);
        HttpHandler<File> handler = http.download(
                downloadInfo.getDownloadUrl(),
                downloadInfo.getFileSavePath(),
                downloadInfo.isAutoResume(),
                downloadInfo.isAutoRename(),
                new DownloadCallBack(downloadInfo, callback));
        downloadInfo.setHandler(handler);
        downloadInfo.setState(handler.getState());
    }

    public void removeDownload(int index) throws DbException {
        HttpHandler<File> handler = downloadInfoList.get(index).getHandler();
        if (handler != null && !handler.isStopped()) {
            handler.stop();
        }
        db.delete(downloadInfoList.get(index));
        downloadInfoList.remove(index);
    }

    public void stopDownload(int index) {
        HttpHandler<File> handler = downloadInfoList.get(index).getHandler();
        if (handler != null && !handler.isStopped()) {
            handler.stop();
        }
    }

    public void stopAllDownload() {
        for (DownloadInfo downloadInfo : downloadInfoList) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null && !handler.isStopped()) {
                handler.stop();
            }
        }
    }

    public void backupDownloadInfoList() throws DbException {
        for (DownloadInfo downloadInfo : downloadInfoList) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }
        }
        db.saveOrUpdateAll(downloadInfoList);
    }

    public int getMaxDownloadThread() {
        return maxDownloadThread;
    }

    public void setMaxDownloadThread(int maxDownloadThread) {
        this.maxDownloadThread = maxDownloadThread;
    }

    private class DownloadCallBack extends RequestCallBack<File> {
        private DownloadInfo downloadInfo;
        private RequestCallBack<File> callBack;

        private DownloadCallBack(DownloadInfo downloadInfo, RequestCallBack<File> callBack) {
            this.callBack = callBack;
            this.downloadInfo = downloadInfo;
        }

        @Override
        public Object getUserTag() {
            return callBack.getUserTag();
        }

        @Override
        public void setUserTag(Object userTag) {
            callBack.setUserTag(userTag);
        }

        @Override
        public void onStart() {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }
            try {
                db.saveOrUpdate(downloadInfo);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
            callBack.onStart();
        }

        @Override
        public void onStopped() {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }
            try {
                db.saveOrUpdate(downloadInfo);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
            callBack.onStopped();
        }

        @Override
        public void onLoading(long total, long current, boolean isUploading) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }
            downloadInfo.setFileLength(total);
            try {
                db.saveOrUpdate(downloadInfo);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
            callBack.onLoading(total, current, isUploading);
        }

        @Override
        public void onSuccess(ResponseInfo<File> responseInfo) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }
            try {
                db.saveOrUpdate(downloadInfo);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
            callBack.onSuccess(responseInfo);
        }

        @Override
        public void onFailure(HttpException error, String msg) {
            HttpHandler<File> handler = downloadInfo.getHandler();
            if (handler != null) {
                downloadInfo.setState(handler.getState());
            }
            try {
                db.saveOrUpdate(downloadInfo);
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
            callBack.onFailure(error, msg);
        }
    }

    private class HttpHandlerStateConverter implements ColumnConverter<HttpHandler.State> {

        @Override
        public HttpHandler.State getFiledValue(Cursor cursor, int index) {
            return HttpHandler.State.valueOf(cursor.getInt(index));
        }

        @Override
        public HttpHandler.State getFiledValue(String fieldStringValue) {
            if (fieldStringValue == null) return null;
            return HttpHandler.State.valueOf(fieldStringValue);
        }

        @Override
        public Object fieldValue2ColumnValue(HttpHandler.State fieldValue) {
            return fieldValue.value();
        }

        @Override
        public String getColumnDbType() {
            return "INTEGER";
        }
    }
}
