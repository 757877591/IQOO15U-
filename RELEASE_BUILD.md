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

发布脚本执行 Release 组装与 Lint，检查签名、对齐、包名及 Xposed 入口后，将 APK 输出到 `dist/IQOOShoulderExtension-v0.1.0-alpha10-release.apk`。`dist/`、`.build-tools/`、`.signing/` 和本地生成文件均不提交到仓库。
