package com.sunxy.sunproxyguard;


import android.app.Application;
import android.content.Context;

/**
 * --
 * <p>
 * Created by sunxy on 2018/8/7 0007.
 */
public class MyApplication extends Application {

    private static MyApplication app;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        app = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static MyApplication getApp(){
        return app;
    }

    public String getTvString(){
        return "------MyApplication------";
    }
}
