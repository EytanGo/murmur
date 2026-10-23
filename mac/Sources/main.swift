import AppKit
import AVFoundation
import Combine

final class AppDelegate: NSObject, NSApplicationDelegate, NSMenuDelegate {
    private enum State { case idle, recording, transcribing }

    private let settings = Settings.shared
    private let hotkey = HotkeyMonitor()
    private let recorder = Recorder()
    private let chunker = Chunker()
    private let engine = Engine()
    private let overlay = Overlay()
    private let settingsWindow = SettingsWindow()

    private var statusItem: NSStatusItem!
    private var state = State.idle
    private var pressedAt = Date()
    private var interrupted = false
    private var trusted = AXIsProcessTrusted()
    private var bag = Set<AnyCancellable>()

    // Chunks of the current recording, transcribed while you talk.
    private var session = 0
    private var parts: [Int: String] = [:]
    private var forcedCuts: Set<Int> = []
    private var lastText = ""
    private var sent = 0
    private var received = 0
    private var failure: String?
    private var finishing = false

    func applicationDidFinishLaunching(_ note: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        setIcon(recording: false)
        let menu = NSMenu()
        menu.delegate = self
        menu.autoenablesItems = false
        statusItem.menu = menu

        settingsWindow.engineStatus = { [weak self] in self?.engine.status ?? "" }
        settingsWindow.cloudStatus = { [weak self] in self?.engine.cloudError }
        engine.start(model: settings.model)
        settings.$model.dropFirst().removeDuplicates()
            .sink { [weak self] in self?.engine.load(model: $0) }
            .store(in: &bag)

        recorder.onSamples = { [weak self] samples in
            guard let self, self.state == .recording else { return }
            self.chunker.append(samples)
            self.overlay.push(level: self.chunker.level)
        }
        chunker.onChunk = { [weak self] chunk, forced in self?.send(chunk, forced: forced) }

        hotkey.onPress = { [weak self] in self?.keyDown() }
        hotkey.onRelease = { [weak self] in self?.keyUp() }
        hotkey.onOtherKey = { [weak self] in self?.otherKey() }
        hotkey.onEscape = { [weak self] in self?.cancel() }
        hotkey.start()

        // Get each app ready for auto-focus as soon as you switch to it (and whatever is
        // in front right now), so the text box can be found the moment you finish speaking.
        NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didActivateApplicationNotification, object: nil, queue: nil
        ) { note in
            guard let app = note.userInfo?[NSWorkspace.applicationUserInfoKey] as? NSRunningApplication else { return }
            Self.wake(app)
        }
        if let front = NSWorkspace.shared.frontmostApplication { Self.wake(front) }

