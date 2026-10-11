# 构建

## 环境

| 项目 | 配置 |
|---|---|
| JDK | 17 或 21，Java 源码级别 17 |
| Gradle | 8.11.1，由构建脚本下载并校验 |
| Android Gradle Plugin | 8.9.2 |
| Android SDK | Platform 35、Build-Tools 35.0.0 |
| Xposed API | 82，仅作为编译依赖 |
| Python | Python 3，用于维护工具和 Linux/macOS 发布脚本 |

设置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 指向 SDK。Windows 也可通过 `-SdkPath` 指定路径。`prepare-sdk.ps1` 可准备所需 SDK，许可由使用者自行确认。

Windows 请将源码放在不含中文等非 ASCII 字符的路径，例如 `C:\Projects\IQOO15U`。

## Debug

Windows PowerShell：

```powershell
.\build.ps1 -SdkPath "C:\Android\Sdk"
```

Linux/macOS：

```sh
export ANDROID_HOME=/path/to/android-sdk
bash build.sh
```

默认执行 Debug 组装和 Lint，APK 位于 `app/build/outputs/apk/debug/`。

## Release

提供以下四个签名环境变量，或将自己的匹配密钥和凭据保存在本地 `.signing/iqoo-release.p12` 与 `.signing/credentials.json`。凭据 JSON 使用 `alias`、`password` 字段。

| 环境变量 | 用途 |
|---|---|
| `IQOO_KEYSTORE` | 密钥文件路径 |
| `IQOO_STORE_PASSWORD` | 密钥库密码 |
| `IQOO_KEY_ALIAS` | 签名条目别名 |
| `IQOO_KEY_PASSWORD` | 私钥密码 |

四个变量须全部提供；缺失签名材料时脚本停止，不自动生成替代密钥。保持同一签名身份才能覆盖安装已有版本。

Windows PowerShell：

```powershell
.\release.ps1 -SdkPath "C:\Android\Sdk"
```

Linux/macOS：

```sh
bash release.sh
```

发布脚本执行 Release 组装与 Lint，检查签名、对齐、包名及 Xposed 入口后，将 APK 输出到 `dist/IQOOShoulderExtension-v0.1.0-alpha11-release.apk`。`dist/`、`.build-tools/`、`.signing/` 和本地生成文件均不提交到仓库。

## 发布到 lispacerelease

