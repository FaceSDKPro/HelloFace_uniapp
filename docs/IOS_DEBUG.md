# iOS 运行与调试

规范源码位于 `uni_modules/HelloFace-Verify/utssdk/app-ios`。根 Vue 示例与独立 uvue 示例调用同一套公开 API，修改插件后执行 `npm run demo:prepare` 同步副本。

录入、验证、独立活体及特征管理使用同一套 [公开 API](API.md)。iOS 使用 AVFoundation 管理相机、Keychain 保护基准，并负责亮度恢复、重拍、取消及后台释放。结果卡按比例显示回调中的 [结束帧](API.md#验证与活体的结束帧)。

## 开发检查

```sh
npm run check
npm run ios:check
npm run demo:prepare
```

`ios:check` 要求 macOS 和 Xcode，分四层输出结果：

1. 解析插件所有 Swift 文件。
2. 编译并实际执行 `HelloFaceVerifyCameraState.swift` 的纯状态检查，覆盖圆环、代次隔离、关闭顺序和帧尺寸；不模拟人脸采集或识别成功。
3. 用真实 iOS SDK 类型检查 UIKit 视图、圆环和状态代码，不替换识别 SDK。
4. 用仅供编译的 HiFace 边界声明检查全部 UIKit/AVFoundation/Keychain 接入代码。边界方法全部为 `fatalError`，位于 `tests/ios`，不会复制到插件或链接进 App；这一步不代表真实 HiFace SDK 已编译或运行。

选择兼容当前 SDK 的 Xcode 后，进一步执行：

```sh
xcodebuild -version
xcrun swiftc --version
npm run ios:check -- --full
```

`--full` 使用已安装的真实 HiFace/HiFaceNative framework 类型检查全部源码，失败会返回非零状态。工具链版本要求见 [打包指南](BUILDING.md#ios)。平台类型检查开启严格并发诊断，UTS 回调和通知处理显式路由到主 actor；通过后仍需完成 App 链接、签名与真机验收。

## 构建与断点

1. 使用兼容 SDK 的 Xcode；在 HBuilderX 配好当前 AppID、iOS Bundle ID 和自己的签名。保留已有标识与 License，权限说明来自插件 `Info.plist` 的 `NSCameraUsageDescription`。
2. 通过仓库已有的 [本地或云端构建流程](BUILDING.md#ios) 制作含真实两个 xcframework 的完整 App/自定义基座；仅刷新 Vue 页面不能更新 Swift 原生源码。
3. 用 Xcode 打开按该流程准备的原生宿主工程，连接、信任并授权自己的 iPhone，选择 debug 构建，保留数据更新安装。
4. 在 Vue/uvue 回调处看 `code / msg / passed`；在 `app-ios/index.uts` 看原生方法和参数。在 Swift 中按以下位置设断点：

| 位置 | 观察内容 |
| --- | --- |
| `HelloFaceVerifyOperation.invoke` | activeID、重复调用、临时基准、当前前台 presenter |
| `HelloFaceVerifyCameraController` 的权限回调 / `presentationDidFinish` / `finish` | 权限晚到、快速取消、资源关闭与弹出动画的先后顺序 |
| 同文件 `progress` / `result` / `confirm` | SDK 的真实阶段/结果、确认前不保存、重拍隔离 |
| 同文件 `maximizeBrightness` / `restoreBrightness` | 原屏幕亮度快照及前后台恢复 |
| `HelloFaceVerifyCameraView.updateCaptureProgress / updateActionCountdown` | 录入稳定与动作倒计时独立，重复阶段不重启，离页重置 |
| `HelloFaceVerifyCameraPipeline.configure` / `pause` / `emitError` / `close` | 实际帧尺寸、停止后重拍、会话中断及首次错误后拒绝帧/重启 |
| `ReferenceStore` / `FaceImageImporter` | Keychain 状态码、图片方向及真实 SDK 提取错误 |

Xcode 控制台和设备日志只记录阶段、状态码、时序；不要输出 HF1、原照片、Base64 或签名信息。分别检查相机拒绝、快速取消、重拍、连续调用、前后置、后台、锁屏/来电中断、亮度恢复、真人录入、本人/他人比对及动作活体。没有 iPhone 时这些项目保留待验收，不输出模拟识别成功。

平台接口依据：[相机会话中断](https://developer.apple.com/documentation/avfoundation/avcapturesession/wasInterruptedNotification)、[预览方向](https://developer.apple.com/documentation/avfoundation/setting-up-a-capture-session)、[相机连接镜像](https://developer.apple.com/documentation/avfoundation/avcaptureconnection/isvideomirrored)。

重拍的暂停屏障依据 [AVCaptureSession.stopRunning](https://developer.apple.com/documentation/avfoundation/avcapturesession/stoprunning()) 的同步停止语义：在送帧串行队列停止会话，回主 actor 核对当前代次后才创建新请求。帧限制读取锁定 SDK 的 `HiFaceCameraFrame.maximumLongSidePixels / maximumShortSidePixels`，不改变采集预设、SDK 版本或业务阈值。
