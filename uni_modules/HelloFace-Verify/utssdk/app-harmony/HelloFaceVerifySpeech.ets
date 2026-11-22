import { util } from '@kit.ArkTS';
import { textToSpeech } from '@kit.CoreSpeechKit';

const REPLACE_PROMPT: Record<string, Object> = { 'queueMode': 1 };
const FINISH_LIMIT_MS: number = 2000;

export class HelloFaceVerifySpeech {
  private static finishingPrompt: HelloFaceVerifySpeech | undefined = undefined;
  private engine: textToSpeech.TextToSpeechEngine | undefined;
  private text: string | undefined;
  private actionStep: number = 0;
  private requestID: string | undefined;
  private started: boolean = false;
  private closed: boolean = false;
  private finishTimer: number = -1;

  static stopFinishingPrompt(): void { HelloFaceVerifySpeech.finishingPrompt?.close(); }

  constructor() {
    if (canIUse('SystemCapability.AI.TextToSpeech')) this.initialize();
    else this.closed = true;
  }

  update(text: string | undefined, actionStep: number): void {
    if (this.closed || HelloFaceVerifySpeech.finishingPrompt === this || text === undefined ||
      (this.text === text && this.actionStep === actionStep)) return;
    this.text = text;
    this.actionStep = actionStep;
    this.speakLatest();
  }

  close(): void {
    if (HelloFaceVerifySpeech.finishingPrompt === this) HelloFaceVerifySpeech.finishingPrompt = undefined;
    if (this.finishTimer >= 0) { clearTimeout(this.finishTimer); this.finishTimer = -1; }
    this.closed = true;
    this.text = undefined;
    this.requestID = undefined;
    this.started = false;
    const engine = this.engine;
    this.engine = undefined;
    try { engine?.stop(); } catch (_) {}
    try { engine?.shutdown(); } catch (_) {}
  }

  finishCurrentPrompt(): void {
    if (HelloFaceVerifySpeech.finishingPrompt === this) return;
    try {
      if (this.started && this.engine?.isBusy()) {
        HelloFaceVerifySpeech.stopFinishingPrompt();
        HelloFaceVerifySpeech.finishingPrompt = this;
        this.finishTimer = setTimeout((): void => { this.close(); }, FINISH_LIMIT_MS);
        return;
      }
    } catch (_) {}
    this.close();
  }

  private async initialize(): Promise<void> {
    try {
      const engine = await textToSpeech.createEngine({ language: 'zh-CN', person: 13, online: 1 });
      // The camera can close while the system voice is loading.
      if (this.closed) { engine.shutdown(); return; }
      this.engine = engine;
      engine.setListener({
        onStart: (id: string): void => { if (id === this.requestID) this.started = true; },
        onComplete: (id: string, response: textToSpeech.CompleteResponse): void => {
          // Synthesis can finish before audio has played out.
          if (response.type === 1) this.utteranceEnded(id);
        },
        onStop: (id: string): void => { this.utteranceEnded(id); },
        onError: (id: string): void => { this.utteranceEnded(id); }
      });
      this.speakLatest();
    } catch (_) { this.close(); }
  }

  private speakLatest(): void {
    if (this.engine === undefined || this.text === undefined) return;
    try {
      const requestId = util.generateRandomUUID(false).replace(/-/g, '');
      this.requestID = requestId;
      this.started = false;
      this.engine.speak(this.text, { requestId: requestId, extraParams: REPLACE_PROMPT });
    } catch (_) { this.close(); }
  }

  private utteranceEnded(id: string): void {
    if (id !== this.requestID) return;
    this.requestID = undefined;
    this.started = false;
    if (HelloFaceVerifySpeech.finishingPrompt === this) this.close();
  }
}
