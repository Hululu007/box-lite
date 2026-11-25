# 构建工具链与发布流程（星辰盒 box-lite · 双架构）

本文档描述从零环境到签名发布 APK 的完整工具链。适用工程树：仓库根目录（32 位主工程）与 `arm64/`（64 位工程）。

## 1. 工具链版本矩阵

| 组件 | 版本 | 用途 | 备注 |
|---|---|---|---|
| JDK | 21 | Gradle daemon / javac / jar / javap | 源码级见第 9 节 |
| Gradle | 8.13 | 构建系统 | **独立安装**，`gradlew` wrapper 仍是上游遗留 6.7.1 旧值，**勿用 `./gradlew`** |
| AGP | 8.5.2 | Android Gradle Plugin | `com.android.application` / `com.android.library` |
| Chaquopy | 15.0.1 | Python 3.10 嵌入运行时插件 | 最后一个支持 minSdk 21 的版本（AGP 7.0-8.5） |
| Android SDK | compileSdk 35 | 编译目标 | 安装路径写在 `local.properties` 的 `sdk.dir` |
| build-tools | 30.0.1 | aapt / aapt2 / apksigner / zipalign | 发布校验用 |
| NDK | **27.0.12077973** | native 库编译与配套 | **硬约束**：libnode 依赖的 libc++_shared 必须用 NDK 27 版本，NDK 21 缺 48 个 C++ 符号不可用 |
| CMake | 3.22.1 | `:app` externalNativeBuild（libm3u8native） | 16KB 对齐；（旧项目可另装低版本） |
| 目标 Python | 3.10 | 打进 APK 的运行时 | 由 Chaquopy 提供 |
| 宿主 Python | 3.8+ | 构建期跑 pip（不上设备） | 路径写在 `app/build.gradle` 的 `buildPython`，**自建者须改成本机解释器路径** |

## 2. 工程树、版本序列与产物

| 项 | 仓库根（32 位） | `arm64/`（64 位） |
|---|---|---|
| ABI | armeabi-v7a | arm64-v8a |
| applicationId | `tv.of.eu.box.daily` | `tv.of.eu.box.daily.arm64`（同机共存） |
| versionCode 序列 | …39 → 40 → **41**（v2.0.1） | …37 → 38 → **39**（v2.0.1） |
| 本地服务端口 | 9978 | 19978 |
| 桌面标签 | 星辰影音32 | 星辰影音64 |
| 产物名 | box-v\<版本\>-arm32.apk | box-v\<版本\>-arm64.apk |

产物路径：`app/build/outputs/apk/release/`。每次发版 versionCode 递增，versionName 跟随语义化版本。

## 3. 签名

- 密钥库：`box-release.jks`（证书 DN：`CN=Box Lite, OU=Box Lite, O=zeyark88`，SHA-256 指纹 `4bfe34f1…784c7`）
- 配置从工程根 `local.properties` 读取，键：`box.storeFile` / `box.storePassword` / `box.keyAlias` / `box.keyPassword`（`.gitignore` 已排除，**严禁入库**）
- 密钥丢失 = 无法再覆盖升级，务必备份

## 4. 依赖源与镜像（settings.gradle）

- Maven：阿里云 google / public / gradlePlugin 镜像优先，mavenCentral、gradlePluginPortal、jitpack 兜底；jcenter 镜像与 CrossWalk 旧仓为历史遗留保留
- Chaquopy pip：
  - `--index-url https://pypi.tuna.tsinghua.edu.cn/simple` + pypi.org + `chaquo.com/pypi-13.1` 三源
  - C 扩展轮子用 `--platform android_24_armeabi_v7a`（64 位工程为 `android_24_arm64_v8a`）放行，**平台标签必须全下划线**（pip 20.1 不归一化，带连字符匹配不到轮子）
  - `--only-binary cffi,cryptography,lxml,ujson,pycryptodome,numpy`：防止 pip 选高版本源码包导致元数据阶段要宿主 MSVC
  - **arm64 轮子预下载机制**：chaquo.com 直连仅 ~12-34KB/s 必超时。轮子预下载到本地暂存目录，构建时另开终端跑 `python -m http.server 8765 --directory <轮子目录>` 作为本地扁平索引（chaquopy 内置 pip 20.1 在 Python 3.12 下本地路径 find-links 失效，HTTP URL 正常），pip 侧配 `--find-links http://127.0.0.1:8765/`。arm32 的 env 缓存已完整，无需此步

