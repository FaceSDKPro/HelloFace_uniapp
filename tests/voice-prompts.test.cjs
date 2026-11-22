const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { test } = require('node:test')
const { setImmediate } = require('node:timers/promises')
const ts = require('typescript')

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
  let resolveEngine, rejectEngine, nextID = 0
  const engine = new Promise((resolve, reject) => { resolveEngine = resolve; rejectEngine = reject })
  const Speech = loadNativeClass('HelloFaceVerifySpeech', {
    '@kit.ArkTS': { util: { generateRandomUUID: () => `voice-${++nextID}` } },
    '@kit.CoreSpeechKit': { textToSpeech: {
      createEngine({ language, person, online }) {
        assert.deepEqual([language, person, online], ['zh-CN', 13, 1])
        events.push('init')
        return engine
      }
    } }
  }, { canIUse: () => supported })
  return {
    speech: new Speech(), events, reject: rejectEngine,
    ready() {
      resolveEngine({
        setListener() {},
        speak(text, { requestId, extraParams }) { events.push([text, requestId, extraParams.queueMode]) },
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
  speech.close()
  ready()
  await setImmediate()
  assert.deepEqual(events, ['init', 'shutdown'])
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
