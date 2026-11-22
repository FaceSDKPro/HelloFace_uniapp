<template>
  <scroll-view class="page" scroll-y :show-scrollbar="false" :scroll-into-view="resultScrollTarget" scroll-with-animation>
    <view class="content">
      <view class="identity">
        <view class="identity-field">
          <text class="label">FaceID</text>
          <input class="input" v-model="faceID" :disabled="busy" maxlength="64" placeholder="录入与验证使用同一标识" />
        </view>
        <button class="website-button" :disabled="busy" aria-label="打开 HelloFace 官网" @click="openWebsite">🌍</button>
      </view>
      <button class="action" :disabled="busy" @click="enroll">相机录入人脸</button>
      <view class="verification-card">
        <text class="section-title">1:1 人脸验证</text>
        <label class="liveness-row">
          <view class="liveness-copy">
            <text class="liveness-title">静默活体</text>
            <text class="liveness-detail">无需指定动作，检测是否为真人</text>
          </view>
          <switch class="liveness-switch" :checked="silentLiveness" :disabled="busy" color="#6865C6" @change="changeSilent" />
        </label>
        <label class="liveness-row">
          <view class="liveness-copy">
            <text class="liveness-title">动作活体</text>
            <text class="liveness-detail">勾选需要完成的动作</text>
          </view>
          <switch class="liveness-switch" :checked="actionLiveness" :disabled="busy" color="#6865C6" @change="changeActions" />
        </label>
        <view v-if="actionLiveness" class="action-selection">
          <text class="liveness-detail">至少选择一个动作，按下方顺序执行</text>
          <checkbox-group class="action-options" @change="changeSelectedActions($event, false)">
            <label v-for="item in actionOptions" :key="item.value" class="action-option">
              <checkbox class="action-checkbox" :value="item.value" :checked="verifyActions.includes(item.value)"
                :disabled="busy || (verifyActions.length == 1 && verifyActions.includes(item.value))" color="#6865C6" />
              <text class="action-name">{{ item.label }}</text>
            </label>
          </checkbox-group>
        </view>
        <view class="mode-summary">
          <text class="mode-title">{{ verificationMode }}</text>
          <text class="mode-detail">{{ verificationHint }}</text>
        </view>
        <button class="action verify-action" :disabled="busy" @click="verify">开始 1:1 验证</button>
      </view>
      <view class="verification-card">
        <text class="section-title">独立活体检测</text>
        <text class="feature-note">不比对身份，无需 FaceID 或基准。静默活体始终开启，可叠加动作。</text>
        <label class="liveness-row">
          <view class="liveness-copy">
            <text class="liveness-title">动作活体</text>
            <text class="liveness-detail">勾选需要完成的动作</text>
          </view>
          <switch class="liveness-switch" :checked="livenessActions" :disabled="busy" color="#6865C6" @change="changeLivenessActions" />
        </label>
        <view v-if="livenessActions" class="action-selection">
          <text class="liveness-detail">至少选择一个动作，按下方顺序执行</text>
          <checkbox-group class="action-options" @change="changeSelectedActions($event, true)">
            <label v-for="item in actionOptions" :key="item.value" class="action-option">
              <checkbox class="action-checkbox" :value="item.value" :checked="detectActions.includes(item.value)"
                :disabled="busy || (detectActions.length == 1 && detectActions.includes(item.value))" color="#6865C6" />
              <text class="action-name">{{ item.label }}</text>
            </label>
          </checkbox-group>
        </view>
        <button class="action verify-action" :disabled="busy" @click="detect">开始活体检测</button>
      </view>
      <button class="action" :disabled="busy" @click="syncDemo('图片')">同步演示人脸图片</button>
      <button class="action" :disabled="busy" @click="syncDemo('特征')">同步演示人脸特征</button>
      <button class="action" :disabled="busy" @click="remove">删除人脸信息</button>
      <view id="demo-result" class="result-card">
        <view id="demo-result-body" class="result-body">
          <view class="result-copy">
            <text v-if="resultStatus.length > 0" class="result-status" :class="resultStatusTone">{{ resultStatus }}</text>
            <text class="result-title">{{ resultTitle.length > 0 ? resultTitle : '调用结果' }}</text>
            <text v-if="resultMode.length > 0" class="result-mode">{{ resultMode }}</text>
            <text v-if="resultText.length > 0" :class="resultTitle.length > 0 ? 'recognition-detail' : 'result-text'">{{ resultText }}</text>
            <text v-if="resultCode > 0 && resultCode != 103" class="result-code">返回码：{{ resultCode }}</text>
            <!-- #ifdef APP -->
            <button v-if="resultCode == 102" class="result-settings" :disabled="busy" @click="openCameraSettings">去设置</button>
            <!-- #endif -->
          </view>
          <image v-for="frame in resultFrames" :key="frame.generation" class="result-frame" :src="frame.src" mode="aspectFit"
            :style="{ width: frame.width + 'px', height: frame.height + 'px' }" aria-label="检测结束时的相机画面"
            @error="onResultFrameError(frame.generation)" />
        </view>
      </view>
      <text class="contact-email">邮箱：FaceAISDK.Service@gmail.com</text>
    </view>
  </scroll-view>