## 5. native 库生态（谁提供、谁加载）

| so | 来源模块 | 加载点 | 体积(v7a) |
|---|---|---|---|
| libnode.so / libnodebridge.so | :nodejs jniLibs | NodeService（:nodejs 进程） | 40.7 + 0.7MB |
| libc++_shared.so | :nodejs jniLibs（NDK 27 版） | libnode 依赖 | 0.9MB |
| libijkffmpeg / libijksdl / libplayer | :player jniLibs | IjkMediaPlayer（ijk 播放内核） | 10.3 + 0.2 + 0.3MB |
| libp2p.so | :player jniLibs | P2PClass | 3.6MB |
| libxl_thunder_sdk / libxl_stat | :player jniLibs | 迅雷 SDK | 3.5 + 0.5MB |
| libquickjs-android-wrapper.so | :quickjs jniLibs | QuickJSLoader（js 源引擎） | 0.8MB |
| libm3u8native.so | `:app` CMake 现场编译 | M3u8Native（m3u8 过滤） | 7KB |
| libpython3.10 + libcrypto/libssl/libsqlite3_chaquopy + libchaquopy_java | Chaquopy 插件注入 | Python 运行时 | ~5.9MB |
| libconceal.so | Hawk 2.0.1 传递依赖 | Hawk 默认加密（ConcealEncryption），**不可删** | 0.2MB |
| librtmp-jni.so | exoplayer extension-rtmp 传递依赖 | rtmp:// 直播拉流，**在用** | 0.06MB |

## 6. 构建命令

```powershell
# 32 位
Set-Location C:\bl-build\bl27
$env:JAVA_HOME='C:\bl-build\jdk21'
& 'C:\bl-build\gradle-8.13\bin\gradle.bat' :app:assembleRelease --console=plain
# 检查 $LASTEXITCODE == 0

# 64 位（首次或 pip 变更时，先另开终端启动轮子索引服务，见第 4 节）
Set-Location C:\bl-build\bl27-64
$env:JAVA_HOME='C:\bl-build\jdk21'
& 'C:\bl-build\gradle-8.13\bin\gradle.bat' :app:assembleRelease --console=plain
```

- 增量构建约 1.5~2 分钟；全量约 10 分钟
- release **不开启 minify/R8**（历史教训：proguard 规则为空时 R8 删反射用类导致启动崩；如启用须配套完整 keep 规则并真机回归）
- 模块级编译验证（快速冒烟）：`gradle :app:compileReleaseJavaWithJavac` 或 `:catvod:assembleRelease`

## 7. 发布校验与交付

```powershell
$bt='<SDK>/build-tools/30.0.1'
# 1. 验签 + 证书指纹
& "$bt\apksigner.bat" verify --print-certs <apk>
# 期望：CN=Box Lite, O=zeyark88；SHA-256 4bfe34f1…
# 2. 版本/包名/桌面标签
& "$bt\aapt.exe" dump badging <apk> | Select-String "package:|application-label:"
# 3. SHA256（写入 CHANGELOG/发布说明）
(Get-FileHash <apk> -Algorithm SHA256).Hash
# 4. 拷贝交付到工作空间根；旧版本包走回收站
```

**校验 APK 内资源注意**：AGP 8 会混淆资源文件名（如 `dialog_about` → `res/Tt.xml`），查内容需先 `aapt2 dump resources` 拿映射，再 `aapt dump xmltree <apk> res/Tt.xml`。

