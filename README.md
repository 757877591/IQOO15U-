# IQOO肩键扩展

为具有原生肩键硬件的 iQOO 15 Ultra 扩展应用支持范围。

当前版本：`0.1.0-alpha11 / 10011`。包名：`ls.iqooaugment.com`。适配游戏魔盒：`14.0.16.00_de / 1401600`。

## 功能

- 按应用和空间分别选择目标，保留每个实例的独立开关。
- 扩展所选应用的原生准入和肩键菜单分类；单击、长按、连点、按下/抬起及多操作映射由系统原生功能提供。
- 冰蓝中文界面，自动跟随系统深浅色；支持搜索、空间筛选及新增草稿恢复。
- 配置变化与前台事件驱动更新，没有周期配置轮询或肩键会话续期。
- 应用进入前台时自动检查 GitHub 新版本（24 小时内最多一次）；“关于”页可手动检查。显示更新说明，由用户确认下载并交给系统安装。

## 安装与使用

1. 从 [软件发布仓库 Releases](https://github.com/liligit1815/lispacerelease/releases) 下载 iQOO 肩键扩展的 APK 并安装。同签名版本可覆盖安装。本仓库仅保存源码，不提供 Release 安装包。
2. 在兼容的 LSPosed 框架中启用模块，作用域勾选游戏魔盒 `com.vivo.gamecube`，然后重启设备。
3. 在模块界面核验 Root 和游戏魔盒版本；新增应用时先确认需要读取的空间，再选择应用。
4. 进入目标应用，通过原生游戏侧边栏配置肩键。

修改总开关或应用选择前，先松开肩键并退出目标应用；更改后重新进入。模块只改变之后的追加资格，已开启映射的保存与关闭仍由原厂处理。

王者等专用映射仍活动、原生空间无法确认时保持系统分类。原生方案可能按包名共享，应用资格按空间区分不代表各空间坐标独立。硬件、方向、温控、权限和冲突检查保留。

## 隐私

APK 声明联网与请求安装应用权限，仅检查和下载更新时连接 GitHub，无统计 SDK 或账号。更新请求不附带应用列表、空间、Root 状态或设备标识；GitHub 会接收到连接的网络信息。Root 仅用于读取用户确认空间中的应用；设备信息刷新不枚举应用。模块不写系统 Settings 或厂商方案数据库，运行状态仅保存在内存。

更新源为 `https://raw.githubusercontent.com/liligit1815/lispacerelease/main/updates/iqoo-shoulder.json`。无新版或自动检查失败保持静默；下载前须用户确认，下载后核验大小、SHA-256、包名、版本与现有签名。安装交给系统，首次需要允许本应用安装未知来源应用；取消后可在“关于”页重试。没有后台定时任务或静默安装。

## 以后如何发布在线更新

发布入口为 [lispacerelease 仓库](https://github.com/liligit1815/lispacerelease)，本软件固定使用 `--app iqoo-shoulder` 和 `updates/iqoo-shoulder.json`。源码继续保存在本项目中，更新仓库只保存发布信息与 APK 附件。

完整的构建到发布步骤、清单字段说明和发布后验证命令见 [RELEASE_BUILD.md 的发布章节](RELEASE_BUILD.md#发布到-lispacerelease)。合法清单示例见 [examples/iqoo-shoulder.json](examples/iqoo-shoulder.json)，示例对应已发布的 `10011`，正式清单由发布工具生成。

1. 在 `app/build.gradle` 递增 `versionCode`，并更新 `versionName`、更新记录及相关版本检查的预期值。版本代码必须大于远端清单中的当前值；目前为 `10011`，下一版可使用 `10012 / 0.1.0-alpha12`。更新检测按 `versionCode` 比较，不能只改显示版本或 APK 文件名。
2. 保持包名 `ls.iqooaugment.com`、现有发行签名及固定更新源。按 [RELEASE_BUILD.md](RELEASE_BUILD.md) 配置原签名材料，使用 `release.ps1`（Linux/macOS 使用 `release.sh`）构建已签名 Release APK。不要用 Debug 包或重新生成签名替换既有发行密钥。
3. 将本版更新说明保存为 UTF-8 `.md` 或 `.txt` 文件，说明变更内容和必要的使用注意事项，最多 12000 个字符。
4. 同步本地更新仓库，先执行发布工具的 `--dry-run`，确认软件、实际 APK 版本、签名、文件大小及更新说明；检查通过后使用相同参数去掉 `--dry-run` 正式发布。

构建产物位于 `dist/IQOOShoulderExtension-v<versionName>-release.apk`。Windows 构建需使用不含中文的源码路径；完整命令和可替换的 SDK、JDK、Python 路径见 [构建与发布说明](RELEASE_BUILD.md#发布到-lispacerelease)。首次克隆发布仓库后，按该文档同步、预检查、正式发布及验证。

`--dry-run` 只检查本地 APK 与本地更新清单，不访问 GitHub 或读取认证；正式发布需要 GitHub 写入权限，并会重新读取线上清单校验版本。认证配置见 [发布仓库说明](https://github.com/liligit1815/lispacerelease#发布新版)。令牌、密码和签名私钥只保留在本地。

工具先上传并公开 `iqoo-shoulder-v<versionCode>` Release，再推进 `main/updates/iqoo-shoulder.json`。远端 APK 附件名为 `iqoo-shoulder-<versionCode>.apk`；下载地址、大小、哈希和证书由工具生成。每个软件只有一个更新通道，推进 alpha/test 版本同样会向使用该更新源的用户提供更新。不要使用多软件仓库的 “Latest” 判断本软件新版。

发布后按 [验证步骤](RELEASE_BUILD.md#发布后验证) 核对独立清单和公开下载。上传中断可使用相同 APK 和参数重试；若最后响应丢失，先检查公开清单是否已推进。不要以同一个 `versionCode` 换包。

旧版 `0.1.0-alpha10 / 10010` 及更早版本没有在线更新入口，首次需手动覆盖安装 [首个在线更新版本 0.1.0-alpha11 / 10011](https://github.com/liligit1815/lispacerelease/releases/tag/iqoo-shoulder-v10011)，或后续保留更新功能的版本；之后才可通过“关于”页检查更新或前台自动检查（24 小时内最多一次）。

已安装版本与清单版本相同时，应显示“当前已是最新版本”。公开清单读取、下载、文件哈希和发行签名已通过主机校验；设备上的更新界面和系统覆盖安装仍待真机验证。

## 源码

构建方法见 [RELEASE_BUILD.md](RELEASE_BUILD.md)，架构见 [技术实现](docs/技术实现.md)，更新内容见 [CHANGELOG.md](CHANGELOG.md)。源码保留构建脚本、维护工具与规则代码；签名材料、依赖缓存、生成报告和构建产物不进入仓库。
