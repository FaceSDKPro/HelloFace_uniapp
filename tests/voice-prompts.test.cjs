const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { test } = require('node:test')
const { setImmediate } = require('node:timers/promises')
const ts = require('./helpers/typescript.cjs')

const directory = path.resolve(__dirname, '../uni_modules/HelloFace-Verify/utssdk/app-harmony')

function loadNativeClass(name, imports = {}, globals = {}) {
  const filename = path.join(directory, `${name}.ets`)
  const source = fs.readFileSync(filename, 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS }
  })
  const exports = {}
  vm.runInNewContext(outputText, { exports, require: (id) => imports[id], ...globals }, { filename })
  return exports[name]
}

function fakeSpeech(supported = true) {
  const events = []
  const warnings = []
  const timers = new Map()
  let resolveEngine, rejectEngine, listener, nextID = 0, nextTimer = 0
  let failNextSpeak = false
  const engine = new Promise((resolve, reject) => { resolveEngine = resolve; rejectEngine = reject })
  const Speech = loadNativeClass('HelloFaceVerifySpeech', {
    '@kit.ArkTS': { util: { generateRandomUUID: () => `voice-${++nextID}` } },
    '@kit.PerformanceAnalysisKit': { hilog: { warn: (...args) => warnings.push(args) } },
    '@kit.CoreSpeechKit': { textToSpeech: {
      createEngine({ language, person, online }) {
        assert.deepEqual([language, person, online], ['zh-CN', 13, 1])
        events.push('init')
        return engine
      }
    } }
  }, {
    canIUse: () => supported,
    setTimeout(callback, delay) { timers.set(++nextTimer, { callback, delay }); return nextTimer },
    clearTimeout(id) { timers.delete(id) }
  })
  return {
    speech: new Speech(), Speech, timers, events, warnings, reject: rejectEngine,
    failNextSpeak() { failNextSpeak = true },
    start(id) { listener.onStart(id) },
    complete(id, type) { listener.onComplete(id, { type }) },
    ready() {
      resolveEngine({
        setListener(value) { listener = value },
        isBusy() { return true },
        speak(text, { requestId, extraParams }) {
          if (failNextSpeak) { failNextSpeak = false; throw new Error('Queue unavailable') }
          events.push([text, requestId, extraParams.queueMode])
        },
        stop() { events.push('stop') },
        shutdown() { events.push('shutdown') }
      })
    }
  }
}

test('native speech text follows the camera prompt for known stages and actions', () => {
  const UI = loadNativeClass('HelloFaceVerifyPresentation')
  for (const stage of ['WAITING_FOR_FACE', 'FACE_TOO_SMALL', 'MULTIPLE_FACES', 'FACE_NOT_FRONTAL', 'FACE_FORWARD', 'HOLD_STILL']) {
    assert.equal(UI.speechGuidance(stage, undefined), UI.guidance(stage, undefined))
  }
  for (const stage of ['WAITING_FOR_FACE', 'FACE_NOT_FRONTAL', 'FACE_FORWARD', 'HOLD_STILL', 'FACE_UNSTABLE']) {
    assert.equal(UI.guidance(stage, undefined), '请正对屏幕')
  }
  for (const action of ['blink', 'openMouth', 'shakeHead', 'nodHead']) {
    assert.equal(UI.speechGuidance('PERFORM_ACTION', action), UI.guidance('PERFORM_ACTION', action))
  }
  assert.equal(UI.speechGuidance('PERFORM_ACTION', 'unknown'), undefined)
  assert.equal(UI.speechGuidance('FACE_UNSTABLE', undefined), undefined)
  assert.equal(UI.speechGuidance('UNKNOWN', undefined), undefined)
})

test('Harmony speech keeps the latest prompt, deduplicates repeats and supports consecutive identical actions', async () => {
  const { speech, events, ready } = fakeSpeech()
  speech.update('请正对屏幕', 0)
  speech.update('请眨眨眼', 1)
  ready()
  await setImmediate()
  speech.update('请眨眨眼', 1)
  speech.update(undefined, 0)
  speech.update('', 0)
  speech.update('请眨眨眼', 2)
  speech.close()
  speech.update('请缓慢点头', 3)
  assert.deepEqual(events, ['init', ['请眨眨眼', 'voice1', 1], ['请眨眨眼', 'voice2', 1], 'stop', 'shutdown'])
})

