package com.sunxy.proxyguard.core;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * -- 工具类
 * <p>
 * Created by sunxy on 2018/8/7 0007.
 */
public class Utils {

    static {
        System.loadLibrary("sunxy_ssl");
    }

    public static native void decrypt(byte[] data,String path);

    /**
     * 读取文件
     */
    public static byte[] getBytes(File file) throws Exception {
        RandomAccessFile r = new RandomAccessFile(file, "r");
        byte[] buffer = new byte[(int) r.length()];
        r.readFully(buffer);
        r.close();
        return buffer;
    }


    /**
     * 反射获得 指定对像中的成员
     * 找不到的话就去他的 父类 中找
     */
    public static Field findField(Object instance, String name) throws NoSuchFieldException{
        Class<?> clazz = instance.getClass();
        while (clazz != null){
            try {
                Field field = clazz.getDeclaredField(name);
                if (!field.isAccessible()){
                    field.setAccessible(true);
                }
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    /**
     * 反射获取对象中的指定函数
     *
     */
    public static Method findMethod(Object instance, String name, Class... parameterTypes)
    throws NoSuchMethodException{
        Class<?> clazz = instance.getClass();
        while (clazz != null){
            try {
                Method method = clazz.getDeclaredMethod(name, parameterTypes);
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }
                return method;
            } catch (NoSuchMethodException e) {
                //如果找不到往父类找
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchMethodException("Method " + name + " with parameters " + Arrays.asList
                (parameterTypes) + " not found in " + instance.getClass());
    }


    public static void copy(List<File> dexFiles, File dexDir){
        if (dexFiles != null && !dexFiles.isEmpty()){
            for (File dexFile : dexFiles) {
                try {
                    copy(dexFile, dexDir.getAbsolutePath() + "/" + dexFile.getName());
                    Log.v("sunxy", "copy success " + dexDir.getAbsolutePath());
                }catch (Exception e){
                    Zip.deleteFile(dexDir);
                }
            }
        }
    }

    private static void copy(File dexFile, String filePath) throws IOException {
        //创建目录
        File file = new File(filePath);
        if (!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        //写文件
        FileOutputStream fos = new FileOutputStream(file);
        InputStream is = new FileInputStream(dexFile);
        byte[] buffer = new byte[2048];
        int len;
        while ((len = is.read(buffer)) != -1){
            fos.write(buffer, 0, len);
        }
        is.close();
        fos.close();
    }
}
