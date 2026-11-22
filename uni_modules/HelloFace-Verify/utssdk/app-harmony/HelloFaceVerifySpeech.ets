import { util } from '@kit.ArkTS';
import { textToSpeech } from '@kit.CoreSpeechKit';

const REPLACE_PROMPT: Record<string, Object> = { 'queueMode': 1 };

export class HelloFaceVerifySpeech {
  private engine: textToSpeech.TextToSpeechEngine | undefined;
  private text: string | undefined;
  private actionStep: number = 0;
  private closed: boolean = false;

  constructor() {
    if (canIUse('SystemCapability.AI.TextToSpeech')) this.initialize();
    else this.closed = true;
  }

  update(text: string | undefined, actionStep: number): void {
    if (this.closed || text === undefined || (this.text === text && this.actionStep === actionStep)) return;
    this.text = text;
    this.actionStep = actionStep;
    this.speakLatest();
  }

  close(): void {
    this.closed = true;
    this.text = undefined;
    const engine = this.engine;
    this.engine = undefined;
    try { engine?.stop(); } catch (_) {}
    try { engine?.shutdown(); } catch (_) {}
  }

  private async initialize(): Promise<void> {
    try {
      const engine = await textToSpeech.createEngine({ language: 'zh-CN', person: 13, online: 1 });
      // The camera can close while the system voice is loading.
      if (this.closed) { engine.shutdown(); return; }
      this.engine = engine;
      engine.setListener({ onStart: () => {}, onComplete: () => {}, onStop: () => {}, onError: () => {} });
      this.speakLatest();
    } catch (_) { this.close(); }
  }

  private speakLatest(): void {
    if (this.engine === undefined || this.text === undefined) return;
    try {
      this.engine.speak(this.text, {
        requestId: util.generateRandomUUID(false).replace(/-/g, ''), extraParams: REPLACE_PROMPT
      });
    } catch (_) { this.close(); }
  }
}