        // Keyboard monitoring only starts working once Accessibility is granted.
        Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            guard let self else { return }
            let now = AXIsProcessTrusted()
            if now && !self.trusted { self.hotkey.start() }
            self.trusted = now
        }

        let micStatus = AVCaptureDevice.authorizationStatus(for: .audio)
        if micStatus == .notDetermined { AVCaptureDevice.requestAccess(for: .audio) { _ in } }
        if !trusted || micStatus != .authorized { settingsWindow.show() }
    }

    func applicationWillTerminate(_ note: Notification) {
        recorder.stop()
        engine.stop()
    }

    // MARK: - Shortcut handling

    private func keyDown() {
        pressedAt = Date()
        interrupted = false
        if settings.isHold && state == .idle { startRecording() }
    }

    private func keyUp() {
        if settings.isHold {
            if state == .recording { finishRecording() }
        } else {
            // Toggle mode: a clean, quick tap flips recording on or off.
            guard !interrupted, Date().timeIntervalSince(pressedAt) < 0.6 else { return }
            if state == .idle { startRecording() } else if state == .recording { finishRecording() }
        }
    }

    /// Another key was pressed while ours was held: it was a keyboard shortcut, not dictation.
    private func otherKey() {
        interrupted = true
        if settings.isHold && state == .recording && recorder.duration < 1.0 { cancel() }
    }

    private static func wake(_ app: NSRunningApplication) {
        guard AXIsProcessTrusted() else { return }
        DispatchQueue.global(qos: .utility).async { Focus.wake(app) }
    }

    // MARK: - Recording

    private func startRecording() {
        guard AVCaptureDevice.authorizationStatus(for: .audio) == .authorized else {
            settingsWindow.show()
            return
        }
        session += 1
        parts = [:]
        forcedCuts = []
        sent = 0
        received = 0
        failure = nil
        finishing = false
        chunker.reset()
        if settings.useCloud { engine.warm() }
        if let front = NSWorkspace.shared.frontmostApplication { Self.wake(front) }
        guard recorder.start() else { return }
        state = .recording
        setIcon(recording: true)
        play("Tink")
        overlay.show(.recording)
    }

    private func finishRecording() {
        let tooShort = recorder.duration < 0.35 && sent == 0
        recorder.stop()
        guard !tooShort else { return cancel() }

        state = .transcribing
        setIcon(recording: false)
        play("Pop")
        overlay.show(.transcribing)

        // Most of the speech is already transcribed; only the last bit is left.
        if let tail = chunker.finish() { send(tail, forced: false) }
        finishing = true
        deliverIfDone()
    }

    /// Hands one chunk to the engine; results are collected in order.
    private func send(_ chunk: ArraySlice<Float>, forced: Bool) {
        guard let url = Chunker.writeWAV(chunk) else { return }
        let index = sent, current = session
        sent += 1
        if forced { forcedCuts.insert(index) }
        engine.transcribe(url, language: settings.language, session: current) { [weak self] result in
            try? FileManager.default.removeItem(at: url)
            guard let self, current == self.session else { return } // cancelled meanwhile
            switch result {
            case .success(let text): self.parts[index] = text
            case .failure(let err): self.failure = err.localizedDescription
            }
            self.received += 1
            self.deliverIfDone()
        }
    }

    private func deliverIfDone() {
        guard finishing, received == sent else { return }
        finishing = false
        state = .idle
        overlay.hide()

        let text = stitch()
        if !text.isEmpty {
            lastText = text
            Paster.insert(text) { [weak self] outcome in
                if case .copiedOnly = outcome {
                    self?.overlay.flash(AXIsProcessTrusted()
                        ? "No text box found · copied, press ⌘V"
                        : "Copied · grant Accessibility to auto-type")
                }
            }
        } else if let failure {
            notify(failure)
        }
    }

    /// Joins chunk texts. Where a chunk was cut mid-sentence, Whisper will have
    /// ended it with a period and capitalized the next one, so undo that.
    private func stitch() -> String {
        var text = ""
        var joinMidSentence = false
        for i in 0..<sent {
            guard var part = parts[i], !part.isEmpty else { continue }
            if joinMidSentence, let first = part.first, first.isUppercase,
               !part.hasPrefix("I ") && !part.hasPrefix("I'") {
                part = first.lowercased() + part.dropFirst()
            }
            if forcedCuts.contains(i) && part.hasSuffix(".") && !part.hasSuffix("...") {
                part.removeLast()
            }
            text += text.isEmpty ? part : " " + part
            joinMidSentence = forcedCuts.contains(i)
        }
        return text
    }

    private func cancel() {
        guard state == .recording else { return }
        recorder.stop()
        session += 1
        state = .idle
        setIcon(recording: false)
        overlay.hide()
    }

    // MARK: - Menu bar

    func menuNeedsUpdate(_ menu: NSMenu) {
        menu.removeAllItems()
        let hint = settings.isHold ? "Hold \(settings.keyName) to dictate" : "Tap \(settings.keyName) to dictate"
        for line in [settings.useCloud ? "Cloud · OpenAI (\(settings.cloudModel))" : engine.status, hint] {
            let item = NSMenuItem(title: line, action: nil, keyEquivalent: "")
            item.isEnabled = false
            menu.addItem(item)
        }
        menu.addItem(.separator())
        let copy = menu.addItem(withTitle: "Copy Last Dictation", action: #selector(copyLast), keyEquivalent: "c")
        copy.target = self
        copy.isEnabled = !lastText.isEmpty
        menu.addItem(withTitle: "Settings…", action: #selector(openSettings), keyEquivalent: ",").target = self
        menu.addItem(withTitle: "Quit Murmur", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
    }

    @objc private func openSettings() { settingsWindow.show() }

    @objc private func copyLast() {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(lastText, forType: .string)
    }

    private func setIcon(recording: Bool) {
        let name = recording ? "waveform.circle.fill" : "waveform"
        let img = NSImage(systemSymbolName: name, accessibilityDescription: "Murmur")
        img?.isTemplate = true
        statusItem.button?.image = img
    }

    private func play(_ sound: String) {
        guard settings.sounds else { return }
        NSSound(named: sound)?.play()
    }

    private func notify(_ text: String) {
        let alert = NSAlert()
        alert.messageText = "Murmur"
        alert.informativeText = text
        NSApp.activate(ignoringOtherApps: true)
        alert.runModal()
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
