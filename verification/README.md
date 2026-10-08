# FuckClip JVM 回归验证

在项目根目录执行：

```bash
python3 verification/run_regressions.py
```

验证范围：远程 Preferences 替换后的配置读取。

脚本使用 GRADLE_USER_HOME（未设置时为 ~/.gradle）中缓存的 Kotlin 2.2.20 编译器及其依赖，要求 JDK 21，不下载软件。测试直接读取当前项目源码；需要 Android 环境的局部方法使用类型替身。临时编译产物自动删除。

测试验证局部控制流，不能替代完整 Gradle/APK/R8 构建、设备运行、Binder 或 ContentProvider 集成、真实 GIF 编码和性能测量。

新增重连测试使用 Compose runtime 1.9.2、Kotlin Compose 编译插件 2.2.20、collection-jvm 1.5.0 和 coroutines 1.8.0 的缓存依赖。需要 JDK 的 javac；Android 的 Trace/Looper/Parcelable 使用最小类型替身，不依赖 Android SDK。

测试读取生产 SettingsScreen 的 key 范围和列表加载代码，在实际 Compose 重组与协程中验证：换配置后旧列表立即移除；快速二次重连取消旧结果发布；用户编辑不被取消中的旧任务覆盖；相同 Preferences 重新建立 Settings 时列表与开关一起重建。PackageManager 为可阻塞替身，测试不替代实际服务 Binder 和 Android 页面交互验证。
