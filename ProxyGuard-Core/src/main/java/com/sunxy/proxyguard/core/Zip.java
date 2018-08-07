package com.sunxy.proxyguard.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * -- 解压压缩类
 * <p>
 * Created by sunxy on 2018/8/7 0007.
 */
public class Zip {

    public static void deleteFile(File file){
        if (file.isDirectory()){
            File[] files = file.listFiles();
            for (File file1 : files) {
                deleteFile(file1);
            }
        }else{
            file.delete();
        }
    }

    /**
     * 解压 zip 文件到 dir 目录
     */
    public static void upSip(File zip, File dir){
        try {
            deleteFile(dir);
            ZipFile zipFile = new ZipFile(zip);
            //zip文件中每一个条目
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()){
                ZipEntry zipEntry = entries.nextElement();
                //zip中 文件/目录名
                String name = zipEntry.getName();
                //原来的签名文件 不需要了
                if (name.equals("META-INF/CERT.RSA") || name.equals("META-INF/CERT.SF") || name
                        .equals("META-INF/MANIFEST.MF")) {
                    continue;
                }
                //空目录不管
                if (!zipEntry.isDirectory()){
                    //创建目录
                    File file = new File(dir, name);
                    if (!file.getParentFile().exists()){
                        file.getParentFile().mkdirs();
                    }
                    //写文件
                    FileOutputStream fos = new FileOutputStream(file);
                    InputStream is = zipFile.getInputStream(zipEntry);
                    byte[] buffer = new byte[2048];
                    int len;
                    while ((len = is.read(buffer)) != -1){
                        fos.write(buffer, 0, len);
                    }
                    is.close();
                    fos.close();
                }
            }
            zipFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将 dir 压缩到 zip 文件
     */
    public static void zip(File dir, File zip) throws IOException {
        deleteFile(zip);
        //对输出文件做CRC32检验
        CheckedOutputStream cos = new CheckedOutputStream(
                new FileOutputStream(zip), new CRC32());
        ZipOutputStream zos = new ZipOutputStream(cos);
        //压缩
        compress(dir, zos, "");
        zos.finish();
        zos.close();
    }

    /**
     * 添加目录、文件 到 zip中
     * @param srcFile   需要添加的目录/文件
     * @param zos       zip输出流
     * @param basePath  递归子目录时的完整目录
     */
    private static void compress(File srcFile, ZipOutputStream zos, String basePath) throws IOException {
        if (srcFile.isDirectory()){
            File[] files = srcFile.listFiles();
            for (File file : files) {
                compress(file, zos, basePath + srcFile.getName()+"/");
            }
        }else{
            compressFile(srcFile, zos, basePath);
        }
    }

    private static void compressFile(File file, ZipOutputStream zos, String dir) throws IOException {
        // temp/lib/x86/lib/ssl.so
        String fullName = dir + file.getName();
        // 需要去掉temp
        String[] fileNames = fullName.split("/");
        //正确的文件目录名 (去掉了temp)
        StringBuffer sb = new StringBuffer();
        if (fileNames.length > 1){
            for (int i = 1;i<fileNames.length;++i){
                sb.append("/");
                sb.append(fileNames[i]);
            }
        }else{
            sb.append("/");
        }
        //添加一个zip条目
        ZipEntry entry = new ZipEntry(sb.substring(1));
        zos.putNextEntry(entry);
        //读取条目输出到zip中
        FileInputStream fis = new FileInputStream(file);
        int len;
        byte data[] = new byte[2048];
        while ((len = fis.read(data, 0, data.length)) != -1) {
            zos.write(data, 0, len);
        }
        fis.close();
        zos.closeEntry();
    }
}