## 8. 双工程同步规程

1. 一律先改 **bl27**（32 位主工程）
2. 逐文件比对：`git diff --no-index <bl27文件> <bl27-64文件>`，确认差异仅在白名单内
3. 拷贝文件到 bl27-64，回改 64 位专属差异：
   - `RemoteServer.serverPort` = 19978（1 处）
   - `ApiConfig` 两处 `127.0.0.1:19978/proxy?do=live`
   - `strings.xml` 的 `app_name`（星辰影音32/64）
   - `build.gradle`：versionCode 序列、abiFilters、pip `--platform`、产物名后缀
4. 同步文档：CHANGELOG / SECURITY / docs 直接整文件复制

## 9. 依赖治理约定（根/模块 build.gradle）

| 约定 | 原因 |
|---|---|
| 源码级：app 1.8 + coreLibraryDesugaring；catvod 17（record/var）+ 脱糖 | catvod 用 Java 17 record，D8 需 desugar_jdk_libs 2.1.2 |
| `resolutionStrategy.force bcprov-jdk18on:1.79 / bcpkix-jdk18on:1.79` | smbj 0.15.0 传递的 1.85.2 是 Java 25 字节码（class major 69），Jetifier 无法解析 |
| `exclude org.jetbrains:annotations-java5` | chaquopy_java 与 kotlin-stdlib 重复类 |
| `exclude xpp3:xpp3_min` | app 的 xstream(xpp3_min) 与 catvod sardine→simple-xml(xpp3) 同名冲突 |
| `:catvod` 的 `okhttp-dnsoverhttps` 为 **compileOnly** | 运行期用 app 内本地定制实现（mutable、支持运行期切换 DoH）；若改 api 会与本地实现重复类致 mergeDex 失败 |
| `:chaquo` 的 `chaquopy_java` 为 compileOnly | chaquopy 插件会给 :app 自动注入同名 jar，implementation 会重复类 |
| `:nodejs` 对 catvod / nanohttpd 为 compileOnly | 运行期由宿主提供，同理防重复类 |
| `com.github.catvod.crawler` 包**跨模块拆分** | Spider 系在 ：catvod，JarLoader/JsLoader/PyLoader 在 ：app 同包；**升级 catvod 模块时官方若带同名类会重复类冲突，须剔除** |

## 10. 常见坑速查

| # | 坑 | 对策 |
|---|---|---|
| 1 | 用了 `./gradlew` | wrapper 还是 6.7.1，必失败；用独立 Gradle 8.13 |
| 2 | buildPython 路径是维护者机器路径 | 自建前改成自己本机 Python 3.8+ |
| 3 | PowerShell 5.1 `Set-Content` 写中文 Java 源码 | ANSI 写入毁 UTF-8；必须 `[IO.File]::WriteAllText` UTF8 无 BOM |
| 4 | pip 20.1 本地路径 find-links 报 "not a file" | 用 HTTP 扁平索引（8765 端口方案） |
| 5 | `--platform` 带连字符 | 必须全下划线 `android_24_arm64_v8a` |
| 6 | C 扩展被源码包抢占 | `--only-binary` 强制走轮子 |
| 7 | 构建机网络慢导致 pip 超时 | 轮子预下载 + 本地 HTTP 索引 |
| 8 | 换 NDK 21 编译/配 libc++_shared | libnode 需 NDK 27 的 libc++_shared（缺 48 符号） |
| 9 | release 开 minify | R8 删反射类启动崩；保持关闭或配全 keep 规则 |

## 11. 排查工具

- `aapt2 dump resources` / `aapt dump xmltree`：APK 资源取证（先查映射，AGP 已混淆资源名）
- `jar` / `javap -p -c`：依赖 jar 字节码取证（如定位 Hawk 默认加密、TypeToken 兼容问题）
- `git diff --no-index`：双工程逐文件对拍
- `gradle :app:dependencies --configuration releaseRuntimeClasspath`：依赖树定位传递依赖真凶
