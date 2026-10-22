import AVFoundation

/// Streams the microphone as 16 kHz mono samples (what Whisper wants) while recording.
final class Recorder {
    /// Called on the main thread with each new batch of samples.
    var onSamples: ([Float]) -> Void = { _ in }

    private var engine: AVAudioEngine?
    private var converter: AVAudioConverter?
    private let outFormat = AVAudioFormat(
        commonFormat: .pcmFormatFloat32, sampleRate: Double(Chunker.rate), channels: 1, interleaved: false)!
    private var startedAt = Date()

    var isRecording: Bool { engine?.isRunning ?? false }
    var duration: TimeInterval { Date().timeIntervalSince(startedAt) }

    func start() -> Bool {
        let engine = AVAudioEngine() // fresh each time so mic changes are picked up
        let input = engine.inputNode
        let inFormat = input.outputFormat(forBus: 0)
        guard inFormat.sampleRate > 0, let conv = AVAudioConverter(from: inFormat, to: outFormat) else { return false }
        converter = conv

        input.installTap(onBus: 0, bufferSize: 1024, format: inFormat) { [weak self] buf, _ in
            self?.convert(buf)
        }
        engine.prepare()
        do { try engine.start() } catch {
            input.removeTap(onBus: 0)
            return false
        }
        self.engine = engine
        startedAt = Date()
        return true
    }

    func stop() {
        guard let engine else { return }
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        self.engine = nil
        converter = nil
    }

    /// Runs on the audio thread: resample to 16 kHz mono and hand off.
    private func convert(_ buf: AVAudioPCMBuffer) {
        guard let converter else { return }
        let ratio = outFormat.sampleRate / buf.format.sampleRate
        let capacity = AVAudioFrameCount(Double(buf.frameLength) * ratio) + 64
        guard let out = AVAudioPCMBuffer(pcmFormat: outFormat, frameCapacity: capacity) else { return }

        var fed = false
        var error: NSError?
        converter.convert(to: out, error: &error) { _, status in
            if fed { status.pointee = .noDataNow; return nil }
            fed = true
            status.pointee = .haveData
            return buf
        }
        let n = Int(out.frameLength)
        guard n > 0, let ch = out.floatChannelData?[0] else { return }
        let samples = Array(UnsafeBufferPointer(start: ch, count: n))
        DispatchQueue.main.async { [weak self] in self?.onSamples(samples) }
    }
}
