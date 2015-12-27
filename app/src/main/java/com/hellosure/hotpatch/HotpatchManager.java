package com.hellosure.hotpatch;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.taobao.android.dexposed.DexposedBridge;
import com.taobao.patch.PatchMain;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * HotpatchManager,do the follows：
 * 1.Get Patch information.(md5 and download url of patch apk)
 * 2.Patch apk validate(md5 check and compare)
 * 3.load Patch
 */
public class HotpatchManager {
    private Context mContext;
    private String hostUrl = "http://192.168.0.100:8080/PathServer/patch?version=";

    public boolean init(Context context) {
        this.mContext = context;

        boolean isSupport = DexposedBridge.canDexposed(mContext);
        if (isSupport) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String version = Utils.getVersionName(mContext);
                    hostUrl = hostUrl + version;
                    GetPatchInfoTask task = new GetPatchInfoTask(hostUrl);
                    task.execute();
                }
            }).start();
        }
        return isSupport;
    }

    public final class DownloadTask extends AsyncTask<String, Integer, String> {
        private PatchInfo info;

        public DownloadTask(PatchInfo info) {
            this.info = info;
        }

        @Override
        protected String doInBackground(String... params) {
            InputStream is = null;
            FileOutputStream fos = null;
            String filePath = Utils.getCacheApkFilePath(mContext, info.apkFileUrl);

            try {
                is = new URL(info.apkFileUrl).openConnection().getInputStream();
                File file = new File(filePath);
                if (!file.exists()) {
                    file.createNewFile();
                }
                fos = new FileOutputStream(file);
                int byteCount;
                byte[] buffer = new byte[1024];
                while ((byteCount = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, byteCount);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != is) {
                    try {
                        is.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (null != fos) {
                    try {
                        fos.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return filePath;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            loadPath(info);
        }
    }

    private void loadPath(PatchInfo info) {
        if (Utils.isSignEqual(mContext, info.apkFileUrl) && TextUtils.equals(info.apkMd5, Utils.getMd5ByFile(new File(info.apkFileUrl)))) {
            PatchMain.load(mContext, info.apkFileUrl, null);
        }
    }

    class GetPatchInfoTask extends AsyncTask<String, Integer, String> {
        private String requestUrl;

        public GetPatchInfoTask(String url) {
            this.requestUrl = url;
        }

        @Override
        protected String doInBackground(String... params) {
            StringBuilder result = new StringBuilder();
            InputStream is = null;
            try {
                is = new URL(requestUrl).openConnection().getInputStream();

                InputStreamReader reader = new InputStreamReader(is);
                BufferedReader bufferedReader = new BufferedReader(reader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    result.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != is) {
                    try {
                        is.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return result.toString();
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if (!TextUtils.isEmpty(s)) {
                try {
                    JSONObject object = new JSONObject(s);
                    String md5 = object.get("md5").toString();
                    String url = object.get("patchApkUrl").toString();

                    PatchInfo info = new PatchInfo();
                    info.apkMd5 = md5;
                    info.apkFileUrl = url;

                    String apkPath = Utils.getCacheApkFilePath(mContext, info.apkFileUrl);
                    File file = new File(apkPath);
                    if (file.exists()) {
                        loadPath(info);
                    } else {
                        DownloadTask task = new DownloadTask(info);
                        task.execute();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}
