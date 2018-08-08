package com.sunxy.proxyguard.core;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

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
    private boolean isNeedLoadDex;

    Application delegate;


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        getMetaData();

        if (!isNeedLoadDex){
            return;
        }

        //获取当前APK文件
        File apkFile = new File(getApplicationInfo().sourceDir);
        // apk 文件减压到 appDir这个目录
        File rootDir = getDir(app_name, MODE_PRIVATE);
        File versionDir = new File(rootDir, app_version);
        File appDir = new File(versionDir, "app");
        //提取apk中需要解密的所有dex文件放入这个目录。
        File dexDir = new File(versionDir, "dexDir");
        //存储需要插入的dex
        List<File> dexFiles = new ArrayList<>();
        // 如果dexDir不存在或者里面没东西，则去解压
        if (!dexDir.exists() || dexDir.list().length == 0){
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
            Utils.copy(dexFiles, dexDir);
        }else{
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

    @Override
    public String getPackageName() {
        //如果有ContentProvider，则ContentProvider会在调用application的onCreate之前就
        //使用的获取content，但是content那是还没有被替换成目标的content
        // 这里返回空则会调用application.createPackageContext方法获取content
        if (isNeedLoadDex){
            return "";
        }
        return super.getPackageName();
    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        if (!isNeedLoadDex){
            return super.createPackageContext(packageName, flags);
        }
        try {
            bindRealApplication();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (delegate == null){
            return super.createPackageContext(packageName, flags);
        }
        return delegate;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            bindRealApplication();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        isNeedLoadDex = !TextUtils.isEmpty(app_name) && !TextUtils.isEmpty(app_version);
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
        System.arraycopy(addElements, 0, newElements, 0, addElements.length);
        System.arraycopy(dexElements, 0, newElements, addElements.length, dexElements.length);
        /**
         * 4  替换classLoader中的element数组
         */
        dexElementsFiled.set(pathList, newElements);
    }

    /**
     * 替换application
     *
     *
     Application创建出来后 给到了以下对象的以下成员：
     ContextImpl -> mOuterContext ProxyApplication
     ActivityThread -> mAllApplications(ArrayList) ProxyApplication
     ActivityThread -> mInitialApplication ProxyApplication
     LoadedApk -> mApplication ProxyApplication
     *
     */
    private void bindRealApplication() throws ClassNotFoundException,
            IllegalAccessException, InstantiationException, NoSuchMethodException,
            InvocationTargetException, NoSuchFieldException {
        if (!isNeedLoadDex){
            return;
        }

        if (delegate != null){
            return;
        }

        //这个就是attachBaseContext传进来的 ContextImpl
        Context baseContext = getBaseContext();

        //创建目标application
        Class<?> delegateClass = Class.forName(app_name);
        Application app = (Application) delegateClass.newInstance();
        //反正执行attach函数，该函数在源码中被 @hide 注解修饰，9.0能行吗？
        Method attach = Utils.findMethod(app, "attach", Context.class);
        attach.invoke(app, baseContext);

        /**
         * 替换 ContextImpl -> mOuterContext
         */
        Field mOuterContextField = Utils.findField(baseContext, "mOuterContext");
        mOuterContextField.set(baseContext, app);


        /**
         *  获得ActivityThread对象 ActivityThread
         *  可以通过 ContextImpl 的 mMainThread 属性获得
         */
        Field mMainThreadField = Utils.findField(baseContext, "mMainThread");
        Object mMainThread = mMainThreadField.get(baseContext);

        /**
         *  替换 ActivityThread -> mInitialApplication
         */
        Field mInitialApplicationField = Utils.findField(mMainThread, "mInitialApplication");
        mInitialApplicationField.set(mMainThread, app);

        /**
         * 替换 ActivityThread -> mAllApplications(ArrayList)
         */
        Field mAllApplicationsField = Utils.findField(mMainThread, "mAllApplications");
        ArrayList<Application> mAllApplications = (ArrayList<Application>) mAllApplicationsField.get(mMainThread);
        mAllApplications.remove(this);
        mAllApplications.add(app);

        /**
         * 替换 LoadedApk -> mApplication
         */
        Field mPackageInfoField = Utils.findField(baseContext, "mPackageInfo");
        Object mPackageInfo = mPackageInfoField.get(baseContext);
        Field mApplicationField = Utils.findField(mPackageInfo, "mApplication");
        mApplicationField.set(mPackageInfo, app);

        /**
         * 修改ApplicationInfo 的 className，LoadedApk
         *
         */
        Field mApplicationInfoField = Utils.findField(mPackageInfo, "mApplicationInfo");
        ApplicationInfo mApplicationInfo = (ApplicationInfo) mApplicationInfoField.get(mPackageInfo);
        mApplicationInfo.className = app_name;

        delegate = app;
        delegate.onCreate();
    }
}