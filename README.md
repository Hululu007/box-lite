# 星辰盒 box-lite（星辰影音）— TVBox 多源发行版

一个以 [mlabalabala/box](https://github.com/mlabalabala/box)（Jade box）为基线、融合 [FongMi/TV](https://github.com/FongMi/TV) 多运行时能力的 AGPL-3.0 开源发行版。

目标：**多源/多仓/多线路**、**广泛兼容老设备**、**32/64 位双包同机共存**、**可审计（无后门、无上报）**。

> 本仓库为 AGPL-3.0 开源的 fork，沿用上游许可，禁止闭源再分发。

## 适用范围与最低系统

| 项 | 要求 |
|---|---|
| **最低系统** | **Android 5.0（API 21）**；targetSdk 26、compileSdk 35 |
| 32 位包（`tv.of.eu.box.daily`） | Android 5.0+，armeabi-v7a；64 位设备可 32 位兼容运行 |
| 64 位包（`tv.of.eu.box.daily.arm64`） | Android 5.0+，arm64-v8a |
| node: 源 / NodeJS 配置源 / .py 源 | **Android 7.0（API 24）+**；低版本设备在配置解析阶段自动门控剔除（不可选、不崩溃） |
| 设备形态 | TV 盒子 / 智能电视 / 手机均可（遥控器 + 触屏） |
| 双包共存 | 32/64 位包独立包名与本地服务端口（9978 / 19978），同机可同时安装，桌面标签「星辰影音32」「星辰影音64」 |

## 当前版本（v2.0.1）

| 项 | 值 |
|---|---|
| versionName | 2.0.1 |
| arm32 包 | `tv.of.eu.box.daily`（versionCode 41） |
| arm64 包 | `tv.of.eu.box.daily.arm64`（versionCode 39） |
| 运行时 | 内嵌 NodeJS（libnode）+ Python 3.10（Chaquopy）+ QuickJS |
| 包体 | arm32 ≈ 61MB / arm64 ≈ 64MB |

版本明细见 [CHANGELOG.md](CHANGELOG.md)；最新安装包见 [Releases](https://github.com/zeyark88/box-lite/releases)。

## V2 版变化（相对 v1.0.26）

| 方面 | v1.0.26 | v2.0.x |
|---|---|---|
| **工具链** | AGP 4.x · JDK 8 · Gradle 6.7.1 · compileSdk 30 · minSdk 16 | **AGP 8.5.2 · JDK 21 · Gradle 8.13 · compileSdk 35 · minSdk 21**（Android 5.0 起） |
| **爬虫运行时** | QuickJS（js 源）+ jar 源 | **新增内嵌 NodeJS（node: 源）与 Python 3.10（.py 源）**，QuickJS/js 源保留；API<24 自动门控 |
| **NodeJS 配置源** | 不支持 | 支持 `index.js.md5` 配置源（bundle 增量下载 + 本地 Node 服务，v2.0.0 接线修复后真正可用） |
| **架构** | 仅 32 位单包 | **32/64 位双包**，独立包名/端口/桌面标签，同机共存 |
| **订阅与存储** | — | 修复 Hawk 2.0.1 × Gson 2.15 不兼容（v1.1.8），多源订阅「写得进读不出」系列故障根除，旧数据自动复活 |
| **安全** | v1.0.x B-01~B-10 | 叠加 **A1/A2/A3/A4a/B1~B5**：receiver 导出收敛、本地服务令牌白名单补 `/proxy`/`/dns-query`、DoH 5s 超时回退、hosts 即时生效、订阅判重、诊断日志轮转等；**jar MD5 策略改为知情提示不阻断**（v2.0.1，兼容生态中把 `;md5;` 当缓存标记的源） |
| **稳定性** | — | TLS 套件加宽、订阅专用 client（无 DoH + 30s 超时）、主线程回调修复、订阅诊断日志 `store_debug.log` |
| **包体** | ≈ 18~20MB | ≈ 61/64MB（三个脚本运行时 + 双架构） |

## 特性

- **多源 / 多仓 / 多线路订阅**（`StoreApiConfig`）。纯净版：不内置任何默认源地址，首次启动零外联
- **三种内嵌爬虫运行时**：`node:`（NodeJS）、`.py`（Python 3.10）、`js`（QuickJS）
- 直播源 m3u8 / tvbox 格式；m3u8 原生 JNI 过滤
- **DLNA 投屏**：MulticastLock + 组播/单播/网段直扫发现引擎、投屏控制面板、直链预处理
- **WebDAV 网盘**浏览播放（坚果云 / Nextcloud / Alist / 群晖）
- 本地 HTTP 控制服务（扫码遥控、文件管理、局域网推送/投屏），令牌鉴权
- hosts 域名映射；DoH 加密解析（超时自动回退系统 DNS）；可选严格 TLS 校验
- 双架构包同机共存，桌面标签可分辨

## 仓库结构

```
.                       ← 32 位主工程（Gradle 根工程，Android Studio 打开此目录即可）
├── app/                主模块：UI、ApiConfig 配置中枢、本地服务、DLNA、WebDAV、字幕、m3u8 JNI
├── catvod/             爬虫协议与工具层（FongMi 系 SDK）
├── player/             播放器封装（ijk/exo）+ 直播/迅雷 native 库
├── quickjs/            QuickJS 引擎（js 源）
├── chaquo/             Python↔Spider 适配层
├── nodejs/             NodeJS 嵌入运行时
├── docs/               BUILD.md（构建工具链）/ ARCHITECTURE.md（目录架构）+ 1.0.x 归档文档
├── tests/              m3u8 原生过滤样例自测
└── arm64/              ← 64 位工程（独立 Gradle 工程：cd arm64 构建，或 Android Studio 单独打开）
```

模块依赖与数据流详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)；64 位工程差异见 [arm64/README.md](arm64/README.md)。

## 构建

环境要求：**JDK 21 / Gradle 8.13 / Android SDK 35 / NDK 27.0.12077973 / CMake 3.22.1 / 本机 Python 3.8+**。

- 仓库自带的 `gradlew` wrapper 仍是上游遗留 6.7.1 旧值，**请使用独立安装的 Gradle 8.13**，或先升级 wrapper
- `app/build.gradle` 中 `chaquopy.buildPython` 为维护者机器路径，**自建前请改成本机 Python 路径**
- 签名信息从 `local.properties` 读取（`box.storeFile` / `box.storePassword` / `box.keyAlias` / `box.keyPassword`），**严禁提交仓库**

```bash
# 32 位（仓库根）
gradle :app:assembleRelease
# 64 位
cd arm64 && gradle :app:assembleRelease
```

完整工具链、依赖镜像、arm64 wheel 预下载机制、双工程同步规程、常见坑见 [docs/BUILD.md](docs/BUILD.md)。

## 安全与隐私

- **无后门、无统计/广告 SDK、无强制更新**。v1.1.8 全量源码安全审计（8 类后门专项）结论 0 发现
- 已落地 v1.0.23 B-01~B-10 与 v2.0.x A/B 系列加固，详见 [SECURITY.md](SECURITY.md)
- 默认「全信任 TLS + 允许明文」以兼容第三方源自签名/老服务器生态；追求严格可在设置开启 `SSL_STRICT_VERIFY`
- 应用不内置任何默认数据源，源由用户自行配置

## 版权与协议

- 本发行版：**AGPL-3.0**（见 [LICENSE](LICENSE)）
- 上游：`mlabalabala/box`（AGPL-3.0）→ `CatVodTVOfficial/TVBoxOSC`
- 集成代码来源：[`FongMi/TV`](https://github.com/FongMi/TV)（GPL-3.0）的 catvod / chaquo / nodejs 模块；GPLv3 与 AGPLv3 兼容，统一按 AGPL-3.0 分发
- `M3u8.java` 流过滤实现参考 FongMi/TV（原作者 asdfgh、FongMi）

## 免责声明

- 本项目仅供学习与技术研究，不提供、不存储、不分发任何视听内容
- 数据源由用户自行配置，其内容与版权由源提供方负责；请遵守所在地法律法规使用
- 测试可能不够，有 bug 欢迎提 issue
