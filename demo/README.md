# 外部基准示例

- `../static/demo-face.png` 是 200×200 的虚构 AI 成年人脸，仅用于演示基准同步。验证本人时请先用相机录入本人。
- `face-reference.json` 保存由真实 SDK 提取的 HF1、特征空间、图片哈希及提取来源。`npm run demo:prepare` 生成相同的 JS/UTS 常量并同步到 uvue 示例。
- 同步使用输入框中的 `faceID`，默认 `demo-user`；已有基准时，确认后才覆盖。同步本身不表示身份或活体检测通过。

更新图片时，先运行 [真实 SDK 图片检查](../tests/android/README.md) 重新提取特征，更新 JSON 中的特征、图片哈希与提取来源，再执行 `npm run demo:prepare`。跨版本复用特征要求模型及清单哈希完全一致，保留原始提取版本和提交。
