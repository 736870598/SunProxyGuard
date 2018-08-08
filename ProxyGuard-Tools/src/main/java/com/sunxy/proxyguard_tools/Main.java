package com.sunxy.proxyguard_tools;

import com.sunxy.proxyguard_tools.utils.AES;
import com.sunxy.proxyguard_tools.utils.Constant;
import com.sunxy.proxyguard_tools.utils.Zip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;

/**
 * --
 * <p>
 * Created by sunxy on 2018/8/7 0007.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        /**
         * 1. 制作只包含解密代码的dex文件
         */
        File aarFile = new File(Constant.AAR_NAME);
        File aarTemp = new File("ProxyGuard-Tools/temp/aar");
        Zip.unZip(aarFile, aarTemp);
        //aar中的jar文件。
        File classesJar = new File(aarTemp, "classes.jar");
        //将jar转换成dex文件。
        File classesDex = new File(aarTemp, "classes.dex");
        //执行命令将jar转换为dex文件
        Process process = Runtime.getRuntime().exec("cmd /c dx --dex --output " +
                classesDex.getAbsolutePath() + " " + classesJar.getAbsolutePath());
        process.waitFor();
        //失败
        if (process.exitValue() != 0) {
            throw new RuntimeException("dex error");
        }

        /**
         * 2. 加密apk中所有的dex文件
         */
        File apkFile = new File(Constant.APK_NAME);
        File apkTemp = new File("ProxyGuard-Tools/temp/apk");
        Zip.unZip(apkFile, apkTemp);
        //获取原apk中的所有dex文件
        File[] dexFiles = apkTemp.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.endsWith(".dex");
            }
        });
        AES.init(AES.DEFAULT_PWD);
        for (File dex : dexFiles) {
            //读取文件
            byte[] bytes = getBytes(dex);
            //加密
            byte[] encrypt = AES.encrypt(bytes);

            FileOutputStream fos = new FileOutputStream(new File(apkTemp, "secret-" + dex.getName()));
            fos.write(encrypt);
            fos.flush();
            fos.close();
            dex.delete();
        }

        /**
         * 3. 把classes.dex 放入 apk解压目录，在压缩成apk
         */
        classesDex.renameTo(new File(apkTemp, "classes.dex"));
        File unSignedApk = new File("ProxyGuard-Tools/temp/app-unsigned.apk");
        Zip.zip(apkTemp, unSignedApk);

        /**
         * 4. 对齐与签名
         */
        //4.1 对齐
        File alignedApk = new File("ProxyGuard-Tools/temp/app-unsigned-aligned.apk");
        process = Runtime.getRuntime().exec("cmd /c zipalign -f 4 " +
                unSignedApk.getAbsolutePath() + " " + alignedApk.getAbsolutePath());
        process.waitFor();
        //失败
        if (process.exitValue() != 0) {
            throw new RuntimeException("zipalign error");
        }
        //4.2 签名
        File signedApk = new File("ProxyGuard-Tools/out/app-signed-aligned.apk");
        File jks = new File(Constant.JSK_NAME);
        process = Runtime.getRuntime().exec("cmd /c apksigner sign --ks " +
                jks.getAbsolutePath() + " --ks-key-alias " +
                Constant.KS_KEY_ALIAS +
                " --ks-pass pass:" +
                Constant.KS_PASS +
                " --key-pass pass:" +
                Constant.KEY_PASS +
                " --out " + signedApk.getAbsolutePath() + " " + alignedApk.getAbsolutePath());
        process.waitFor();
        //失败
        if (process.exitValue() != 0) {
            throw new RuntimeException("apksigner error");
        }

        System.out.println("success---");

    }

    private static byte[] getBytes(File file) throws Exception {
        RandomAccessFile r = new RandomAccessFile(file, "r");
        byte[] buffer = new byte[(int) r.length()];
        r.readFully(buffer);
        r.close();
        return buffer;
    }
}
