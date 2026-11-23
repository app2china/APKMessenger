# APKMessenger
## 软件介绍
APK Messenger 是一款电脑端APK信息文件提取工具，由果核剥壳网站开发，本软件为基于JAVA GUI开发的桌面程序  
目前JAVA版本实现了功能  
获取应用名，支持提取多语言名称  
获取包名，版本名，版本号，文件MD5  
查壳功能，支持获取爱加密，360加固，梆梆加固，阿里云加固，腾讯乐固，百度加固，娜迦加固，顶象加固，通付盾，网秦，几维加固等壳信息  
读取APK图标
支持提取APK对应的权限，并且给出权限注释  

## 相关说明  
本软件为APKMessenger的复刻版本，原软件为易语言开发迭代到了4.2版本，下载地址：https://www.ghpym.com/apkinfo.html
本JAVA源代码实现了发行版本的大部分功能，如果要用于生产环境，建议您下载上面地址上面的版本。  
开发工具：IDEA  
## Windows 运行

用 IntelliJ IDEA 打开项目，用 JDK 8 或更新版本编译并运行 `Main`。项目附带 Windows x64 版 AAPT2，运行时只需要 Java，无需安装 Android SDK 或配置 SDK 环境变量。

点击「打开 APK」或按 Ctrl+O 选择 APK，也可以将文件拖入窗口。「签名信息」按 V1、V2、V3 分页显示证书信息、公钥参数、签名算法，以及 SHA-256、SHA-1 和 MD5 指纹。每张证书都可复制详情、PEM 或 DER 十六进制。

主窗口跟随 Windows 的应用颜色模式。文件选择使用 Windows 原生「打开」对话框，可访问资源管理器的常用位置。APK 可拖到主窗口的任意区域，也可在资源管理器中复制文件后按 Ctrl+V。一次提供多个文件时，打开其中第一个可识别的 APK。

「多语言文件名」列出 APK 自带的各语言应用名和建议的 Windows 文件名。中文条目置顶，其他条目保留原有顺序。选择一行后即可复制建议名称。应用名语言会优先选用 APK 提供的简体中文，其次为其他中文。

权限页从 `AndroidManifest.xml` 读取权限请求和自定义权限，显示保护级别、适用 API、请求标记和离线说明。权限目录来自 Android SDK Platform 37.2 的 API 数据、平台清单和框架字符串。框架字符串版权归 Android Open Source Project 所有，依据 Apache 2.0 许可分发，许可证文本见 [AAPT2 随附许可](src/aapt/NOTICE)。

内置 AAPT2 来自 Google Maven 的 Windows 构件 `com.android.tools.build:aapt2:9.1.0-14792394`。随附[许可证及归属说明](src/aapt/NOTICE)。

Windows 接口通过 `lib/` 中随附的 JNA 和 JNA Platform 5.6.0 调用。上游许可证收录在两个 JAR 的 `META-INF/LICENSE` 中。
