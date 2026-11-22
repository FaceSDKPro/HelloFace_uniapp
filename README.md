# HelloFace 离线人脸识别与活体检测

为你的 uni-app / uni-app x 应用接入人脸录入、1:1 身份比对与活体检测，一套 UTS API 面向 Android、iOS、鸿蒙，适配 Vue2、Vue3、uvue。

自带原生采集、录入确认和动作引导界面；静默与动作活体按需组合，也可只验真人而不比对身份。支持导入已有的同模型人脸基准，减少重复录入，适合账号本人确认、签到核验与人员验证流程的技术评估。

功能介绍、接入示例和接口列表见 [插件使用说明](uni_modules/HelloFace-Verify/readme.md)。插件 ID：`HelloFace-Verify`；底层 SDK：HelloFace 0.8.15。

示例源码和问题反馈见 [HelloFace_uniapp GitHub 仓库](https://github.com/FaceSDKPro/HelloFace_uniapp)。

## 运行示例

1. 从插件市场导入完整插件和示例，确认 `uni_modules/HelloFace-Verify` 含目标平台 SDK；源码中缺失的 SDK 可按 [开发维护](#开发维护)说明补齐。
2. 用 HBuilderX 导入根目录，配置自己的 AppID、包名和签名，按 [打包指南](docs/BUILDING.md)运行到真机。默认 Vue3；Vue2 将 `manifest.json` 的 `vueVersion` 改为 `"2"`。
3. uni-app x 示例需把根目录的 `uni_modules/HelloFace-Verify`、`app-icons`、`demo`、`static` 及公开配置 `harmony-configs/entry`、`harmony-configs/AppScope` 复制到 `examples/uni-app-x` 对应目录，再单独导入并配置。可选用 `npm run demo:prepare` 自动同步，该辅助命令需要 Python；私有签名配置单独维护。

Android / iOS 可选云端制作基座或完整本地构建，鸿蒙使用本地 DevEco 工具链。运行产物必须包含真实 SDK；Android 模型不压缩配置见 [插件接入说明](uni_modules/HelloFace-Verify/readme.md#接入)。修改 SDK、依赖、权限或资源后需重建。
**集成到应用后一个CPU架构仅新增10MB左右体积(Android可以配置仅支持ARM64位CPU)**
**使用完整插件运行 Demo 不需要 Python，也不强制在线制作自定义基座。**

## 环境要求

| 项目 | 要求 |
| --- | --- |
| HBuilderX | 5.34+，安装对应 UTS 原生运行扩展 |
| Android | Android 6.0 / API 23+，ARM64 / ARMv7 |
| iOS | iOS 15+；本地构建需兼容 Swift 6.1.2 的 Xcode（16.3+） |
| HarmonyOS | API 20+，ARM64，DevEco Studio 26 |

Web、小程序不支持此插件。各平台实际编译与真机结果见 [验证记录](docs/VALIDATION.md)。

## Demo 与接入

在 [uni-app 首页](pages/index/index.vue) 或 [uni-app x 首页](examples/uni-app-x/pages/index/index.uvue) 的 `methods` 开头可以找到三个按钮的插件调用。从 `@/uni_modules/HelloFace-Verify` 导入即可使用，插件会按调用初始化 SDK、申请权限并打开原生相机页。

| 想体验 | 从哪里开始 | 必要参数 |
| --- | --- | --- |
| 录入本人 | `enrollFace`，在原生页确认保存 | `faceID`，例如 `demo-user` |
| 1:1 验证 | `verifyFace`，使用已录入的本机基准 | 录入时的同一个 `faceID`。静默活体默认开启，可通过 `actions` 追加动作 |
| 独立活体 | `detectLiveness`，可直接开始 | 无。默认做静默活体，可选传 `actions`，例如 `['blink', 'nodHead']` |

验证是否通过以 `success` 回调里的 `passed === true` 为准。未通过也可能进入 `success`，调用错误走 `fail`；`complete` 用于恢复按钮等收尾。需要显示结束帧时，把非空的 `frameJpegBase64` 加上 `data:image/jpeg;base64,` 前缀传给 `image`，用 `mode="aspectFit"` 显示整帧。请避免记录包含帧或特征的完整回调。

同步按钮使用内置 [AI 演示基准](demo/README.md)；同一 `faceID` 已有基准时，确认后才会替换。试验 1:1 本人验证时，请先录入本人。页面的开关、确认弹窗和结果卡片是演示交互，业务接入可从 [最小调用](docs/API.md#最小调用) 开始。各接口参数和错误码见 [API 参考](docs/API.md)。

## 开发维护

以下为源码开发维护流程，使用完整插件接入业务或运行 Demo 无需执行这些脚本。

GitHub 仓库不附带大型 SDK 二进制。开发者可使用 Python 3.9+、curl 和网络安装、校验 SDK：

```sh
python3 scripts/install_sdk.py
python3 scripts/install_sdk.py --check
```

仅安装单端可加 `--platform android`、`--platform ios` 或 `--platform harmony`；也可按 `sdk-artifacts.json` 手动放置对应 SDK 文件。

插件只维护根目录 `uni_modules/HelloFace-Verify`。修改后执行 `npm run demo:prepare` 同步 uvue 副本；修改共用原生页面配置 `native-ui.json` 后，先执行 `npm run ui:build`。

```sh
npm ci --ignore-scripts
npm run check               # 页面配置、测试、SDK 校验与三端 UTS 转换
npm run android:alignment   # SDK AAR / 最终 APK 的 16KB 对齐检查
```

这些检查不代替完整 App 构建或真机验收。更多说明：[代码结构](docs/CODE_STRUCTURE.md)、[原生页面](docs/NATIVE_UI.md)、[应用图标](app-icons/README.md)、[Android 调试](docs/ANDROID_DEBUG.md)、[iOS 调试](docs/IOS_DEBUG.md)、[鸿蒙调试](docs/HARMONY_DEBUG.md)。

## 体验与联系

- 🌍 **网站说明**：[HelloFace SDK](https://helloface.dev/)
- 📧 **EMAIL**：[FaceAISDK.Service@gmail.com](mailto:FaceAISDK.Service@gmail.com)
- 📱 **APK 下载**：[下载体验 APK](https://www.pgyer.com/HelloFace)

## 许可

The wrapper and example code are provided for technical exchange and evaluation purposes only. See [LICENSE](LICENSE). The native SDK, models, and resources remain subject to the upstream `Hello Face Test Only` terms. Upstream currently states that its models are not intended for children under 12 or financial use.
