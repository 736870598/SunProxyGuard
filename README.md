# SunProxyGuard
 apk加固。

#### 先说缺点：

   1. 必须要引入core,而且主工程的AndroidManifest.xml文件必须要：

            <application
                ....
                android:name="com.sunxy.proxyguard.core.ProxyApplication">
                ....
                <!-- 为了我们之后的替换 (真实的Application全类名) -->
                <meta-data android:name="app_name" android:value="你真实的application"/>
                <!-- 一个版本标识 用于dex解密后的目录名 -->
                <meta-data android:name="app_version" android:value="你的版本号"/>
                ....
            </application>

   2. 主工程的apk必须要放在Tool工程中进行加固。
   3. 在首次安装apk运行或者升级后运行的时候要进行解压、复制、替换等工作，可能有点点耗时。
   4. 在每次运行的时候都要进行动态的application的替换，而且用到了反射~~~，


#### 再说优点：

   1. 主工程中的dex文件全部加密了，也就是拿到apk文件根本看不到源码，而且连装源码的dex文件也都打不开。
   2. 没了....


#### 核心原理：

###### 加密：
   1. 在Tool工程中进行，首先将需要加密的apk文件进行解压，遍历里面的dex文件进行加密，
   2. 将core的aar中的jar文件打包成classes.dex放入apk中作为主dex使用，因为在清单文件中
   配置了application为aar中的ProxyApplication，所以在开始加载是是能正常加载出的，
   解压和替换工作就是在ProxyApplication的attachBaseContext和onCreate中进行的
   3. 压缩、对齐、签名

###### 解密：
   1. 在ProxyApplication中进行解密和替换application的工作。
   在attachBaseContext中进行dex的解密工作。并且将dex放入到软件的elements中。
   2. 在onCreate中进行application的替换工作。


###### tools工程中文件夹的说明：
   1. aar文件夹，存放core的aar文件（可没有）
   2. jks文件夹，存放jks文件（可没有）
   3. out文件夹，存放加固后apk文件的（必须有）
   4. temp文件夹，存放在加固过程中产生的文件。（可没有）