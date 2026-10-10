# 火焰纹章·万缕千丝 规划（Android）

「火焰纹章·万缕千丝 (Fortunes Weave)」离线规划器的原生 Android 移植版。
Kotlin + Jetpack Compose (Material3)，深色主题，完全离线（无网络权限、无后端）。

数据在 `app/src/main/assets/`（`fe_data.json` + `chr|cls` 图片），通过 `../tools/build_android_data.py` 从网页源数据生成，随 APK 打包。
源码行为参照 `../src/app.js` 逐项移植（挖角/转职/坐骑/角色/存档五大功能）。

## 用 GitHub Actions 一键构建 APK（推荐部署方式）

本机无需安装 Android SDK / 下载 Gradle，仓库根目录已配置 `.github/workflows/android.yml`，
任意推送 `android/**` 相关改动到 GitHub 即自动构建：

1. 把整个 `fe-fw-planner/` 目录作为仓库根目录推送到 GitHub：
   ```bash
   git init
   git add .
   git commit -m "feat: 离线规划器（桌面版 + Android 原生版）"
   git remote add origin git@github.com:<你的用户名>/<仓库名>.git
   git push -u origin main
   ```
2. 打开仓库页 → **Actions** 标签 → 找到 **Android Debug Build** 工作流。
3. 构建成功后可进入该次运行记录，在底部 **Artifacts** 下载
   `fefw-planner-debug-apk.zip`（内含 `app-debug.apk`，可直接安装到安卓手机）。
4. 也可以点 **Run workflow** 手动触发，或改成只监听 `android/**` 的 push。

> 首次构建预计 5–10 分钟（需下载 Gradle 8.9 与依赖）。若构建失败，把 Actions 日志贴回即可排查。

本地可选：用 Android Studio 打开 `android/` 直接运行；或本机配好 JDK17+Android SDK 后
`./gradlew :app:assembleDebug`。

## 重新生成数据（可选）

`assets/fe_data.json` 与图片已随仓库提供，正常情况下**不要**重新生成。
如需调整源数据后重建，请修改上游 `../src` 相关文件后运行：

```bash
python3 ../tools/build_android_data.py
```

脚本只负责将网页端数据重新打包为 `fe_data.json`，不会改动其他文件。

## 项目结构

```
android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── app/build.gradle.kts
└── app/src/main/
    ├── AndroidManifest.xml
    ├── assets/            # fe_data.json + chr/cls 图片（勿改）
    ├── res/               # 主题、字符串、矢量图标
    └── java/com/fefw/planner/
        ├── MainActivity.kt
        ├── data/          # FeData 模型/加载、PlannerLogic 纯函数、Repository 持久化
        ├── viewmodel/     # PlannerViewModel（状态 + 落盘）
        └── ui/            # PlannerApp、theme、components、screens
```

## 存档与导入导出

- 存档保存在 App 私有目录 `files/planner.json`（卸载即清空，不联网）。
- 「存档」页支持多槽位：切换 / 重命名 / 复制 / 删除 / 新建 / 清空。
- 导出为 JSON（单槽或全部），导入时自动识别单槽/全量格式。

## 已知取舍（相对网页版）

- 无需手机端的两处交互降级：网页的「材料清单」抽屉交互改为独立弹层；「四线对比」由悬浮层改为卡片内展开。
- 除上述差异外，筛选、排序、聚合统计、路线切换、数据格式均与网页版保持一致。