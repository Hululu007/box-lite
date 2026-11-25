# 工程目录架构说明（星辰盒 box-lite）

> 配套阅读：[BUILD.md](BUILD.md)（工具链与构建流程）、根 [README.md](../README.md)（项目定位）。
> 双工程说明：根工程（32 位）与 `arm64/`（64 位）目录结构完全同构，差异仅 5 处构建参数（见 BUILD.md 第 8 节）。

## 1. 顶层布局与模块依赖

```
仓库根/
├── app/        主模块（壳：UI/配置/播放调度/本地服务）
├── catvod/     爬虫协议与工具层（FongMi 系 SDK，53 类）
├── player/     播放器封装（ijk/exo）+ 直播/迅雷 native 库
├── quickjs/    QuickJS JS 引擎（whl 系 JNI + so）
├── chaquo/     Python↔Spider 适配层（FongMi 系，7 类）+ Python 脚本目录
├── nodejs/     NodeJS 嵌入运行时（FongMi 系，9 类 + bootstrap + libnode.so）
├── docs/       文档（BUILD/ARCHITECTURE 现行；ANALYSIS/ROADMAP/DISTRIBUTION 为 1.0.x 归档）
├── gradle/     wrapper（6.7.1 旧值，勿用，见 BUILD.md 坑 1）
├── dist-tools/ 1.0.x 时代构建脚本遗留（setup_and_build.sh，已过时）
├── release/    本地产物归档（gitignore 已排除，不入库）
├── tests/      m3u8_core_test.sh（native 过滤核心的样例自测）
├── settings.gradle / build.gradle / gradle.properties
├── local.properties   SDK 与签名配置（**不入库**）
└── .gitignore        已排除：签名文件(*.jks)、local.properties、release/、dist-tools 工具链
```

**模块依赖方向**（箭头 = depends on）：

```
        ┌──→ :player ──→ exoplayer 系
        ├──→ :quickjs（js 源引擎）
  :app ─┼──→ :chaquo ──→ :catvod
        ├──→ :nodejs ─ compileOnly→ :catvod（运行期由 app 供类）
        └──→ :catvod（协议+工具+网络）
```

## 2. 各模块职责与体积

| 模块 | 内容 | native 库 | 对 APK 体积贡献 |
|---|---|---|---|
| `:app` | 壳：UI、配置中枢 ApiConfig、源分发、播放调度、本地控制服务、DLNA、WebDAV、字幕、m3u8 原生过滤 | libm3u8native.so（现场 CMake 编译，7KB） | dex 大头 |
| `:catvod` | Spider 协议（crawling jar 的 SDK）、Task 线程池、okhttp4 Net 封装、NodeRuntimeApi 接口、工具类 | 无 | 小 |
| `:player` | ijk/exo 播放封装、迅雷拉流 | libijkffmpeg(10.3MB)/libijksdl/libp2p(3.6MB)/libxl_thunder_sdk(3.5MB)/libplayer | ~18MB |
| `:quickjs` | `com.whl.quickjs` JNI 封装 | libquickjs-android-wrapper.so(0.8MB) | ~1MB |
| `:chaquo` | `com.fongmi.chaquo` 7 类（Python↔Spider 适配）+ `src/main/python` | —（libpython 由 Chaquopy 插件注入 :app） | 小 |
| `:nodejs` | `com.fongmi.nodejs` 9 类（NodeClient/NodeSpider/NodeService/RuntimeEntry…）、assets bootstrap.js、cpp | libnode.so(40.7MB)/libnodebridge/libc++_shared | ~42MB |
| Chaquopy 插件 | Python 3.10 stdlib + pip 包（打为 assets + lib） | libpython3.10/libcrypto/libssl/libsqlite3_chaquopy | ~21MB |

## 3. `:app` 包结构（`com.github.tvbox.osc.bbox.*`）