test('frontal and stable stages keep a single spoken prompt', async () => {
  const { speech, events, ready } = fakeSpeech()
  const UI = loadNativeClass('HelloFaceVerifyPresentation')
  ready()
  await setImmediate()
  for (const stage of ['WAITING_FOR_FACE', 'FACE_NOT_FRONTAL', 'FACE_FORWARD', 'HOLD_STILL', 'FACE_UNSTABLE']) {
    speech.update(UI.speechGuidance(stage, undefined), 0)
  }
  assert.deepEqual(events, ['init', ['请正对屏幕', 'voice1', 1]])
})

test('Harmony speech shuts down engines that arrive after the camera closes', async () => {
  const { speech, events, ready } = fakeSpeech()
  speech.update('请正对屏幕', 0)
  speech.finishCurrentPrompt()
  ready()
  await setImmediate()
  assert.deepEqual(events, ['init', 'shutdown'])
})

test('Harmony keeps started audio through completion and releases it when playback ends', async () => {
  const service = fakeSpeech()
  const { speech, events, timers } = service
  service.ready()
  await setImmediate()
  speech.update('请正对屏幕', 0)
  service.start('voice1')
  speech.finishCurrentPrompt()
  assert.deepEqual(events, ['init', ['请正对屏幕', 'voice1', 1]])
  assert.equal([...timers.values()][0].delay, 2000)
  speech.update('请靠近一些', 0)
  service.complete('old', 1)
  service.complete('voice1', 0) // 只完成合成，音频仍在播放。
  assert.equal(events.length, 2)
  service.complete('voice1', 1)
  assert.deepEqual(events.slice(-2), ['stop', 'shutdown'])
  assert.equal(timers.size, 0)
})

test('Harmony discards a queued prompt even when an older prompt has started', async () => {
  const service = fakeSpeech()
  const { speech, events, timers } = service
  service.ready()
  await setImmediate()
  speech.update('请正对屏幕', 0)
  service.start('voice1')
  speech.update('请靠近一些', 0)
  service.start('voice1')
  speech.finishCurrentPrompt()
  service.start('voice2')
  assert.deepEqual(events.slice(-2), ['stop', 'shutdown'])
  assert.equal(timers.size, 0)
})

test('Harmony bounds finishing audio and stops it when another camera opens', async () => {
  for (const interrupt of [false, true]) {
    const service = fakeSpeech()
    const { speech, Speech, events, timers } = service
    service.ready()
    await setImmediate()
    speech.update('请正对屏幕', 0)
    service.start('voice1')
    speech.finishCurrentPrompt()
    const timer = [...timers.values()][0]
    if (interrupt) {
      Speech.stopFinishingPrompt()
      assert.equal(timers.size, 0)
    }
    timer.callback()
    assert.deepEqual(events.slice(2), ['stop', 'shutdown'])
    assert.equal(timers.size, 0)
  }
})

test('a new Harmony speech session releases the previous finishing engine', async () => {
  const service = fakeSpeech()
  service.ready()
  await setImmediate()
  service.speech.update('请正对屏幕', 0)
  service.start('voice1')
  service.speech.finishCurrentPrompt()
  const next = new service.Speech()
  assert.equal(service.timers.size, 0)
  assert.deepEqual(service.events.slice(2), ['stop', 'shutdown', 'init'])
  next.close()
  await setImmediate()
})

test('Harmony retries the same prompt after a queue failure without recreating the engine', async () => {
  const service = fakeSpeech()
  service.ready()
  await setImmediate()
  service.failNextSpeak()
  service.speech.update('请正对屏幕', 0)
  assert.equal(service.warnings.length, 1)
  service.speech.update('请正对屏幕', 0)
  assert.deepEqual(service.events, ['init', 'stop', ['请正对屏幕', 'voice2', 1]])
  service.speech.close()
})

test('Harmony speech tolerates an unavailable offline voice', async () => {
  const unsupported = fakeSpeech(false)
  unsupported.speech.update('请正对屏幕', 0)
  unsupported.speech.close()
  assert.deepEqual(unsupported.events, [])
  const failed = fakeSpeech()
  failed.reject(new Error('No offline voice'))
  await setImmediate()
  failed.speech.update('请正对屏幕', 0)
  assert.deepEqual(failed.events, ['init'])
})

test('Harmony ignores an initialization failure arriving after the session closes', async () => {
  const service = fakeSpeech()
  service.speech.close()
  service.reject(new Error('Late initialization failure'))
  await setImmediate()
  assert.equal(service.warnings.length, 0)
  assert.deepEqual(service.events, ['init'])
})
