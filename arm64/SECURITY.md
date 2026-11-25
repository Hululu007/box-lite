# Security（安全）

本仓库是 AGPL-3.0 开源发行版，安全问题请遵循最小公开原则。

## 报告安全漏洞
请通过 GitHub **私有安全公告（Private vulnerability reporting）** 或项目 Issue（标注 SEC）报告，不要公开贴 exploit。

## 安全审计与加固现状

**v1.1.8 全量源码安全审计（2026-11）**：对硬编码回连/自更新通道/动态加载外传/数据上报/bootstrap 脚本/反序列化/本地服务等 8 类后门专项定向排查，**结论为后门 0 发现**；无广告与统计 SDK；更新检查已禁用且更新地址为空串。审计发现的 P1/P2 风险已在 **v2.0.0 全量修复**：

- **A1** NodeJS 运行时接线缺失导致的静默失效，重构为后台加载 + 主线程防护 + 同源复用 + session 失效复位
- **A2** `SearchReceiver` / `CustomWebReceiver` 默认导出收敛（`exported=false` + onReceive 判空）
- **A3** 本地服务令牌白名单补齐 `/proxy`、`/dns-query`（封死无令牌局域网代理滥用；本机与遥控页不受影响）
- **A4a** 订阅客户端尊重 `SSL_STRICT_VERIFY` 开关；**B1** hosts 映射即时生效；**B2** DoH 5s 超时自动回退系统 DNS；**B3** 订阅线路判重；**B4** 空更新地址直接返回；**B5** 诊断日志 256KB 轮转
- 更早的 v1.0.23 已落地 B-01~B-10（本地服务令牌鉴权、路径穿越/zip slip 防护、jar 下载 MD5 校验、XStream 类型白名单与 XXE 拦截、allowBackup 关闭等），见 CHANGELOG

**已知取舍（有意保留）**：默认「全信任 TLS + 允许明文」以兼容 TVBox 生态的自签名源与老服务器，可在设置中开启 `SSL_STRICT_VERIFY` 切换严格校验；jar 无 MD5 场景遵循生态惯例直接加载（审计建议的知情提示待决策）。

## 已知要点（务必遵守）
1. **签名密钥严禁入库**：`.jks/.keystore`/签名密码只能放本机 `local.properties`（已 `.gitignore`）。
   上游 `mlabalabala/box` 曾把 keystore 与密码提交进仓库，本项目已移除。任何人拿到 keystore 都能伪签同名包做**更新劫持**。
2. **下载安装**：只从官方 GitHub Release 安装，核对 `SHA256SUMS.txt` 与签名。
3. **数据源**：本应用仅聚合第三方数据源；源内容版权归内容方。请勿用于传播侵权/违法内容。
4. **更新模块**：本项目"检测更新"永远不强制更新；请勿在后添加强制更新逻辑。
5. **native 层**：`app/src/main/jni/` 的 C 代码改动需通过 `tests/m3u8_core_test.sh` 且 `-Wall -Wextra` 零告警，防止内存问题。

## 依赖
保持依赖版本明确（见 `app/build.gradle`），升级时关注已知 CVE。