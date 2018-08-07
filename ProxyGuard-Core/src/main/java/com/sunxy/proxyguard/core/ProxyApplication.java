package com.sunxy.proxyguard.core;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * -- 代理application
 * 工作：
 * //解密与加载多个dex
 * //替换真实的Application
 * <p>
 * Created by sunxy on 2018/8/7 0007.
 */
public class ProxyApplication extends Application {

    private String app_name;
    private String app_version;


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        getMetaData();
        Log.v("sunxy", "app_name: " + app_name );
        Log.v("sunxy", "app_version: " + app_version );

        //获取当前APK文件
        File apkFile = new File(getApplicationInfo().sourceDir);
        Log.v("sunxy", "apkFile: " + apkFile.getAbsolutePath() );
        // apk 文件减压到 appDir这个目录
        File rootFile = getDir(app_name, MODE_PRIVATE);
        File versionDir = new File(rootFile, app_version);
        Log.v("sunxy", "versionDir: " + versionDir.getAbsolutePath() );
        File appDir = new File(versionDir, "app");
        //提取apk中需要解密的所有dex文件放入这个目录。
        File dexDir = new File(versionDir, "dexDir");
        //
        List<File> dexFiles = new ArrayList<>();
        // 如果dexDir不存在或者里面没东西，则去解压
        if (!dexDir.exists() || dexDir.list().length == 0){
            Log.v("sunxy", "没有解压过 ");
            //把apk解压 到 appDir
            Zip.upSip(apkFile, appDir);
            //获取目录下文件
            File[] files = appDir.listFiles();
            for (File file : files) {
                String name = file.getName();
                //文件名是 .dex结尾， 并且不是主dex 放入 dexDir 目录
                if (name.endsWith(".dex") && !TextUtils.equals(name, "classes.dex")){
                    try {
                        //从文件中读取 byte数组 加密后的dex数据
                        byte[] bytes = Utils.getBytes(file);
                        //将dex 文件 解密 并且写入 原文件file目录
                        Utils.decrypt(bytes, file.getAbsolutePath());
                        dexFiles.add(file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            //将dexFiles中的文件拷贝到dexDir中
            Log.v("sunxy", "准备copy size: " + dexFiles.size());
            Utils.copy(dexFiles, dexDir);
        }else{
            Log.v("sunxy", "已经解压过了 " + dexDir.listFiles().length);
            //已经解压过了
            Collections.addAll(dexFiles,  dexDir.listFiles());
        }

        try {
            loadDex(dexFiles, versionDir);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载dex
     */
    private void loadDex(List<File> dexFiles, File optimizedDirectory)
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        /**
         * 1 获取系统classLoader中的dexElements数组
         */
        Field pathListField = Utils.findField(getClassLoader(), "pathList");
        Object pathList = pathListField.get(getClassLoader());
        //取出pathList中的dexElement
        Field dexElementsFiled = Utils.findField(pathList, "dexElements");
        Object[] dexElements = (Object[]) dexElementsFiled.get(pathList);
        /**
         * 2 创建新的element数组， 解密后加载dex
         */
        Method makeDexElement;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.M){
            //5.0 - 6.0
            makeDexElement = Utils.findMethod(pathList, "makeDexElements",
                    ArrayList.class, File.class, ArrayList.class);
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            //大于 6.0
            makeDexElement = Utils.findMethod(pathList, "makePathElements",
                    List.class, File.class, List.class);
        }else{
            return;
        }
        ArrayList<IOException> suppressedExceptions = new ArrayList<>();
        Object[] addElements = (Object[]) makeDexElement
                .invoke(pathList, dexFiles, optimizedDirectory, suppressedExceptions);
        /**
         * 3 合并俩个element数组为一个
         */
        Object[] newElements = (Object[]) Array.newInstance(dexElements.getClass().getComponentType(),
                dexElements.length + addElements.length);
        System.arraycopy(dexElements, 0, newElements, 0, dexElements.length);
        System.arraycopy(addElements, 0, newElements, dexElements.length, addElements.length);
        /**
         * 4  替换classLoader中的element数组
         */
        dexElementsFiled.set(pathList, newElements);
    }

    /**
     * 获取用户在androidManifest.xml中配置的信息
     */
    public void getMetaData() {
        try {
            ApplicationInfo applicationInfo = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = applicationInfo.metaData;
            if (null != metaData){
                if(metaData.containsKey(Conts.APP_NAME)){
                    app_name = metaData.getString(Conts.APP_NAME);
                }
                if (metaData.containsKey(Conts.APP_VERSION)) {
                    app_version = metaData.get(Conts.APP_VERSION).toString();
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }
}