| 包 | 职责 |
|---|---|
| `api/` | **配置中枢**：ApiConfig（loadConfig JSON/Node 双分支 + getCSP 源分发 + hosts/代理）、StoreApiConfig（多源订阅）、NodeRuntime（Node 宿主桥） |
| `ui/activity` `ui/fragment` `ui/adapter` `ui/dialog` `ui/tv.widget` | UI 层：首页/详情/搜索/播放/设置等 + TV 焦点控件 |
| `bean/` | 数据模型（SourceBean/AbsSortXml/VodInfo 等 22 类） |
| `server/` | **本地控制服务**：RemoteServer（9978 端口，令牌鉴权，扫码遥控/文件管理/推流）、ControlManager |
| `player/` `player.controller` `player.render` `player.thirdparty` | 播放器封装（IPlayer 抽象、ijk/exo 实现、渲染与手势） |
| `subtitle/`（model/format/runtime/widget/exception） | 字幕引擎：解析/时间轴/渲染 |
| `util/` | 35+ 工具类，其中子包：`dlna/`（DLNA 投屏发现引擎+控制面板）、`webdav/`（网盘）、`alist/`、`js/`（**QuickJS↔Spider 适配器**，JsSpider 等 8 类 + rsa）、`live/`（直播订阅）、`thunder/`（迅雷）、`parser/`、`urlhttp/`（1.0.x 老 HTTP 栈，仍在用）、`SSL/`（宽 TLS） |
| `cache/` | CacheManager（播放进度/搜索结果缓存） |
| `update/` | 更新检查组件（**已禁用**：更新地址空串，仅保留代码） |
| `base/` `data/` `viewmodel/` `model/` `callback/` `event/` `receiver/` `service/` `component/` `constant/` | App 入口、Room 数据层、ViewModel、EventBus 事件、广播接收器（v2.0.0 收敛导出）、组件与常量 |
| `jni/` | m3u8 原生过滤：`m3u8_core.c` / `jni_m3u8.c` / `CMakeLists.txt`（改动须过 `tests/m3u8_core_test.sh` 且零告警） |

### app 内的“同包异地”目录（重要，升级需警惕）

| 路径 | 说明 |
|---|---|
| `app/.../com/github/catvod/crawler/` | **JarLoader / JsLoader / PyLoader** —— 与 :catvod 模块同包（`com.github.catvod.crawler`）不同类的自维护装载器。升级 catvod 若带入官方同名类会 dex 冲突 |
| `app/.../com/orhanobut/hawk/` | **FixedHawkConverter** —— v1.1.8 修复 Hawk×Gson 不兼容（TypeToken.getParameterized 替换匿名 TypeToken），同包访问 Hawk 内部结构 |
| `app/.../okhttp3/dnsoverhttps/` | okhttp3 官方 artifact 的**本地定制版**（mutable、支持运行期 setUrl 切换 DoH、空 URL 回退系统 DNS）；:catvod 的同名 artifact 因此为 compileOnly |
| `app/.../com/p2p/` | P2PClass（p2p 加速的 JNI 入口） |

## 4. 核心数据流（实证自 ApiConfig.getCSP）

```
多源订阅（StoreApiConfig.doGet，okhttp4 + 主线程回调）
   → ApiConfig.loadConfig（JSON 缓存捷径 / Node 分支 index.js.md5 → :nodejs 后台起服务）
   → 源列表 SourceBean → 用户选源 → getCSP 分发：
        csp_  → JarLoader  → DexClassLoader（宿主 JVM 直跑，jar 借宿主 catvod 类）
        .js   → JsLoader   → util/js JsSpider → :quickjs 引擎
        node: → NodeSpider → :nodejs（:nodejs 进程，本地 HTTP 互通）
        .py   → PyLoader   → :chaquo com.fongmi.chaquo.Spider → Chaquopy Python
   → Spider 统一协议（:catvod）→ 首页/搜索/详情/播放数据
   → PlayerHelper 选内核（ijk / exo / 系统）播放
        直播特殊链路：clan://localhost → RemoteServer(9978) 本机代理
        rtmp:// → exoplayer extension-rtmp（librtmp-jni）
```

API<24 老设备：node/.py 源在配置解析阶段直接剔除（门控），不可选不崩溃。

## 5. 安全相关位置速查

- 本地服务鉴权：`server/RemoteServer.needsToken()`（v2.0.0 补齐 /proxy、/dns-query；64 位端口 19978）
- TLS 开关：`util/SSL/` + `HawkConfig.SSL_STRICT_VERIFY`（默认宽 TLS 保兼容）
- hosts：`ApiConfig.getMyHost()` ← 设置页，CustomDns 每次活读（v2.0.0 B1）
- Hawk 加密：Facebook Conceal（Hawk 默认 ConcealEncryption，libconceal.so **不可删**）
- jar 下载校验：`ApiConfig.loadJar` / `JarLoader.loadJarInternal`（v2.0.1 起 md5 不匹配知情提示不阻断）