</template>
<script>
// #ifdef APP
import { enrollFace, verifyFace, detectLiveness, syncFaceImage, syncFaceFeature, deleteFace, cancel } from '@/uni_modules/HelloFace-Verify'
import { demoImageBase64, demoFeature, demoFeatureSpaceID } from '@/demo/face-reference.js'
// #endif
export default {
  data() {
    return { faceID: 'demo-user', silentLiveness: true, actionLiveness: false, livenessActions: false, busy: false,
      actionOptions: [{ value: 'blink', label: '眨眼' }, { value: 'openMouth', label: '张嘴' },
        { value: 'shakeHead', label: '摇头' }, { value: 'nodHead', label: '点头' }],
      verifyActions: ['blink', 'nodHead'], detectActions: ['blink', 'nodHead'],
      resultMode: '', resultTitle: '', resultStatus: '', resultCode: -1, resultScrollTarget: '', resultGeneration: 0,
      resultFrames: [], resultText: '身份验证需先录入或同步基准；独立活体可直接开始。' }
  },
  computed: {
    resultStatusTone() {
      if (this.resultCode == 0) return 'result-passed'
      return this.resultCode == 103 ? 'result-cancelled' : 'result-rejected'
    },
    verificationMode() {
      if (this.silentLiveness) return this.actionLiveness ? '静默 + 动作活体' : '仅静默活体'
      return this.actionLiveness ? '仅动作活体' : '仅人脸比对'
    },
    verificationHint() {
      if (!this.silentLiveness && !this.actionLiveness) return '两个活体均已关闭，本次只做 1:1 人脸比对。'
      return this.actionLiveness ? '正对镜头，按提示完成' + this.actionNames(this.verifyActions) + '。' : '正对镜头即可，无需做指定动作。'
    }
  },
  onShow() {
    // #ifdef APP
    if (this.resultCode == 102 && uni.getAppAuthorizeSetting().cameraAuthorized == 'authorized') {
      this.resultCode = 0
      this.resultStatus = '相机权限已开启，请重试'
    }
    // #endif
  },
  // #ifdef APP
  onResize() {
    this.measureResultFrame(this.resultGeneration)
  },
  // #endif
  onUnload() {
    this.resultGeneration++
    this.resultFrames = []
    // #ifdef APP
    // 先停止结果展示，再取消相机，避免迟到回调在离页后弹出 Toast。
    if (!this.busy) return
    uni.hideLoading()
    this.busy = false
    cancel()
    // #endif
  },
  methods: {
    // 将这三个调用绑定到相应按钮。插件负责相机、权限申请和 SDK 初始化。
    enroll() {
      if (!this.begin('正在录入人脸…', '相机录入')) return
      // #ifdef APP
      enrollFace({
        faceID: this.faceID,
        overwrite: true, // 方便重复体验；仍需在原生页面确认保存。
        success: this.showResult,
        fail: this.showError,
        complete: this.finish
      })
      // #endif
    },
    verify() {
      const actions = this.actionLiveness ? this.verifyActions.slice() : []
      if (this.busy || (this.actionLiveness && !this.requireAction(actions))) return
      if (!this.begin('正在验证身份…', '1:1 人脸验证')) return
      this.resultMode = this.verificationMode + (this.actionLiveness ? ' · ' + this.actionNames(actions) : '')
      // #ifdef APP
      verifyFace({
        faceID: this.faceID,
        // 两项独立传入；同时开启时，两种活体都参与本次验证。
        silentLiveness: this.silentLiveness,
        actions: actions,
        success: this.showResult,
        fail: this.showError,
        complete: this.finish
      })
      // #endif
    },
    detect() {
      const actions = this.livenessActions ? this.detectActions.slice() : []
      if (this.busy || (this.livenessActions && !this.requireAction(actions))) return
      if (!this.begin('正在检测活体…', '独立活体检测')) return
      this.resultMode = '独立活体 · ' + (this.livenessActions ? '静默 + 动作活体 · ' + this.actionNames(actions) : '仅静默活体')
      // #ifdef APP
      // SDK 的独立活体始终静默；只传可选动作，不传 faceID 或身份基准。
      detectLiveness({
        actions: actions,
        success: this.showResult,
        fail: this.showError,
        complete: this.finish
      })
      // #endif
    },
    // 同步读取内置演示图片/HF1。已有基准时先确认，再允许覆盖。
    syncDemo(kind) {
      if (!this.begin('正在同步' + kind + '…', '同步演示' + kind)) return
      // #ifdef APP
      this.submitDemoSync(kind, this.faceID.trim(), false)
      // #endif
    },
    // #ifdef APP
    submitDemoSync(kind, faceID, overwrite) {
      const generation = this.resultGeneration
      const loading = '正在同步' + kind
      this.resultText = loading + '…'
      uni.showLoading({ title: loading, mask: true })
      let needsConfirmation = false
      const fail = (error) => {
        if (!this.busy || generation != this.resultGeneration) return
        needsConfirmation = !overwrite && error.code == 106
        if (!needsConfirmation) this.showError(error)
      }
      // 等插件完成本次调用后再提示覆盖。
      const complete = () => {
        if (!this.busy || generation != this.resultGeneration) return
        if (!needsConfirmation) {
          this.busy = false
          return
        }
        uni.hideLoading()
        this.resultText = '等待确认覆盖…'
        this.confirmReferenceChange('替换人脸基准？',
          '「' + faceID + '」已有本机人脸基准。继续将用演示数据替换。', '确认替换',
          () => this.submitDemoSync(kind, faceID, true))
      }
      if (kind == '图片') {
        syncFaceImage({ faceID: faceID, imageBase64: demoImageBase64, overwrite: overwrite,
          success: this.showResult, fail: fail, complete: complete })
      } else {
        syncFaceFeature({ faceID: faceID, feature: demoFeature, featureSpaceID: demoFeatureSpaceID, overwrite: overwrite,
          success: this.showResult, fail: fail, complete: complete })
      }
    },
    // #endif
    remove() {
      if (!this.begin('等待删除确认…', '删除人脸信息')) return
      // #ifdef APP
      const faceID = this.faceID.trim()
      this.confirmReferenceChange('删除人脸信息', '确认删除「' + faceID + '」的本机人脸基准？', '确定', () => {
        deleteFace({ faceID: faceID, success: this.showResult, fail: this.showError, complete: this.finish })
      })
      // #endif
    },

    // 以下方法处理这个 Demo 的界面状态，业务接入可按自己的页面实现。
    // #ifdef APP
    confirmReferenceChange(title, content, confirmText, onConfirm) {
      const generation = this.resultGeneration
      uni.showModal({
        title: title, content: content, confirmText: confirmText,
        success: (modal) => {
          if (!this.busy || generation != this.resultGeneration) return
          if (modal.confirm) onConfirm()
          else this.endPrompt(103, '已取消')
        },
        fail: () => {
          if (this.busy && generation == this.resultGeneration) this.endPrompt(-1, '操作未完成')
        }
      })
    },
    endPrompt(code, message) {
      this.busy = false
      this.presentResult(code, message, '', false)
    },
    // #endif
    // complete 用于恢复按钮，结果已在 success 或 fail 中展示。
    finish() { this.busy = false },
    begin(message, title) {
      if (this.busy) return false
      // #ifndef APP
      this.resultText = '请在 App 真机体验：Android、iOS 或鸿蒙。'
      uni.showToast({ title: '请在 App 真机体验', icon: 'none' })
      return false
      // #endif
      // #ifdef APP
      uni.hideKeyboard()
      this.busy = true
      this.resultMode = ''
      this.resultTitle = title
      this.resultStatus = ''
      this.resultCode = -1
      this.resultScrollTarget = ''
      this.resultGeneration++
      this.resultFrames = []
      this.resultText = message
      return true
      // #endif
    },
    openWebsite() {
      uni.navigateTo({ url: '/pages/website/index' })
    },
    // #ifdef APP
    openCameraSettings() {
      if (this.busy || this.resultCode != 102) return
      uni.openAppAuthorizeSetting({
        fail: () => { uni.showToast({ title: '请在系统设置中开启相机权限', icon: 'none' }) }
      })
    },
    // #endif
    changeSilent(event) {
      if (!this.busy) this.silentLiveness = event.detail.value
    },
    changeActions(event) {
      if (this.busy) return
      if (event.detail.value && this.verifyActions.length == 0) this.verifyActions = ['blink']
      this.actionLiveness = event.detail.value
    },
    changeLivenessActions(event) {
      if (this.busy) return
      if (event.detail.value && this.detectActions.length == 0) this.detectActions = ['blink']
      this.livenessActions = event.detail.value
    },
    changeSelectedActions(event, standalone) {
      if (this.busy) return
      // 固定执行顺序，不依赖不同平台 checkbox-group 返回值的排列。
      const actions = this.actionOptions.filter(item => event.detail.value.includes(item.value)).map(item => item.value)
      if (!this.requireAction(actions)) return
      if (standalone) this.detectActions = actions
      else this.verifyActions = actions
    },
    actionNames(actions) {
      return this.actionOptions.filter(item => actions.includes(item.value)).map(item => item.label).join('、')
    },
    requireAction(actions) {
      if (actions.length > 0) return true
      uni.showToast({ title: '请至少选择一个动作', icon: 'none' })
      return false
    },

    onResultFrameError(generation) {
      if (generation == this.resultGeneration) this.resultFrames = []
    },
    // #ifdef APP
    measureResultFrame(generation) {
      if (this.resultFrames.length == 0) return
      uni.createSelectorQuery().in(this).select('#demo-result-body').boundingClientRect(rect => {
        if (rect != null) this.applyResultFrameWidth(generation, rect.width)
      }).exec()
    },
    applyResultFrameWidth(generation, contentWidth) {
      if (generation != this.resultGeneration || this.resultFrames.length == 0 || contentWidth <= 0) return
      this.fitResultFrame(this.resultFrames[0], Math.max(1, Math.min(160, Math.floor(contentWidth * 0.42))))
    },
    fitResultFrame(frame, maxWidth) {
      const scale = Math.min(maxWidth / frame.pixelWidth, 120 / frame.pixelHeight)
      frame.width = Math.max(1, Math.round(frame.pixelWidth * scale))
      frame.height = Math.max(1, Math.round(frame.pixelHeight * scale))
    },
    // success 也包含正常完成但未通过的识别。业务是否放行应判断 passed === true。
    showResult(result) {
      if (!this.busy) return
      const details = []
      if (this.resultMode.length == 0 && result.faceID.length > 0) details.push('FaceID：' + result.faceID)
      if (result.similarity != null) details.push('相似度：' + result.similarity.toFixed(4))
      if (result.livenessScore != null) details.push('活体分数：' + result.livenessScore.toFixed(4))
      const jpeg = result.frameJpegBase64
      const width = result.frameWidth ?? 0
      const height = result.frameHeight ?? 0
      if (typeof jpeg == 'string' && jpeg.length > 0 && width > 0 && height > 0) {
        const frame = { generation: this.resultGeneration, src: 'data:image/jpeg;base64,' + jpeg,
          pixelWidth: width, pixelHeight: height, width: 0, height: 0 }
        this.fitResultFrame(frame, 96)
        this.resultFrames = [frame]
      }
      this.presentResult(result.code, result.msg, details.join('\n'), true)
    },
    showError(error) {
      if (!this.busy) return
      const details = this.resultMode.length == 0 && error.faceID.length > 0 && error.code != 103
        ? 'FaceID：' + error.faceID : ''
      this.presentResult(error.code, error.msg, details, true)
    },
    presentResult(code, message, details, toast) {
      this.resultCode = code
      this.resultStatus = message
      this.resultText = details
      const generation = this.resultGeneration
      this.$nextTick(() => {
        if (generation != this.resultGeneration) return
        this.measureResultFrame(generation)
        this.resultScrollTarget = 'demo-result'
      })
      if (toast) {
        // 先关闭加载提示，再显示结果提示。
        uni.hideLoading()
        uni.showToast({ title: message, icon: 'none', duration: 2000 })
      }
    }
    // #endif
  }
}
</script>
<style>
.page { height: 100%; flex: 1; background-color: #F4F5F9; }
.content { display: flex; flex-direction: column; width: 100%; max-width: 680px; margin: 0 auto; padding: 15px 16px 28px; }
.identity { display: flex; flex-direction: row; align-items: center; padding-left: 4px; margin-bottom: 14px; }
.identity-field { display: flex; flex-direction: row; align-items: center; flex: 1; min-width: 0; margin-right: 12px; }
.label { flex-shrink: 0; color: #7C8190; font-size: 14px; margin-right: 12px; }
.input { flex: 1; min-width: 0; max-width: 149px; height: 36px; padding: 0 10px; border: 1px solid #E2E5EA; border-radius: 6px; background-color: #FFFFFF; color: #374151; font-size: 14px; }
.website-button { flex-shrink: 0; width: 44px; height: 44px; margin: 0; padding: 0; border-width: 0; border-radius: 8px; background-color: #E6F2EF; font-size: 20px; line-height: 44px; text-align: center; }
.action { width: 100%; height: 54px; line-height: 52px; margin: 0 0 12px; padding: 0 18px; text-align: left; border: 1px solid #285C5E; border-radius: 9px; background-color: #FFFFFF; color: #285C5E; font-size: 16px; font-weight: bold; }
.verification-card { display: flex; flex-direction: column; margin-bottom: 12px; padding: 16px; border: 1px solid #285C5E; border-radius: 11px; background-color: #FFFFFF; }
.section-title { color: #303540; font-size: 16px; font-weight: bold; margin-bottom: 4px; }
.feature-note { color: #7C8190; font-size: 11px; line-height: 17px; margin-top: 4px; margin-bottom: 6px; }
.liveness-row { display: flex; flex-direction: row; align-items: center; justify-content: space-between; min-height: 60px; }
.liveness-switch { transform: scale(0.8); transform-origin: 100% 50%; }
.liveness-copy { display: flex; flex-direction: column; flex: 1; margin-right: 12px; }
.liveness-title { color: #374151; font-size: 15px; font-weight: bold; }
.liveness-detail { color: #7C8190; font-size: 11px; line-height: 16px; margin-top: 3px; }
.action-selection { display: flex; flex-direction: column; margin-bottom: 6px; }
.action-options { display: flex; flex-direction: row; flex-wrap: wrap; margin-top: 4px; }
.action-option { display: flex; flex-direction: row; align-items: center; width: 25%; min-height: 44px; }
.action-checkbox { transform: scale(0.8); transform-origin: 0% 50%; }
.action-name { color: #374151; font-size: 12px; }
.mode-summary { display: flex; flex-direction: column; padding: 12px; margin-top: 8px; margin-bottom: 14px; border-radius: 8px; background-color: #F2F1FB; }
.mode-title { color: #6865C6; font-size: 14px; font-weight: bold; }
.mode-detail { color: #7C8190; font-size: 11px; line-height: 17px; margin-top: 5px; }
.verify-action { height: 45.9px; line-height: 43.9px; margin-bottom: 0; text-align: center; background-color: #E6F2EF; }
.result-mode { color: #6865C6; font-size: 11px; margin-bottom: 10px; }
.result-card { display: flex; flex-direction: column; margin: 6px 0 14px; padding: 14px; border: 1px solid #E2E5EA; border-radius: 16px; background-color: #FFFFFF; }
.result-body { display: flex; flex-direction: row; align-items: center; }
.result-copy { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.result-status { font-size: 12px; font-weight: bold; margin-bottom: 6px; }
.result-passed { color: #087D65; }
.result-rejected { color: #A02D39; }
.result-cancelled { color: #626775; }
.result-title { color: #303540; font-size: 16px; font-weight: bold; margin-bottom: 8px; }
.recognition-detail { color: #626775; font-size: 13px; line-height: 20px; }
.result-frame { flex-shrink: 0; max-width: 42%; margin-left: 12px; }
.result-code { color: #626775; font-size: 12px; line-height: 18px; }
.result-settings { align-self: flex-start; min-height: 44px; line-height: 44px; margin: 8px 0 0; padding: 0 14px; border-radius: 8px; background-color: #E6F2EF; color: #285C5E; font-size: 14px; }
.result-text { min-height: 120px; padding: 12px; border: 1px solid #E9EBEF; border-radius: 8px; background-color: #F6F7F9; color: #6865C6; font-size: 13px; line-height: 21px; }
.contact-email { color: #626775; font-size: 12px; line-height: 18px; text-align: center; }
</style>
