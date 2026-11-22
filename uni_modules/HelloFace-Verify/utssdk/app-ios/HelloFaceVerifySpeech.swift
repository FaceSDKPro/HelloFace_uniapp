import AVFoundation

@MainActor
final class HelloFaceVerifySpeech {
    private lazy var voice = AVSpeechSynthesisVoice(language: "zh-CN")
    private lazy var synthesizer: AVSpeechSynthesizer = {
        let synthesizer = AVSpeechSynthesizer()
        synthesizer.usesApplicationAudioSession = false
        return synthesizer
    }()
    private var active = false
    private var prompt: (text: String, actionStep: Int)?

    func update(_ text: String?, actionStep: Int) {
        guard let text, prompt?.text != text || prompt?.actionStep != actionStep else { return }
        prompt = (text, actionStep)
        speakLatest()
    }

    func setActive(_ value: Bool) {
        guard active != value else { return }
        active = value
        if value { speakLatest() }
        else if prompt != nil, voice != nil { synthesizer.stopSpeaking(at: .immediate) }
    }

    func close() {
        setActive(false)
        prompt = nil
    }

    private func speakLatest() {
        guard active, let prompt, let voice else { return }
        synthesizer.stopSpeaking(at: .immediate)
        let utterance = AVSpeechUtterance(string: prompt.text)
        utterance.voice = voice
        synthesizer.speak(utterance)
    }
}
