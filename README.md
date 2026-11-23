# HelloFace 离线人脸识别与活体检测

为你的 uni-app / uni-app x 应用接入人脸录入、1:1 身份比对与活体检测，一套 UTS API 面向 Android、iOS、鸿蒙，适配 Vue2、Vue3、uvue。

自带原生采集、录入确认和动作引导界面；静默与动作活体按需组合，也可只验真人而不比对身份。支持导入已有的同模型人脸基准，减少重复录入，适合账号本人确认、签到核验与人员验证流程的技术评估。

功能介绍、接入示例和接口列表见 [插件使用说明](uni_modules/HelloFace-Verify/readme.md)。插件 ID：`HelloFace-Verify`；底层 SDK：HelloFace 0.8.15。

示例源码和问题反馈见 [HelloFace_uniapp GitHub 仓库](https://github.com/FaceSDKPro/HelloFace_uniapp)。

## 运行示例

根目录是 uni-app Vue2/Vue3 Demo，`demo-uni-app-x` 是独立 uvue Demo。插件源码统一维护在根目录 `uni_modules/HelloFace-Verify`；`npm run demo:prepare` 会刷新 uvue 的插件和资源副本，并清理副本中过期的文件。

1. 从插件市场导入完整插件和示例，确认 `uni_modules/HelloFace-Verify` 含目标平台 SDK；源码中缺失的 SDK 可按 [开发维护](#开发维护)说明补齐。
2. 用 HBuilderX 导入根目录，配置自己的 AppID、包名和签名，按 [打包指南](docs/BUILDING.md)运行到真机。默认 Vue3；Vue2 将 `manifest.json` 的 `vueVersion` 改为 `"2"`。
3. 从源码运行 uni-app x 示例时，先执行 `npm run dev:prepare`，再用 HBuilderX 导入 `demo-uni-app-x` 并配置自己的 AppID、包名和签名。手动准备方式见 [打包指南](docs/BUILDING.md#通用准备)。

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

Web、小程序不支持此插件。

## Demo 与接入

在 [uni-app 首页](pages/index/index.vue) 或 [uni-app x 首页](demo-uni-app-x/pages/index/index.uvue) 的 `methods` 开头可以找到三个按钮的插件调用。从 `@/uni_modules/HelloFace-Verify` 导入即可使用，插件会按调用初始化 SDK、申请权限并打开原生相机页。

| 想体验 | 从哪里开始 | 必要参数 |
| --- | --- | --- |
| 录入本人 | `enrollFace`，在原生页确认保存 | `faceID`，例如 `demo-user` |
| 1:1 验证 | `verifyFace`，使用已录入的本机基准 | 录入时的同一个 `faceID`。静默活体默认开启，可通过 `actions` 追加动作 |
| 独立活体 | `detectLiveness`，可直接开始 | 无。默认做静默活体，可选传 `actions`，例如 `['blink', 'nodHead']` |

验证是否通过以 `success` 回调里的 `passed === true` 为准。未通过也可能进入 `success`，调用错误走 `fail`；`complete` 用于恢复按钮等收尾。需要显示结束帧时，把非空的 `frameJpegBase64` 加上 `data:image/jpeg;base64,` 前缀传给 `image`，用 `mode="aspectFit"` 显示整帧。请避免记录包含帧或特征的完整回调。

结果区展示 `code/msg`、非空的 `faceID`、`passed/outcome`、相似度、活体分数和结果图片；调用失败时补充 `errCode/sdkCode`。

同步按钮使用内置 [AI 演示基准](demo/README.md)；同一 `faceID` 已有基准时，确认后才会替换。试验 1:1 本人验证时，请先录入本人。页面的开关、确认弹窗和结果卡片是演示交互，业务接入可从 [最小调用](docs/API.md#最小调用) 开始。各接口参数和错误码见 [API 参考](docs/API.md)。

## 开发维护

插件源码统一在 `uni_modules/HelloFace-Verify` 维护。首次克隆执行 `npm ci --ignore-scripts`，准备环境需要 Python 3.9+ 和 curl，UTS 检查需要 HBuilderX。

```sh
npm run dev:prepare  # 补齐锁定 SDK、生成三端 UI 配置、同步 uni-app x 示例
npm run dev:check    # 提交前执行，包含准备、自动测试和三端 UTS 转换
```

只检查现有工作区时执行 `npm run check`。改动原生桥接、SDK、权限或资源后，按目标平台重新构建并在真机验收。更多命令见 [打包与单端检查](docs/BUILDING.md)。

## 体验与联系

- 🌍 **网站说明**：[HelloFace SDK](https://helloface.dev/)
- 📧 **EMAIL**：[FaceAISDK.Service@gmail.com](mailto:FaceAISDK.Service@gmail.com)
- 📱 **APK 下载**：[下载体验 APK](https://www.pgyer.com/HelloFace)
