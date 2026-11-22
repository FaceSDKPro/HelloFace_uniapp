import AVFoundation

@MainActor
final class HelloFaceVerifySpeech: NSObject, AVSpeechSynthesizerDelegate {
    private static var finishingPrompt: HelloFaceVerifySpeech?
    private lazy var voice = AVSpeechSynthesisVoice(language: "zh-CN")
    private lazy var synthesizer: AVSpeechSynthesizer = {
        let synthesizer = AVSpeechSynthesizer()
        synthesizer.usesApplicationAudioSession = false
        synthesizer.delegate = self
        return synthesizer
    }()
    private var active = false
    private var prompt: (text: String, actionStep: Int)?
    private var utterance: AVSpeechUtterance?
    private var started = false

    static func stopFinishingPrompt() { finishingPrompt?.close() }

    func update(_ text: String?, actionStep: Int) {
        guard Self.finishingPrompt !== self, let text,
              prompt?.text != text || prompt?.actionStep != actionStep else { return }
        prompt = (text, actionStep)
        speakLatest()
    }

    func setActive(_ value: Bool) {
        guard active != value else { return }
        active = value
        if value { speakLatest() }
        else { stopUtterance() }
    }

    /// Keep just the current, already playing prompt through normal dismissal.
    func finishCurrentPrompt() {
        guard Self.finishingPrompt !== self else { return }
        guard active, started, synthesizer.isSpeaking, !synthesizer.isPaused else { close(); return }
        Self.stopFinishingPrompt()
        Self.finishingPrompt = self
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in
            guard let self, Self.finishingPrompt === self else { return }
            self.close()
        }
    }

    func close() {
        if Self.finishingPrompt === self { Self.finishingPrompt = nil }
        setActive(false)
        prompt = nil
    }

    private func speakLatest() {
        guard active, let prompt, let voice else { return }
        stopUtterance()
        let next = AVSpeechUtterance(string: prompt.text)
        next.voice = voice
        utterance = next
        synthesizer.speak(next)
    }

    private func stopUtterance() {
        guard utterance != nil else { return }
        utterance = nil
        started = false
        synthesizer.stopSpeaking(at: .immediate)
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        let id = ObjectIdentifier(utterance)
        Task { @MainActor [weak self] in
            guard let self, let utterance = self.utterance, ObjectIdentifier(utterance) == id else { return }
            started = true
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        utteranceEnded(ObjectIdentifier(utterance))
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        utteranceEnded(ObjectIdentifier(utterance))
    }

    nonisolated private func utteranceEnded(_ id: ObjectIdentifier) {
        Task { @MainActor [weak self] in
            guard let self, let utterance = self.utterance, ObjectIdentifier(utterance) == id else { return }
            self.utterance = nil
            started = false
            if Self.finishingPrompt === self { close() }
        }
    }
}