源码仓库为 `liligit1815/IQOO15U-`，APK 和在线更新清单发布到 [liligit1815/lispacerelease](https://github.com/liligit1815/lispacerelease)。`release.ps1` / `release.sh` 只构建和校验本地 APK；上传 Release 和推进在线更新由发布仓库中的 `tools/publish.py` 完成。

1. 在 `app/build.gradle` 递增 `versionCode` 并更新 `versionName`、更新记录和版本检查的预期值。新版本代码必须大于线上清单中的当前值。保持包名 `ls.iqooaugment.com` 和既有发行签名；不能只改文件名或显示版本来触发更新。
2. 按上文构建 Release，保存 `dist` 中的 APK、`SHA256SUMS.txt` 和校验报告。发布工具会再次从 APK 读取真实版本、包名、最低系统要求和证书，并计算文件哈希。
3. 首次克隆发布仓库；已有仓库时同步 `main`，并准备 UTF-8 更新说明文件。发布工具需要 Python 3.11+、JDK 和 Android SDK Build-Tools。
4. 使用 `--app iqoo-shoulder` 做离线预检查，核对发布计划后用相同参数正式发布。

以下以**待构建的下一版** `0.1.0-alpha12 / 10012` 为例，路径需改成自己的实际位置。首次准备发布仓库：

```powershell
git clone https://github.com/liligit1815/lispacerelease.git "C:\Projects\lispacerelease"
```

已有发布仓库时，从同步开始执行；先将更新说明保存到 `$Notes` 指定的文件：

```powershell
$ReleaseRepo = "C:\Projects\lispacerelease"
$ReleasePython = "python" # Python 3.11+，也可填写解释器的完整路径
$Apk = "C:\Projects\IQOO15U\dist\IQOOShoulderExtension-v0.1.0-alpha12-release.apk"
$Notes = "C:\Projects\iqoo-shoulder-notes.md"
$Sdk = "C:\Android\Sdk"
$Jdk = "C:\Program Files\Android\openjdk\jdk-21.0.8"

git -C $ReleaseRepo pull --ff-only
& $ReleasePython "$ReleaseRepo\tools\publish.py" --app iqoo-shoulder --apk $Apk --notes-file $Notes --sdk $Sdk --java-home $Jdk --dry-run
```

`--dry-run` 不联网、不读取 GitHub 认证，只核验本地 APK 和本地清单，因此先同步发布仓库。正式发布还会重新读取线上清单，拒绝版本回退、同版本代码换包、其他包名或签名变化。正式发布要求 GitHub 仓库写权限；认证可使用发布工具支持的 `GH_TOKEN` / `GITHUB_TOKEN`、已登录的 GitHub CLI 或 Git Credential Manager，配置见 [发布仓库说明](https://github.com/liligit1815/lispacerelease#发布新版)。

确认预检查结果后，在同一窗口执行：

```powershell
& $ReleasePython "$ReleaseRepo\tools\publish.py" --app iqoo-shoulder --apk $Apk --notes-file $Notes --sdk $Sdk --java-home $Jdk
git -C $ReleaseRepo pull --ff-only
& $ReleasePython "$ReleaseRepo\tools\validate.py"
```

正式发布顺序是：上传 APK，公开 `iqoo-shoulder-v<versionCode>` Release，再更新 `main` 分支的 `updates/iqoo-shoulder.json`。远端附件名固定为 `iqoo-shoulder-<versionCode>.apk`，与本地构建产物名称不同；下载地址由工具生成。只推进本软件的清单，不使用多软件仓库的 “Latest” 判断 iQOO 新版。

每个软件只有一个通道，alpha/test 版本只要被推进清单，也会成为客户端候选更新。网络中断后用相同 APK、更新说明和参数重试；若最后响应丢失，先检查公开清单，避免把已成功发布误判为失败。不要以同一个 `versionCode` 替换已公开 APK。发布记录保存到发布仓库忽略的 `out/published/`，下次预检查前再次同步本地清单。

## 更新清单示例与约束

应用固定读取：

```text
https://raw.githubusercontent.com/liligit1815/lispacerelease/main/updates/iqoo-shoulder.json
```

[examples/iqoo-shoulder.json](examples/iqoo-shoulder.json) 是已发布 `0.1.0-alpha11 / 10011` 的完整合法示例。时间、大小、文件哈希和证书指纹均对应该版实际 APK；它是格式参考，不应复制成下一版的正式清单。正式数据由 `tools/publish.py` 自动生成。

| 字段 | 要求 |
|---|---|
| `schemaVersion` | 整数 `1` |
| `appId` | 固定为 `iqoo-shoulder` |
| `packageName` | 固定为 `ls.iqooaugment.com` |
| `release` | 已发布时为下述对象；首次尚未发布时为 `null` |
| `release.versionCode` | JSON 整数，范围 `1..2147483647`；必须高于已安装版本才提示更新 |
| `release.versionName` | 与 APK 显示版本完全一致，非空，最多 100 个字符 |
| `release.minSdk` | JSON 整数，范围 `1..1000`，与 APK 的最低 API 完全一致；高于设备 API 时不可安装 |
| `release.publishedAt` | ISO 8601 UTC 时间，以 `Z` 结尾，例如 `2026-10-11T12:11:21Z` |
| `release.notes` | 字符串，最多 12000 个字符，用 `\n` 表示换行；发布工具要求非空 |
| `release.apk.url` | 本仓库 HTTPS Release APK 附件地址，不允许查询参数、片段或其他仓库 |
| `release.apk.size` | JSON 整数，实际 APK 字节数，范围 `1..536870912` |
| `release.apk.sha256` | 实际 APK 文件 SHA-256，64 位小写十六进制字符串 |
| `release.apk.certificateSha256` | APK 签名证书 SHA-256，64 位小写十六进制字符串；同时必须与已安装版本的单一签名证书一致 |

清单最大 64 KiB。数字字段不能写成字符串或小数；不能含重复字段。下载 URL 的 tag 和 APK 文件名仅允许 ASCII 字母、数字及 `._+-`，并拒绝 `..`、百分号编码和多余路径层级。下载跳转仅接受 GitHub 官方 `*.githubusercontent.com` HTTPS 地址。

更新代码依据 `versionCode` 判断新版，下载后核验大小、文件哈希、包名、版本名称和代码、最低系统要求、签名证书；每次安装前再次核验保留的 APK。重新签名、证书轮换或 Debug 包均不适合覆盖当前发行版。

## 发布后验证

`tools/validate.py` 只验证本地清单结构，不会检查公开下载或 Android 安装。正式发布后先运行上述同步与验证，再检查公开链路。在同一 PowerShell 窗口执行，`$ExpectedVersionCode` 填本次实际发布的版本代码；核验现有 alpha11 时填写 `10011`：

```powershell
$ErrorActionPreference = "Stop"
$ExpectedVersionCode = 10012
$FeedUrl = "https://raw.githubusercontent.com/liligit1815/lispacerelease/main/updates/iqoo-shoulder.json"
$Feed = Invoke-RestMethod -Uri $FeedUrl
if ($Feed.schemaVersion -ne 1 -or $Feed.appId -ne "iqoo-shoulder" -or $Feed.packageName -ne "ls.iqooaugment.com" -or $null -eq $Feed.release) {
    throw "公开清单的软件身份或格式不匹配"
}
$Release = $Feed.release
if ($Release.versionCode -ne $ExpectedVersionCode) { throw "公开清单尚未指向本次版本" }
$ExpectedUrl = "https://github.com/liligit1815/lispacerelease/releases/download/iqoo-shoulder-v$ExpectedVersionCode/iqoo-shoulder-$ExpectedVersionCode.apk"
if ($Release.apk.url -cne $ExpectedUrl) { throw "下载地址不匹配" }

$Downloaded = Join-Path $env:TEMP "iqoo-shoulder-$ExpectedVersionCode-verified.apk"
Invoke-WebRequest -Uri $Release.apk.url -OutFile $Downloaded
if ((Get-Item -LiteralPath $Downloaded).Length -ne $Release.apk.size) { throw "APK 大小不匹配" }
if ((Get-FileHash -LiteralPath $Downloaded -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Release.apk.sha256) { throw "APK SHA-256 不匹配" }

$BuildTools = Join-Path $Sdk "build-tools\35.0.0"
$Signer = Join-Path $BuildTools "lib\apksigner.jar"
$Java = Join-Path $Jdk "bin\java.exe"
$CertificateReport = & $Java -jar $Signer verify --verbose --print-certs $Downloaded
if ($LASTEXITCODE -ne 0) { throw "APK 签名验证失败" }
$CertificateText = $CertificateReport -join "`n"
$Certificates = [regex]::Matches($CertificateText, 'Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})')
if ($Certificates.Count -ne 1) { throw "必须使用单一发行签名证书" }
$ActualCertificate = $Certificates[0].Groups[1].Value.ToLowerInvariant()
if ($ActualCertificate -cne $Release.apk.certificateSha256) { throw "签名证书与清单不匹配" }
$ExpectedCertificate = "7181eae8b831a960d040f9f5c00b656f08849b080751772e81c66c37d01224f2"
if ($ActualCertificate -cne $ExpectedCertificate) { throw "签名证书与既有发行版不匹配" }
& (Join-Path $BuildTools "aapt.exe") dump badging $Downloaded
if ($LASTEXITCODE -ne 0) { throw "APK 元数据读取失败" }
```

核对最后的 APK 元数据输出：包名为 `ls.iqooaugment.com`，`versionCode`、`versionName` 和 `sdkVersion` 与清单完全一致。示例命令同时验证当前发行证书指纹；更换设备或安装过重新签名包时，还需与该设备已安装版本对照。以上检查确认的是公开清单、附件和发布信息，设备流程仍需下述验证。

1. 在已装有在线更新功能且版本代码低于新版本的设备上，进入“关于”页手动检查，确认显示目标版本和更新说明。自动检查在进入前台时触发，距上次自动尝试满 24 小时才再请求；发布后立即验证使用手动检查。
2. 确认下载，核对进度和取消行为；重新下载后，验证未知来源授权返回、系统覆盖安装和安装后的版本。取消安装时应能从“关于”页重试已下载版本。
3. 检查旋转屏幕、切到后台再回前台，以及无网络时的提示。当前版本等于清单版本时，应提示“当前已是最新版本”。

`10010` 及更早版本没有在线更新功能，需要先手动安装 [alpha11 / 10011](https://github.com/liligit1815/lispacerelease/releases/tag/iqoo-shoulder-v10011) 或后续保留更新功能的版本。现有线上版本为 `10011` 时，已安装 `10011` 不会收到升级提示；真正的在线升级安装需等后续更高版本并在设备上验证。
