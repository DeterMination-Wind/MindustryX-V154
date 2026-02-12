package mindustryX.features;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.provider.Settings;
import androidx.core.content.*;
import mindustry.*;

import java.io.*;

public class ApkInstallHelper{
    private static final int REQUEST_INSTALL_PERMISSION = 1234;

    private Activity activity;
    private File pendingApkFile;

    public ApkInstallHelper(Activity activity){
        this.activity = activity;
    }

    /**
     * 安装APK
     */
    public void installApk(File file){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()){
            pendingApkFile = file;
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, REQUEST_INSTALL_PERMISSION);
        }else{
            installApkInternal(file);
        }
    }

    /**
     * 处理权限请求结果
     */
    public void onActivityResult(int requestCode, int resultCode){
        if(requestCode == REQUEST_INSTALL_PERMISSION && resultCode == Activity.RESULT_OK && pendingApkFile != null){
            installApkInternal(pendingApkFile);
            pendingApkFile = null;
        }
    }

    /**
     * 执行APK安装
     */
    private void installApkInternal(File file){
        try{
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri;

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }else{
                uri = Uri.fromFile(file);
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        }catch(Exception e){
            Vars.ui.showException("安装APK失败", e);
        }
    }
}