# 星辰盒 box-lite · 64 位工程（bl27-64）

本目录是 [星辰盒 box-lite（星辰影音）](https://github.com/zeyark88/box-lite) 的 **arm64-v8a 构建工程**，与仓库根目录的 32 位主工程代码逐文件同步，可独立构建出与 32 位版**同机共存**的 64 位包。

完整的项目介绍、构建环境要求、安全与隐私说明见 32 位主工程的 [README.md](../README.md)；构建工具链详规见 [`docs/BUILD.md`](docs/BUILD.md)，此处只列本工程的差异。

## 与 32 位主工程的差异

| 差异点 | bl27（32 位） | 本工程（64 位） |
|---|---|---|
| abiFilters | armeabi-v7a | arm64-v8a |
| applicationId | `tv.of.eu.box.daily` | `tv.of.eu.box.daily.arm64`（同机共存） |
| versionCode 序列 | 40（v2.0.0） | 38（v2.0.0） |
| 本地服务端口 | 9978 | 19978（`RemoteServer.serverPort` + `ApiConfig` 两处 `127.0.0.1:19978/proxy?do=live`） |
| 应用名（桌面标签） | 星辰影音32 | 星辰影音64 |
| Chaquopy pip 平台 | `android_24_armeabi_v7a` | `android_24_arm64_v8a` |
| 产物命名 | box-v\<版本\>-arm32.apk | box-v\<版本\>-arm64.apk |

## 64 位构建注意事项

- Chaquopy 的 arm64 C 扩展 wheel 需从 chaquo 官方源下载，网速受限时可预下载 wheel 后用本地 HTTP 扁平索引（`pip.find-links`）供构建使用
- `app/build.gradle` 的 `buildPython` 为维护者机器路径，自建前请改成本机 Python 3.8+ 解释器路径
- 其余环境要求（JDK 21 / Gradle 8.13 / NDK 27 / CMake 3.22.1）与 32 位主工程一致；`gradlew` wrapper 仍是 6.7.1 旧值，请勿直接使用

## 同步维护约定

两工程由同一套改动驱动：以 32 位主工程为改动源，改完逐文件 diff 校验后同步过来，仅回改上表中的 64 位专属差异。CHANGELOG / SECURITY / docs 与主工程保持一致。
