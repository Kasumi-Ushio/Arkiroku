![Phi-Tracker](https://files.seeusercontent.com/2026/04/04/e5Cc/Phi-Tracker.png)

# Arkiroku

一款可以用于读取 Phigros TapTap 云存档并查分的小工具。一次导入，随时查分。

## 项目简述

Arkiroku 是一个基于 Kotlin Multiplatform 的 Phigros 查分工具，通过读取 TapTap 云存档来获取玩家的游玩数据，并提供查分、统计等功能。

当前，Arkiroku 支持 Android 和 iOS 平台，对于其它平台（例如桌面平台和 Web 端）的支持还在初期调研阶段。

Arkiroku 的目标是成为一款功能全面、易用、简洁且美观的 Phigros 查分工具，让玩家能够方便地查看自己的游戏数据，并在现有的 QQ Bot 查分解决方案之外，提供另一种选择。

目前，Arkiroku 已经支持以下功能：

- 通过 TapTap 扫码登录或 sessionToken 两种方式登录
- B30 成绩展示与详细的单曲曲目详情
- 全部曲目搜索，支持筛选条件和通配符
- 包括等效 RKS 计算、RKS 变化趋势显示和导出 sessionToken 等多项工具功能
- B30 图片展示
- 通过统一查分 API 获取更多统计数据，并解锁更多工具功能
- 高清曲绘预览和保存功能
- 主页可显示最近三次同步历史
- 分离式曲目信息更新，无需在每次游戏本体更新后都全量更新程序


## 构建

### Android 构建

本项目使用 Gradle 构建系统，因此在构建时，您可能需要确认您当前的系统是否安装了任意 JDK 17+ 发行版。

在 Ubuntu 24.04.x LTS 系统下，构建一个 Android APK 的流程如下：
<details>
<summary>点击展开</summary>

1. 同步本仓库至本地：
```bash
git clone https://github.com/Kasumi-Ushio/Phi-Tracker`
```

2. 安装 JDK 17+（推荐使用 JDK 21）和安装 SDK 必要的依赖：
```bash
sudo apt update && sudo apt full-upgrade
sudo apt install openjdk-21-jdk unzip wget
```
3. 安装 Android SDK：
    1. 创建 SDK 目录：
    ```bash
    mkdir -p ~/Android/Sdk/cmdline-tools
    ```
    2. 下载 Android SDK Command-line Tools：
    ```bash
    cd ~/Android/Sdk/cmdline-tools
    wget https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip
    ```
    3. 解压：
    ```bash
    unzip commandlinetools-linux-14742923_latest.zip -d .
    mv cmdline-tools latest
    ```
    4. （可选）清理已下载的压缩包：
    ```bash
    rm commandlinetools-linux-14742923_latest.zip
    ```
    5. 配置环境变量：
    ```bash
    echo 'export ANDROID_HOME="~/Android/Sdk"' >> ~/.bashrc
    echo 'export ANDROID_HOME=$ANDROID_SDK_ROOT' >> ~/.bashrc
    echo 'export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"' >> ~/.bashrc

    # 可选，如果您需要 adb 和 fastboot：

    echo 'export PATH="$PATH:$ANDROID_HOME/platform-tools"' >> ~/.bashrc
    source ~/.bashrc
    ```
    6. 阅读并接受 SDK 许可协议：
    ```bash
    sdkmanager --licenses

    # 输入 y 接受所有许可协议
    ```
    7. 安装构建工具：
    ```bash
    # 我们构建的 Target SDK 版本是 36，因此您需要下载对应的 SDK：

    sdkmanager "build-tools;36.0.0" "platforms;android-36" "ndk;27.0.12077973"

    # 如果您需要 adb 和 fastboot：

    sdkmanager "platform-tools"
    ```
    至此，Android SDK 应该已经被安装完毕。

4. 配置 Gradle：
    1. 为了生成 Release 构建，您需要预先配置您自己的 KeyStore：
    ```bash
    # 如果您此时还在 cmdline-tools 目录中，请先返回至本项目的根目录

    cd /path/to/Ushio-Prober-Phigros
    keytool -genkeypair -v -keystore release.keystore -alias phitracker -keyalg RSA -keysize 2048 -validity 10000
    ```
    命令运行时，会要求您输入 KeyStore 的密码、别名、姓名、组织等信息，请根据提示输入即可，如果您只想构建 Debug 版本，则可以跳过这一步，但请先确认您现在在项目的根目录。

    2. 创建一个 local.properties 文件，在文件中需要填入以下内容：

    ```
    sdk.dir=/path/to/Android/Sdk
    RELEASE_STORE_FILE=/path/to/your/release.keystore
    RELEASE_KEY_ALIAS=phitracker
    RELEASE_STORE_PASSWORD=your_password
    RELEASE_KEY_PASSWORD=your_password
    ```
    以上的五项配置需要根据您的实际情况填写，其中所有路径必须为**绝对路径**，且**不可**包含空格。


5. 构建 Arkiroku：

    若要构建 Debug 版本，请运行：
    ```bash
    ./gradlew :androidApp:assembleDebug
    ```
    若要构建 Release 版本，请运行：
    ```bash
    ./gradlew :androidApp:assembleRelease
    ```

构建完成后，您可以在 `androidApp/build/outputs/apk/debug` 或 `androidApp/build/outputs/apk/release` 目录下找到生成的 APK 文件。
</details>


在其它 GNU/Linux 发行版上的构建流程与 Ubuntu 24.04.x LTS 基本相同，但请根据您系统的实际情况安装对应的软件包。

理论上说，在 Windows 和 macOS 上的构建流程也大致相似，只要有了 Android SDK 和 JDK，就可以完成构建。

### iOS 构建

iOS 构建的流程与 Android 构建流程有所不同，其中一个显著区别是：iOS 平台的构建必须且只能在 macOS 环境中完成。

我们用于构建 Arkiroku 的 macOS 版本为 macOS 26 Tahoe，但截止目前，macOS 15 Sequoia 和 macOS 14 Somona 配合能获得的最新版 Xcode 应该仍能编译本项目。

_请注意：在 macOS 27 Golden Gate 正式版发布后，macOS 14 Somona 将无法编译本项目。_

具体而言，在 macOS 环境中构建 iOS 平台版本的流程如下：

<details>
<summary>点击展开</summary>

1. 安装 JDK 17+ （推荐使用 JDK 21）和 Android SDK。
2. 安装 Xcode，Xcode 在 App Store 中可用。
3. 同步仓库至本地：
    ```zsh
    git clone https://github.com/Kasumi-Ushio/Phi-Tracker
    ```
4. 在项目根目录创建 `local.properties`，并填写以下内容：
    ```
    sdk.dir=/path/to/your/Android/Sdk
    ```
    _注：如果您安装 Android SDK 的方式是直接安装了整个 Android Studio，则应该存在一个 `ANDROID_HOME` 环境变量，此时也可以不设置此项。_

5. 使用 Xcode 打开工程，工程文件位于本项目根目录的 `iosApp/iosApp.xcodeproj` 目录下。
    <details>
    <summary>如果您拥有一个已经认证的 Apple 开发者账户，请点击这里：</summary>

    6. 在顶部选择运行目标，如果为真机则需要签名，您可以在 Xcode 中选择您的开发团队完成签名，或者手动在 `iosApp/Configuration/Config.xcconfig` 中填入您的 `TEAM_ID`:
        ```
        TEAM_ID=YourAppleDeveloperTeamID
        ```

    7. 选择构建配置：`Product -> Build` 可构建 `Debug` 版本，如需构建 `Release` 版本，则改为 `Product -> Archive`。
    8. 执行构建。
    9. 如果您选择了 `Archive`，则您可以在 Organizer 中打开 Distribute App，并选择您喜欢的分发方式，然后按照向导自动签名并导出，此时您得到的就是已签名的 ipa 文件了。
    10. 您可以将 ipa 文件直接拖拽到已连接的 iPhone/iPad 上安装，或者可以在 Xcode -> Window -> Devices and Simulators 选择您的设备，进入 Installed Apps，点击“+”号，选择已导出的 ipa 包安装即可。
    
        _注：您也可以将您编译完成的 ipa 包分发给其他人，但您必须遵守我们对您的许可协议中的相关条款。_

        <details>
        <summary>如果您想将您的编译产物通过 Apple App Store 分发，请点此：</summary>

        根据 GNU 通用公共许可证第 3 版中第 10 条 _Automatic Licensing of Downstream Recipients_ 和第 12 条 _No Surrender of Others' Freedom_ 的规定，**您不能在分发您编译的软件包时以任何方式限制其他人行使依据许可协议获得的权利**。
        
        而又根据 Apple Developer Program 许可协议中第 7 条 _Distribution of Applications and Libraries_ 中的第 7.6 节 _No Other Distribution Authorized Under this Agreement_ 中的规定，**您不被允许且您需要采取措施阻止您的用户通过未在 Apple 许可协议中记载的方式分发许可应用**。

        这两项规定直接冲突，并且我们未获得参考项目开发者的许可来向您颁发于此类分发渠道分发程序时的豁免条款，因此虽然很遗憾，但我们不得不告诉您：**您不能将您编译的产物分发至 App Store，否则您将同时构成对我们和 Apple 向您的许可协议的违反，从而导致您侵权行为的成立。**

        </details>

    </details>
    <details>
    <summary>如果您没有已经认证的 Apple 开发者账户，请点击这里：</summary>

    6. 左侧选中 `iosApp` 工程 → TARGETS 里的 `iosApp` → Signing & Capabilities 选项卡,取消勾选 Automatically manage signing，Team 选择 `None`（工程 `Config.xcconfig` 中 `TEAM_ID` 默认已为空）。 
    7. 在顶部选择运行目标。
    8. 选择构建配置：`Product -> Build` 可构建 `Debug` 版本，如需构建 `Release` 版本，则改为 `Product -> Archive`。
    9. 执行构建。
    10. 若用 Build 构建：左侧 Project Navigator → Products 分组 → 右键 `Arkiroku.app` → Show in Finder（会定位到 DerivedData 的 `Debug-iphoneos/` 或 `Release-iphoneos/`）；若用 Archive：归档完成后 Organizer 会打开 → 右键该归档 → Show in Finder → 右键 `.xcarchive` → Show Package Contents → `Products/Applications/Arkiroku.app`。
    11. 在 `Arkiroku.app` 所在目录通过访达新建 `Payload` 文件夹 → 放入 `.app` → 压缩 → 把 `.zip` 后缀改为 `.ipa`。
    12. 完成。此法得到的 ipa 包未经签名，需要通过 AltStore/SideStore/LiveContainer 等方式安装。
    </details>
</details>

## 免责声明

本项目是一个非官方项目，与 Pigeon Games 和 TapTap 之间没有任何关联，也不以任何形式受其认可或支持。用户在使用过程中导入的所有资源均为其各自所有者的财产，我们不主张也不拥有这些资源的任何权利。

尽管本项目是自由软件，并依据 GNU 通用公共许可证允许商业使用，但我们不鼓励、不支持任何形式的商业用途。任何因商业用途而产生的反馈、问题或争议，我们概不负责。

**我们不支持，且法律明确禁止任何形式的对 TapTap 和/或 Pigeon Games 的网络攻击或其他非法行为。使用本项目进行此类活动所产生的任何法律风险由用户自行承担。将本项目用于在用户所在国家或地区的刑法所禁止的任何形式的非法活动的后果由用户自行承担。**

**本项目不提供任何形式的担保，包括但不限于适销性、特定用途的适用性和非侵权性。在任何情况下，我们均不对因使用本项目而产生的任何索赔、损害或其他责任负责。**

## 版权与许可协议表述

版权所有 © 2026 Kasumi's IT Infrastructure

版权所有 © 2026 铃萤-RinLin a.k.a. 朝比奈ほたる 及所有 Arkiroku 贡献者

Arkiroku 是自由软件，请参阅 [LICENSE.md](https://github.com/Kasumi-Ushio/Ushio-Prober-Phigros/blob/main/LICENSE.md) 获取详细的许可协议内容。