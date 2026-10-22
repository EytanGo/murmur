import Foundation

/// Splits a live recording into chunks at natural pauses, so each chunk can be
/// transcribed while you're still talking. Cuts in the middle of a pause, and
/// never drops audio. Only after 12 s without any pause does it force a cut at
/// the quietest moment, which is flagged so the text can be stitched back.
final class Chunker {
    static let rate = 16000
    private static let frame = 320                    // 20 ms analysis frames
    private static let pauseToCut = rate / 2          // 0.5 s of silence ends a chunk
    private static let minChunk = rate * 3 / 2        // don't cut chunks shorter than 1.5 s
    private static let maxChunk = rate * 12           // force a cut after 12 s of nonstop talk

    /// Called with each finished chunk while recording continues.
    /// `forced` means it was cut mid-sentence rather than at a pause.
    var onChunk: (_ samples: ArraySlice<Float>, _ forced: Bool) -> Void = { _, _ in }

    private var samples: [Float] = []
    private var analyzed = 0
    private var chunkStart = 0
    private var silentRun = 0
    private var speechFrames = 0
    private var noiseFloor: Float = 0.003
    private(set) var rms: Float = 0

    func reset() {
        samples.removeAll(keepingCapacity: true)
        analyzed = 0
        chunkStart = 0
        silentRun = 0
        speechFrames = 0
        rms = 0
    }

    func append(_ new: [Float]) {
        samples += new
        while analyzed + Chunker.frame <= samples.count {
            analyze(frameAt: analyzed)
            analyzed += Chunker.frame
            maybeCut()
        }
    }

    /// The rest of the recording after the last cut, or nil if there's nothing left.
    func finish() -> ArraySlice<Float>? {
        let tail = samples[chunkStart...]
        chunkStart = samples.count
        return tail.count >= Chunker.rate / 4 ? tail : nil
    }

    /// Mic level from 0 to 1 for the overlay.
    var level: CGFloat {
        let db = 20 * log10(max(rms, 1e-6))
        let x = max(0, min(1, (db + 50) / 45))
        return CGFloat(pow(x, 1.6))
    }

    private func analyze(frameAt i: Int) {
        var sum: Float = 0
        for s in samples[i..<(i + Chunker.frame)] { sum += s * s }
        rms = (sum / Float(Chunker.frame)).squareRoot()

        // Track background noise so the pause detector adapts to the room and mic.
        if rms < noiseFloor { noiseFloor = max(0.0005, noiseFloor * 0.5 + rms * 0.5) }
        else { noiseFloor += (rms - noiseFloor) * 0.002 }

        if rms > max(0.008, noiseFloor * 3.5) {
            silentRun = 0
            speechFrames += 1
        } else {
            silentRun += Chunker.frame
        }
    }

    private func maybeCut() {
        let length = analyzed - chunkStart
        let spoke = speechFrames >= 5 // at least 100 ms of speech in this chunk

        if spoke && silentRun >= Chunker.pauseToCut && length >= Chunker.minChunk {
            cut(at: analyzed - silentRun / 2, forced: false) // middle of the pause
        } else if length >= Chunker.maxChunk {
            cut(at: quietestPoint(in: max(chunkStart, analyzed - Chunker.rate * 3)..<analyzed), forced: true)
        }
    }

    private func cut(at point: Int, forced: Bool) {
        guard point > chunkStart else { return }
        onChunk(samples[chunkStart..<point], forced)
        chunkStart = point
        speechFrames = 0
    }

    private func quietestPoint(in range: Range<Int>) -> Int {
        var best = range.upperBound, bestEnergy = Float.greatestFiniteMagnitude
        var i = range.lowerBound
        while i + Chunker.frame <= range.upperBound {
            var e: Float = 0
            for s in samples[i..<(i + Chunker.frame)] { e += s * s }
            if e < bestEnergy { bestEnergy = e; best = i + Chunker.frame / 2 }
            i += Chunker.frame
        }
        return best
    }

    /// Writes samples as a 16-bit mono WAV the engine can read.
    static func writeWAV(_ s: ArraySlice<Float>) -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("murmur-\(UUID().uuidString).wav")
        var d = Data()
        func u32(_ v: UInt32) { withUnsafeBytes(of: v.littleEndian) { d.append(contentsOf: $0) } }
        func u16(_ v: UInt16) { withUnsafeBytes(of: v.littleEndian) { d.append(contentsOf: $0) } }
        let bytes = UInt32(s.count * 2)
        d.append(contentsOf: Array("RIFF".utf8)); u32(36 + bytes)
        d.append(contentsOf: Array("WAVEfmt ".utf8)); u32(16); u16(1); u16(1)
        u32(UInt32(rate)); u32(UInt32(rate * 2)); u16(2); u16(16)
        d.append(contentsOf: Array("data".utf8)); u32(bytes)
        d.reserveCapacity(d.count + s.count * 2)
        for x in s {
            let v = Int16(max(-1, min(1, x)) * 32767)
            withUnsafeBytes(of: v.littleEndian) { d.append(contentsOf: $0) }
        }
        return (try? d.write(to: url)) != nil ? url : nil
    }
}
