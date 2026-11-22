# 星辰影音 — TVBox 多源发行版

一个以 [mlabalabala/box](https://github.com/mlabalabala/box)（Jade box）为基线的自定义发行版。
目标：**多源/多仓/多线路**、**32 位兼容老设备 + 64 位发挥新设备**、**兼容市面 TVBox 数据源**。

> 本仓库为 AGPL-3.0 开源的 fork，沿用上游许可，禁止闭源再分发。

## 下载

**最新版：[v1.0.26](https://github.com/zeyark88/box-lite/releases/tag/v1.0.26)** · [全部版本](https://github.com/zeyark88/box-lite/releases)

| 版本 | 安装包 | 包名 | versionCode | 最低系统 | ABI | 体积 |
|---|---|---|---|---|---|---|
| 32 位 | `box-v1.0.26.apk` | `tv.of.eu.box.daily` | 27 | Android 4.1+（API 16） | armeabi-v7a | 18.33 MiB |
| 64 位共存 | `box-v1.0.26-arm64.apk` | `tv.of.eu.box.daily.arm64` | 28 | Android 5.0+（API 21） | arm64-v8a | 19.29 MiB |

- 两个包使用同一签名证书（`CN=BoxLiteDist`）。32 位包可直接覆盖 v1.0.25 升级；64 位包与 32 位包可同时安装。
- 32 位包为完整重新打包，已移除历史发布包内的零填充（v1.0.25 的 24.52 MiB → 18.33 MiB）。
- 64 位包桌面名为「星辰影音64」，全部原生库为 arm64；QuickJS 自编译并支持 16 KB 页内核。
- 各资产 SHA256 见 Release 附件 `SHA256SUMS.txt`。

### 怎么选

- **32 位设备 / Android 4.1–4.4 / 老盒子** → 用 32 位包。
- **64 位设备 / Android 5.0+** → 用 64 位包；只支持 64 位的设备只能装它。
- **想两个都保留** → 直接同时安装，包名不同，数据互相独立。
- **配置迁移** → 旧版「设置 → 备份」，新版「设置 → 恢复」；两版共用 `/sdcard/tvbox_backup`。

## 特性

- 多源 / 多仓 / 多线路订阅（`StoreApiConfig`）
- 直播源 m3u8 与 tvbox 格式、点播、`quickjs` 跑爬虫脚本
- 64 位版：IJK/FFmpeg、迅雷 P2P、XL SDK、QuickJS、m3u8 过滤全部 arm64；包内 10 个 `.so` 均按 16 KB 页对齐
- DLNA 投屏（发现引擎 + 三端入口 + 控制面板）与直链探测
- 存储空间：本地存储 / WebDAV / Alist
- 崩溃自愈：闪退保存日志并自动重启回首页
- 纯净版：首次启动零内置外联，源/直播/EPG/网盘全部由用户配置
- 触屏设备快捷进设置；设置页「检测更新」小红点，**永不强制更新**
- 低版本：32 位版 `minSdkVersion 16`（Android 4.1 起，覆盖 4.4）；64 位版要求 Android 5.0+
- 安全：本地 HTTP 控制服务带令牌鉴权，仅本机与携带令牌的遥控页可访问；路径穿越、zip slip、XStream 反序列化均已加固
  - 32 位版端口：`9978–9999`
  - 64 位版端口：`19978–19999`（与 32 位版错开，避免两个 App 同时运行冲突）

## 目录

- `app/` 主模块源码
- `player/` IJK 播放器与预编译库（`armeabi-v7a`、`arm64-v8a`）
- `quickjs/` QuickJS 包装层与预编译库
- `docs/` — [`ANALYSIS.md`](docs/ANALYSIS.md)（bug 分析）、[`ROADMAP.md`](docs/ROADMAP.md)（改造路线）、[`DISTRIBUTION.md`](docs/DISTRIBUTION.md)（发行规范）
- `.github/workflows/` CI 构建

## 构建

### 32 位（armeabi-v7a）

```bash
# 需 JDK8 + Android SDK
# 签名 keystore 路径与密码通过 local.properties 配置（勿入库）
export JAVA_HOME=$PWD/dist-tools/jdk
export PATH="$JAVA_HOME/bin:$PWD/dist-tools/gradle-6.7.1/bin:$PATH"
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleRelease
# 产物：release/box-v<版本>.apk
```

### 64 位（arm64-v8a）

64 位构建需要 arm64 原生库与重编的 QuickJS。完整工程、`quickjs-native/` 源码与构建说明见 Release 附件：

- `box-lite-v1.0.26-arm64-source.tar.gz`

关键点：

- `app/build.gradle`、`player/build.gradle`、`quickjs/build.gradle` 的 `abiFilters` 改为 `arm64-v8a`
- 原生库放入 `player/src/main/jniLibs/arm64-v8a/` 与 `quickjs/src/main/jniLibs/arm64-v8a/`
- QuickJS 用 NDK r28 + `-Wl,-z,max-page-size=16384` 重编，详见 `quickjs-native/BUILD.md`

### 签名

- keystore：`~/box-keys/box-release.jks`（**务必备份，丢失无法再发版**；路径与密码勿入库）

## 版本历史

- **v1.0.26**（2026-11-22）：发布 32 位与 64 位共存版；32 位包完整重打包并去除历史零填充；64 位版全部原生库 arm64、QuickJS 16 KB 页对齐、端口与包名与 32 位版隔离
- **v1.0.25**：主页顶栏显示年月日；「存储空间」入口（本地存储 / WebDAV / Alist）；新增 Alist 客户端；本地目录浏览与播放；文件页改用 TvRecyclerView
- **v1.0.24**：DLNA `RelTime` 解析兼容大小写与命名空间前缀，修复部分投屏设备进度/时长显示
- **v1.0.23**：安全加固（B-01~B-10）：本地服务令牌鉴权、路径穿越/zip slip/文件写越界防护、`/action` 协议白名单、jar 即时 MD5 校验、XStream 白名单与 XXE 拦截、关闭 `allowBackup`、Room 破坏性迁移
- **v1.0.22**：崩溃自愈（闪退存日志 + 自动重启回首页）
- **v1.0.17–v1.0.21**：DLNA 投屏全链路；应用更名「星辰影音」
- **v1.0.16**：纯净版（移除全部内置源地址，首启零内置外联）
- **v1.0.15**：图标黑块修复 / 权限收敛（14→9）/ DLNA 投屏 / 网盘 WebDAV / 移除应用管理 / 首页精简
- ⚠️ v1.0.14 起启用新正式签名（原密钥遗失），无法覆盖升级 1.0.13 及更早版本，需卸载后全新安装（详见该版 Release 说明）
- 历史明细见 [`CHANGELOG.md`](CHANGELOG.md)

## 版权与协议

- 本发行版：AGPL-3.0
- 上游：`mlabalabala/box`（AGPL-3.0）→ `CatVodTVOfficial/TVBoxOSC`
- 参考与致谢：[`FongMi/TV`](https://github.com/FongMi/TV)（GPL-3.0）——`M3u8.java` 的流过滤实现参考其代码（原作者 asdfgh、FongMi）；GPLv3 与 AGPLv3 兼容，本发行版统一按 AGPL-3.0 分发
- 数据源仅作示例，与版权内容无关。

> 测试可能不够，有 bug 欢迎提 issue。
