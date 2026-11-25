# v2.0.1 (2026-11-25)

**兼容性修复：jar MD5 强校验改为知情提示**（真机反馈「MD5 校验失败，不加载 jar」）。产物：box-v2.0.1-arm32.apk（versionCode 41）/ box-v2.0.1-arm64.apk（versionCode 39）。

- **根因**：v1.0.23 B-07 的「下载完成 MD5 不匹配即删除拒载」与 TVBox 生态语义冲突——大量配置把 `;md5;` 当**缓存刷新标记**而非真实 MD5（源方更新 jar 不改 md5，或干脆填随机串），这类源被强校验直接打死。且 md5 与 jar 同通道传输，真实 MITM 可同时篡改两者，强校验对防投毒无实质增益
- **修复**：配置阶段（`ApiConfig.loadJar`）与运行期（catvod `JarLoader`）两处下载后校验均改为「知情提示后照常加载」——不匹配记日志（含 expected/actual）+ Toast 提示，不再删除文件、不再拒绝加载；**缓存命中判断仍用 md5**（源方更新 jar 后仍触发重新下载，缓存新鲜度语义不变）；下载内容真损坏时 DexClassLoader 自然失败，下次因 md5 不匹配自动重下，自愈
- 顺手修复 `img+` 形式 jar 提取空数据时缺 `return` 的 NPE（convertResponse 内对 null 执行 write）
- 安全取舍说明：放宽后 MITM 理论上可投毒 jar，但同通道下其本可连配置 md5 一起改——原强校验并未提供可利用的防护；防投毒的实质手段是开启 `SSL_STRICT_VERIFY`

# v2.0.0 (2026-11-25)

**大版本：v1.1.8 全量代码安全审计修复落地 + NodeJS 配置源接线完成 + 双包可分辨**。
产物：box-v2.0.0-arm32.apk（versionCode 40）/ box-v2.0.0-arm64.apk（versionCode 38），双包验签通过。

- **A1 · NodeJS 配置源接线（重点改造）**：深挖发现 v1.1.x 的 `node:` 源从未真正工作——`NodeClient.recover()` 在无 bundle 时直接抛错，`getNodeCSP` 静默返回 SpiderNull（45s ANR 为静态分析误判）。v2.0.0 四层修复：
  - **接线**：`ApiConfig.loadConfig` 新增 Node 分支（`index.js.md5` 结尾地址识别），bundle 下载与 Node 启动（最长 45s）全部在后台线程完成，成功后回主线程走原解析链；不走 JSON 缓存捷径（缓存引用 `node:` 路由，必须先有运行中的服务）
  - **主线程防护**：`getNodeCSP` 在主线程调用时直接返回 SpiderNull（`spider.init` 是同步 HTTP，主线程调用会 ANR）；业务调用点已核实全在后台线程，主线程仅 WebView 嗅探兜底路径，播放不受影响
  - **同源复用**：同一 node 源复用已初始化的 Spider，不再每次 POST `/init`；服务意外死亡由 `NodeClient.recover` 自动重启并重放
  - **session 失效复位**：新增 `resetNodeSpiderCache()`，Node bundle 重载（session 递增）后清空旧 Spider 缓存，避免旧 Spider 持续 `checkSession` 异常导致 node 源全部失效
