# 火焰纹章·万缕千丝 规划（Android）

「火焰纹章·万缕千丝 (Fortunes Weave)」离线规划器的原生 Android 移植版。
Kotlin + Jetpack Compose (Material3)，深色主题，完全离线（无网络权限、无后端）。

数据在 `app/src/main/assets/`（`fe_data.json` + `chr|cls` 图片），通过 `../tools/build_android_data.py` 从网页源数据生成，随 APK 打包。
源码行为参照 `../src/app.js` 逐项移植（挖角/转职/坐骑/角色/存档五大功能）。

## 用 GitHub Actions 发布 APK（推荐部署方式）

本机无需安装 Android SDK / 下载 Gradle，仓库根目录已配置 `.github/workflows/android.yml`：
推送分支即自动构建 **正式签名** 的 release APK；推送 `v*` tag 还会自动发布到 **Releases** 页。

### 一次性配置签名密钥（Secrets）
在仓库 **Settings → Secrets and variables → Actions** 添加 4 个 Secrets：

| Secret | 值 |
|---|---|
| `KEYSTORE_BASE64` | `base64 < release.jks` 的结果（单行）|
| `KEYSTORE_PASSWORD` | keystore 口令 |
| `KEY_ALIAS` | 密钥别名（如 `fefw`）|
| `KEY_PASSWORD` | 密钥口令（PKCS12 下与 keystore 口令相同）|

> ⚠️ `release.jks` 是 App 的签名身份，**务必离线备份**：丢失后无法用同一签名覆盖更新已安装的 App。
> 该文件绝不能提交到 git；CI 构建时将其解码到临时目录，用完即弃。

### 构建与发布
1. 推送任意分支 → Actions 运行 **Android Release Build**，产出 `app-release.apk`。
2. 在运行记录底部 **Artifacts** 下载 `fefw-planner-release-apk.zip`（内含 `app-release.apk`，可直接安装）。
3. 发布正式版本：
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   自动在 **Releases** 页创建 `v1.0.0` 并附上 `app-release.apk`，可直接分享下载链接。
4. 也可在 Actions 页点 **Run workflow** 手动触发。

> 首次构建预计 3–5 分钟（需下载 Gradle 8.9 与依赖）。若构建失败，把 Actions 日志贴回即可排查。

本地可选：用 Android Studio 打开 `android/` 直接运行；或配好本地签名后
`./gradlew :app:assembleRelease`（无 `KEYSTORE_FILE` 环境变量时回退 debug 签名，仅供本地调试）。

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