- **A2 · receiver 收敛**：`SearchReceiver` / `CustomWebReceiver` 显式 `exported=false`（本机遥控走同包显式广播不受影响），`onReceive` 参数判空消除 NPE 广播 DoS
- **A3 · 本地服务令牌白名单补漏**：`needsToken()` 增补 `/proxy` 与 `/dns-query`，封死无令牌局域网代理滥用；本机（127.0.0.1）放行、遥控页 XHR 自带 `X-TV-Token`，播放/投屏无感
- **A4a · 订阅客户端尊重 TLS 开关**：`StoreApiConfig.storeClient` 读取 `SSL_STRICT_VERIFY`（开启则系统默认校验；默认宽 TLS 不变以保广泛兼容）
- **B1 · hosts 即时生效**：`CustomDns.lookup` 每次读 `ApiConfig.getMyHost()` 活引用（原静态快照永不刷新，hosts 功能形同虚设）
- **B2 · DoH 超时兜底**：`dohClient` 加 `callTimeout(5s)`；本地定制 `DnsOverHttps` 的 `latch.await` 带 5s 超时，超时/失败自动回退系统 DNS（v1.1.6 只修了订阅侧，此轮补齐主链路）
- **B3 · 线路判重**：订阅线路改 name+url 双条件判重（同名线路不再覆盖/重复）
- **B4 · 更新检查**：纯净版空更新地址直接 return，不再弹订阅语境误导提示
- **B5 · 诊断日志轮转**：`store_debug.log` 超 256KB 截断重写
- **双包可分辨**：桌面标签改「星辰影音32」「星辰影音64」，关于页显示版本号 + 32/64 位标识（按 `nativeLibraryDir` 判架构），并新增 [FongMi/TV](https://github.com/FongMi) 打包分发出处链接（弹窗顶部固定显示版本号，正文精简避免 TV 弹窗截断）
- 本版基于 v1.1.8 全量代码安全审计（审计结论：后门 0 发现）执行修复；搜索联想上送（atianqi）与默认加速代理域两项生态惯例问题保留未动，待维护者决策

# v1.1.8 (2026-11-25)

**根因终极修复：Hawk 2.0.1 × Gson 2.15.0 不兼容**——v1.1.0 起多源订阅系列故障的真正源头。

- **机理**：Hawk 2.0.1 的 `HawkConverter` 用匿名 `TypeToken<List<T>>{}` 携带类型变量，Gson 2.10.1 起禁止此类 TypeToken → `Hawk.get` 反序列化抛异常被静默吞掉返回空值。表现：所有 Map/List 类 Hawk 键「写得进、读出永远空」（STORE_API_MAP / API_MAP / 各类历史记录全部中招），String/Integer 键不受影响。v1.1.0 引入 FongMi catvod（gson 2.8.7→2.15.0）即触发，1.0.26 因此正常
- **修复**：新增 `FixedHawkConverter`（`TypeToken.getParameterized(HashMap.class, keyClazz, valueClazz)`，两版 gson 均合法），`App.initParams` 接入。序列化格式不变，**与旧数据互读**——v1.1.x 期间「写进读不出」的订阅地址、线路历史修复后直接复活，无需重新输入
- 定位方法：解包 aar 在 JVM 复现 Hawk put/get 链路、双 gson 版本对拍，实锤抛 `IllegalArgumentException: TypeToken type argument must not contain a type variable`

# v1.1.7 (2026-11-25)

- 日志实锤订阅拿到的是**空 URL**（三次 GET 后无地址）：纯净版 `DEFAULT_STORE_API_URL=""`，`STORE_API_MAP` 取不到地址时回退空串，okhttp4 对 `url("")` 同步抛异常 → 请求未发起、回调未执行、无任何 Toast，完美解释 v1.1.4~1.1.6「完全无反应」
- `doGet` 增加空 URL 保护（直接回主线程弹明确提示，不发起请求）；订阅前把 name / map 键集 / storeUrl 全量写入诊断日志

# v1.1.6 (2026-11-25)

- 订阅改用专用干净 client：不走 DoH（系统 DNS）+ `callTimeout(30s)` 强制回调（排查 DoH 无超时挂死）
- **全链路诊断持久化**：订阅发起/HTTP code/body 长度/异常链+栈 append 写入 `Android/data/<包名>/files/store_debug.log`（TV 文件管理器可直读）；失败 Toast 提示查看该文件

# v1.1.5 (2026-11-25)

- 修复 v1.1.4 引入的回调线程 bug：okhttp4 原生 `enqueue` 回调落在无 Looper 线程，`Toast.makeText` 抛 `Can't toast on a thread...` → 回调链静默中断（无 Toast、Hawk 不入库）。body 在 okhttp 线程读完，结果 `MAIN.post` 切主线程再回调，完全复刻 OkGo 主线程回调语义

# v1.1.4 (2026-11-25)

- 多源订阅请求层从 OkGo 3.0.4 迁移到 okhttp4 原生异步（复用 v1.1.3 已配 COMPATIBLE_TLS 的 client），彻底绕开 OkGo 兼容层；失败 Toast 附真实异常名便于分流。注：全局回退 okhttp3 已评估否决（catvod 深度依赖 okhttp4 专属 API）

# v1.1.3 (2026-11-25)

- 主因排查为真机 TLS 握手：三个共享 client 加 `connectionSpecs(COMPATIBLE_TLS, CLEARTEXT)` 宽套件（老盒子/老 TLS 服务器兼容）；订阅失败 Toast 附真实异常 + logcat 标签

# v1.1.2 (2026-11-25)

- 修复 node 源真机全部加载失败：`libnode.so` 依赖 libc++_shared，chaquopy 内置那份在链接器搜索路径外 → jniLibs 补 NDK 27 的 `libc++_shared.so`（v7a/arm64）；符号级验证 NDK27 全覆盖（NDK21 缺 48 个 C++ 符号不可用）

# v1.1.1 (2026-11-24)

- **NodeJS 模块全链路完成**：`:nodejs` 模块（AGP 8.5.2 / CMake 3.22.1 + NDK 27 / 16KB 对齐）；RuntimeModule 增加 `EMBEDDED_NODEJS` 嵌入分支（require/isRestartRequired/load/recordLoaded 绕过外部运行时 APK 机制）；宿主 `NodeService` 直读 assets bootstrap；`ApiConfig` 接线（`node:` 源 CSP 分支 / 本地代理 / 清理）
- **API<24 门控**：源列表解析处剔除 `.py` / `node:` 源（低版本设备不可用），getCSP 级兜底 SpiderNull
- **64 位工程 bl27-64 建成**：独立包名 `tv.of.eu.box.daily.arm64`（同机可共存）、arm64-v8a 全套 abiFilters、本地服务端口 9978→19978、chaquopy arm64 轮子预下载机制
- 产物：box-v1.1.1-arm32.apk（versionCode 31）/ box-v1.1.1-arm64.apk（versionCode 29）

# v1.1.0 (2026-11-24)

**大升级：现代化工具链 + FongMi 三运行时引入**。产物 box-v1.1.0-arm32.apk（versionCode 30，46.27MB）。

- **工具链升级**：compileSdk 30→35、minSdk 16→21（Android 5.0 起）、AGP 4.x→8.5.2、JDK 8→21（源级 1.8 + core library desugaring）、okhttp3→okhttp 4.12 + okio 3.6、gson→2.15.0
- **三大运行时模块引入**（源自 FongMi/TV catvod 系列）：
  - `:chaquo` Python 3.10 嵌入运行时（chaquopy 15.0.1，pip 含 lxml/ujson/pycryptodome/cryptography/pywasm 等），`.py` 源接入源加载与直播
  - `:nodejs` NodeJS 运行时模块（本轮起步）
  - `:catvod` okhttp4 系支撑库
- 冲突处置：app 本地定制版 `okhttp3.dnsoverhttps`（支持运行期切换 DoH）与 catvod 同名 artifact 冲突，后者改 `compileOnly`
- 30 条源数量限制移除；ijkplayer / 迅雷 SDK / QuickJS 均保留在包内

# v1.0.23 (2026-11-06)

**安全加固版**：完整修复静态审计中 B-01 ~ B-10 共 10 项风险，功能与交互尽量保持不变。

- **B-01 本地服务鉴权**：`9978-9999` 端口不再对局域网裸奔。电视端二维码/提示地址自动携带 `?tk=<令牌>`，页面 ajax 通过 `X-TV-Token` 请求头回传；本机（`127.0.0.1` 与本机网卡地址）请求一律放行，播放器、`clan://localhost` 链路不受影响。无令牌直接访问 IP 只返回提示页。
- **B-02 `/file/` 路径穿越**：改为 `getCanonicalPath()` 前缀比对，越界返回 403（兼容 minSdk 16，未使用 `java.nio.file`）。
- **B-03 解压 zip slip**：`unzip()` 逐条校验解压目标必须落在目标目录内，并新增 512MB 累计体积上限防 zip bomb。
- **B-04 文件写操作越界**：`/upload`、`/newFolder`、`/delFolder`、`/delFile` 统一走路径校验；文件名只取 basename，禁止删除外部存储根目录。
- **B-05 `/action` 配置注入**：配置 URL 增加协议白名单（http/https/clan/file）与 2048 字符上限，搜索关键字限长 128，非法请求返回 400。
- **B-06 全信任证书可配置**：新增 `HawkConfig.SSL_STRICT_VERIFY`（默认关闭以兼容自签名源），开启后走系统默认 TrustManager。
- **B-07 jar 下载即时校验**：下载完成后立即校验 MD5，不匹配直接删除且不加载；单包体积上限 32MB。
- **B-08 XStream 反序列化**：新增类型白名单（仅本项目 bean 包与 `java.util`），并拒绝含 `<!DOCTYPE>` / `<!ENTITY>` 的 XML 防 XXE。
- **B-09 Manifest**：`allowBackup` 关闭（`tools:replace` 覆盖三方库声明）；明文流量保持开启以兼容第三方源，严格模式见 B-06。
- **B-10 Room 迁移**：显式声明 `fallbackToDestructiveMigration()`，避免未来升版缺少迁移导致启动崩溃。
- 版本号：versionCode 24 / versionName 1.0.23；产物 `release/box-v1.0.23.apk`。

# v1.0.22 (2026-11-05)

- **新增崩溃自愈**：任何闪退自动保存日志到 `files/crash/crash_*.txt`，**1 秒后自动重启回首页**（TV 无人值守不再死机）
- 其余与 v1.0.21 完全一致（投屏发现引擎/三端入口/控制面板/直链探测/纯净版/星辰影音），未引入任何其他改动

# v1.0.21 (2026-11-05)

「星辰影音」投屏全链路升级（涵盖 v1.0.17 ~ v1.0.21 演进，自 v1.0.16 纯净版基线）：

- **品牌更名**：应用更名「星辰盒」→「**星辰影音**」；关于页显示「星辰影音 v<版本号>」
- **投屏发现引擎（根治扫不到设备）**：
  - MulticastLock（Android 组播锁，多数设备收不到 SSDP 的根因）
  - 4 种 M-SEARCH 并发组播 + 每 1.5s 重发（兼容各厂商应答习惯）
  - **本机单播探测**（支持同机装投屏接收 App 自扫）
  - **/24 网段逐 IP 单播直扫**（彻底绕开组播/IGMP/隔离不可靠）
- **投屏入口三端接入**：详情页「投 屏」、全屏播放控制栏「投屏」、预览小窗控制栏「投屏」（EventBus，预览时也可直接投）
- **投屏控制面板**（投屏中再点投屏呼出）：暂停/继续、快进/快退 30 秒、断开投屏、切换设备、自动回填当前进度（标准 UPnP Pause/Play/Seek/GetPositionInfo/Stop，零第三方库）
- **投屏直链预处理**：投屏前替电视探测直链——展平 302 跳转再投（提高可播率）；403/401 防盗链、404 失效时给出明确提示（不再只看到电视端 -1011）
- 说明：投屏倍速受 DLNA 协议限制（DMR 普遍仅支持 Speed=1）；带防盗链的源电视无法裸拉，属协议限制，可换线路或本机播放

# v1.0.16 (2026-11-05)

**纯净版**：移除全部内置隐藏外联源地址

- 清空 `DEFAULT_STORE_API_URL`（原内置 `gitea.com/404ynnub` 源仓库，启动自动拉源列表的根源）
- 清空 `DEFAULT_API_URL`（原内置 `www.饭太硬.net` 默认线路）
- 不再预置默认 EPG 节目单地址（`epg.51zmt.top` / `epg.112114.xyz`）
- 源/直播/EPG/网盘全部由用户自行配置，**首次启动为全空白，零内置外联**
- 保留（功能性、非隐藏源）：GitHub 下载代理选项历史（ghproxy 等，仅下拉选项）、源 jar 下载代理 `DOMAIN_NAME_PROXY`、DoH 域名解析选项列表（需手动开启才连接）、CrossWalk 运行时按需下载

# v1.0.15 (2026-11-05)

- **图标修复**：修复部分 TV launcher 应用图标显示黑块 —— icon 改为白底位图（drawable/app_icon），TV banner 改为位图 app_banner_b（原 banner 为透明 shape XML）
- **权限收敛**：14 → 9 项（移除定位/电话状态/安装包/GET_TASKS/挂载文件系统，tools:node remove）
- **移除 AppsActivity**（应用管理）；首页应用名点击改为「切换站点」
- **首页精简**：隐藏日期；顶部新增「网盘」入口（云图标）
- **新增 DLNA 投屏**：详情页「投 屏」按钮，标准 UPnP 控制点（SSDP M-SEARCH 发现 + SOAP SetAVTransportURI/Play），零第三方库
- **新增网盘**：WebDAV 浏览/播放（坚果云/Nextcloud/Alist/群晖），TV 大字焦点列表，账号配置持久化
- **详情页**：下载/投屏等按钮间距统一

# Changelog

本项目为 `mlabalabala/box` 的自定义发行版。遵循语义化版本：`主.次.修订`。

## [1.0.14] — 2026-11-03 P1 原生化全链路打通 + 解析热路径重构

**打通 m3u8 原生过滤全链路**（深挖发现三处断点，导致原生路径此前从未真正生效）：
- 修复 `M3u8.purify()` 调用 `M3u8Native.removeMinority` **参数顺序颠倒**（content/tsUrlPre 对调），此前即使放开开关也永远返回 null 回退 Java。
- `app/build.gradle` 接入 `externalNativeBuild`（CMake 指向 `src/main/jni/CMakeLists.txt`）——此前 `libm3u8native.so` 根本没有编译进 APK，`System.loadLibrary` 恒失败。
  **新增构建前置**：需 NDK + CMake（如 `sdkmanager "ndk;21.4.7075529" "cmake;3.10.2"`）。
- `app/build.gradle` 显式 `buildToolsVersion "30.0.1"`（AGP 4.2.2 默认 30.0.3，但国内镜像普遍缺 30.0.3 包；30.0.1 与 compileSdk 30 完全兼容，保证镜像受限环境可复现构建）。
- `quickjs/build.gradle` compileSdk 由 33 统一为 30（与 app/player 一致；该模块只用预编译 jniLibs，不引用 API 31+ 接口，免去 SDK 33 依赖）。
- `CMakeLists.txt` 移除无效的 `set(ANDROID_ABI ...)` 死代码（ABI 实际由 Gradle 侧控制）。
- `jni_m3u8.c` 输出缓冲由 64KB 固定栈改为按需 `malloc`（上界 = 原文长 + 行数×(前缀+2) + 4KB），大清单不再直接回退；分配失败安全回退 Java。
- `M3u8Native.useNative()` 放开为 `true`：C 实现忠实复刻 + 样例单测通过 + 全失败路径自动回退 Java，最坏情况=回到纯 Java 行为。真机回归方法见类注释与 `docs/ROADMAP.md`。

**`JsonParallel` 重构（解析热路径性能，P2 前置）**：
- `OkHttpClient` / 线程池改为共享常驻单例（原每次 `parse` 新建 client + 5 线程池再销毁，连接与线程零复用，老盒子开销显著）。
- `futures`/`calls` 局部化：消除并发调用间互相 clear/cancel 的竞态；拿到首个结果后按 `Call` 精确取消本批，不再 `cancelAll()` 误伤并发批次。
- `completionService.take()` 无限等待改为 15s 整体超时：jx 全挂死不再永久阻塞解析线程。
- `Response` 显式 `close`，取消/异常路径不再泄漏连接。
- 修复 `getReqHeader`：`cat_ext` 为末位参数时 `end=-1` 导致 substring 越界异常 + `newUrl` 拼回整个 url。

**`SuperParse` 加固**：
- `doJsonJx(url)` 修复 `jsonJx` 为 null 时 `LOG` 直接 NPE；快照拷贝防遍历中被并发重写。
- `flagWebJx` 改 `ConcurrentHashMap`（`PlayActivity`/`PlayFragment` 各自解析池并发 put 的丢数据风险）。
- 注：`jsonJx` 静态竞态彻底修复需贯穿调用链重构，仍留在 P2（见 `docs/ROADMAP.md`）。

**`StoreApiConfig` 崩溃修复**：
- 订阅地址返回非 JSON（网关错误页/空内容）时，Gson 异常抛在 OkGo 回调线程直接崩溃 → `success`/`MutiUrl` 整体兜底，只提示不崩。
- 当前线路名不在 `storeMap` 时 `get()` 返回 null 直接传给 OkGo → 回退默认仓库地址。

**文档**：README 产物段对齐实际（包名 `tv.of.eu.box.daily`、版本指向 CHANGELOG）；ROADMAP P1 进展更新为"已打通、待真机回归"。

产物 `release/box-v1.0.14.apk`（versionCode 15，仅 `armeabi-v7a`）。

## [1.0.13] — 2026-10-31 关于页加 GitHub 地址
- "关于"对话框加入本发行版 GitHub：`https://github.com/zeyark88/box-lite`（保留上游出处）。
- 已核验地址在 APK 编译布局内。
- 产物 `release/box-v1.0.13.apk`（18MB，versionCode 14）。

## [1.0.12] — 2026-10-31 清理图标资源·核对源码
- 核对：manifest 图标 = `@mipmap/ic_launcher`（全密度不透明蓝紫渐变位图，alpha 全=255），无运行时改图标代码。
- 清理残留：删除旧白色 `app_icon`、废弃 `ic_launcher_background/foreground`；保留 `drawable/app_icon.png`（播放页用到）为蓝紫图标。
- 产物 `release/box-v1.0.12.apk`（19MB，versionCode 13）。

## [1.0.11] — 2026-10-31 修复图标黑块（彻底·全不透明位图）
- **彻底不用自适应图标**（部分电视/盒子启动器连自适应背景都不认 → 仍垫黑）。移除 `mipmap-anydpi-v26`，改用**全不透明位图**做图标：整块蓝紫渐变+白盒，**任何像素都不透明**，任何启动器都无法垫出黑块。
- 主图标已核验：APK 内为全不透明 PNG。
- 产物 `release/box-v1.0.11.apk`（18MB，versionCode 12）。

## [1.0.10] — 2026-10-31 修复图标黑块（自适应背景）
- **修复电视类设备黑块**：自适应图标 `<background>` 之前用 **PNG 位图**，部分安卓盒子/电视启动器不认位图当背景 → 显示成黑底。已改为 **XML 渐变 shape**（蓝紫对角渐变），任何启动器都正常。
- 前景（白盒+播放键）不变。
- 产物 `release/box-v1.0.10.apk`（18MB，versionCode 11）。

## [1.0.9] — 2026-10-31 修复图标黑块（终版）
- **彻底消除黑块**：legacy 图标改为**全不透明**（整块蓝紫渐变+白盒播放键，无任何透明角），任何启动器/任何背景都不再露黑；自适应图标（透明前景+不透明渐变背景）保持。
- 原版在部分设备黑块是上游通病（图标有透明区被启动器垫黑）；本版从根上规避。
- 产物 `release/box-v1.0.9.apk`（18MB，versionCode 10）。

## [1.0.8] — 2026-10-31 修复图标(续)
- **修复老设备(<安卓8) legacy 图标显示异常**：此前 legacy 渐变误用亮粉色，已统一为与自适应一致的**蓝紫渐变**+白盒播放键，圆角透明。
- 产物 `release/box-v1.0.8.apk`（18MB，versionCode 9）。
- 提示：Android 启动器会缓存旧图标，装新版前请先卸载旧包。

## [1.0.7] — 2026-10-31 修复图标黑块（自适应图标）
- **修复部分设备图标显示异常（黑块）**：改用**标准自适应图标**。
- 新增透明前景（白盒+播放三角）+ 蓝紫渐变背景 + `mipmap-anydpi-v26/ic_launcher.xml`，并补全 legacy `mipmap-*/ic_launcher.png`；manifest `android:icon/@roundIcon` 指向 `@mipmap/ic_launcher`。
- 产物 `release/box-v1.0.7.apk`（18MB，versionCode 8）。

## [1.0.6] — 2026-10-31 精简（去除冗余依赖）
- 移除 `com.google.net.cronet:cronet-okhttp` + `org.chromium.net:cronet-embedded`（代码 0 引用）→ **APK 20MB→18MB**，原生库 libcronet 消失。
- 复核 `juniversalchardet`（CharsetUtils/SubtitleLoader 在用）→ 保留；`brotli`（自研 urlhttp 栈在用）→ 保留。

## [1.0.5] — 2026-10-31 更换默认壁纸
- 按截图样式生成新的默认壁纸 `drawable/app_bg.png`：**左下暖橙黄 → 右上翠青/浅蓝的柔和对角线性渐变**（1440×958，轻磨砂雾感）。
- 已确认打进 APK（`BaseActivity` 默认 `R.drawable.app_bg`）。
- 产物 `release/box-v1.0.5.apk`（20MB，versionCode 6）。

## [1.0.4] — 2026-10-31 深层 bug 加固
- **`server/InputRequestProcess`**：远程推流/遥控 POST 参数缺失时 `.trim()` 会 NPE，已统一防空（恶意/残缺请求不再崩本地服务）。
- **字幕解析加固**：`subtitle/model/Time.java` 时间解析加 try-catch（畸形字幕不再 `NumberFormatException`/越界崩溃）；`Style.getRGBValue` 数值颜色分支加 try-catch（畸形色值返回 null 而非崩溃）。
- 复核确认无崩点：`JsonParallel` 的 `cat_ext` 解析已在 try 内；`StringUtils` 的 trim/substring 有长度边界保护。
- 备注（低危）：`LivePlayActivity` 的 `static SimpleDateFormat` 仅 UI 线程使用，暂不改动。

## [1.0.3] — 2026-10-31 定制化（禁用更新 + 改名 + 换图标）
- **禁用自动更新并删除更新地址**：`URL.GITHUB_VERSION_PATH` 置空；`App.initXUpdate()` 停用；设置"检查更新"改为提示"已禁用自动更新"（不再联网）。
- **改名**：`app_name` 由 `Jade` → `星辰盒`。
- **换图标**：用生成的新图标替换 `app_icon`（drawable 及 hdpi/xhdpi/xxhdpi/xxxhdpi 全密度）。
- 产物 `release/box-v1.0.3.apk`（19MB，versionCode 4，minSdk16，`应用名=星辰盒`）。

## [1.0.2] — 2026-10-31 启动闪退（续）：关闭 R8 混淆
- `app/proguard-rules.pro` 为空，但 release 此前 `minifyEnabled true`，R8 会删掉 Room/Gson/quickjs 等反射用类 → release 启动即崩（debug 正常）。
- **release 关闭 `minify`（`minifyEnabled false`）**，消除 R8 误删风险。
- 产物 `release/box-v1.0.2.apk`（19.6MB，versionCode 3，minSdk16 / armeabi-v7a）。

## [1.0.1] — 2026-10-31 启动闪退修复
- **修复启动闪退**：原生库（p2p/quickjs/ijk/player 等）仅预编译 `armeabi-v7a`，此前误把 release `abiFilters` 放开为三 ABI，导致 arm64/x86 设备缺 so、启动即崩。已还原为仅 `armeabi-v7a`（arm64 设备以 32 位兼容运行）。
- 产物 `release/box-v1.0.1.apk`（15.2MB，versionCode 2，仅 `lib/armeabi-v7a`）。

## [1.0.0] — 2026-10-31 首个可构建发行版
- **构建成功**：`assembleDebug` / `assembleRelease` 全通过；产物 `release/box-v1.0.0.apk`（约 20MB，v1+v2 双签名，1 签名者）。
- 补齐完整源码（`res`、`AndroidManifest`、`player`、`quickjs`、gradle 包装、`app/libs`）。
- 修复 2 处影响编译的构建问题：`FlowLayout` 依赖 groupId 笔误、根 `build.gradle` 死仓库。
- 签名改为从 `local.properties` 读取（keystore 在 `~/box-keys/`，**勿入库**）。
- 内含 P0 bug 修复 + P1 m3u8 原生核心（默认关闭），见 `docs/`。

## [Unreleased] — 初始骨架
- 清理 `app/build.gradle`：移除明文 key 与密码、改为 `local.properties` 读取。
- 版本号改为显式管理（`versionCode`/`versionName`）；`abiFilters` 放开为 `armeabi-v7a + arm64-v8a + x86`。
- 删除 Windows 专属 `DEBUG_APP`（硬编码 adb 路径）任务。
- 新增 `docs/`：`ANALYSIS.md`、`ROADMAP.md`、`DISTRIBUTION.md`；规范化 `README.md`、`CHANGELOG.md`、`.gitignore`。

### Added（P1 原生化，见 `docs/ROADMAP.md`）
- `app/src/main/jni/m3u8_core.c`：纯 C 复刻 `M3u8` 去广告过滤核心，已 `cc` 编译 + 样例自测通过。
- `app/src/main/jni/jni_m3u8.c` + `CMakeLists.txt` + `util/M3u8Native.java`：JNI 桥接。
- `M3u8.purify()` 接入原生路径（默认关闭 `useNative()`，待真机回归后放开）。

### Fixed（P0 bug 修复，见 `docs/ANALYSIS.md`）
- fix(H2): `MD5.java` 改为每次新建 `MessageDigest`，消除共享实例的线程安全问题。
- fix(H3): `AES.java` 先判空再 `trim()`，避免 null key NPE。
- fix(M1): `StoreApiConfig.MutiUrl` 合并既有历史，不再覆盖已存线路。
- fix(M2): `StoreApiConfig` 多仓/线路历史设 30 条上限，防止无限增长。
- fix(M3): `SuperParse` 每次按当前 `jx` 重建 `configs`，修复切源后配置不更新。
- fix(L1): `M3u8` 处理 `#EXT-X-KEY` 时对空 `URI=""` 的越界防护。
- chore: 删除源码中的 `AppUtils.java.bak`。

### 待办
- 编译验证需补齐完整源码（`res/`、`AndroidManifest.xml`、`player/`、`quickjs/`、预置 `.so`/`.apk`）。
- `SuperParse` 的 `jsonJx` 静态竞态完整修复（需贯穿调用链）待源码补全后执行。