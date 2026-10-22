import Foundation

/// Runs the faster-whisper worker (engine.py) and talks to it over JSON lines.
final class Engine {
    static let supportDir = FileManager.default.homeDirectoryForCurrentUser
        .appendingPathComponent("Library/Application Support/Murmur")
    static let python = supportDir.appendingPathComponent("venv/bin/python").path

    var onStatus: (String) -> Void = { _ in }
    private(set) var status = "Starting…" { didSet { onStatus(status) } }
    private(set) var isReady = false

    private var process: Process?
    private var input: FileHandle?
    private var buffer = Data()
    private var pending: [Int: (Result<String, Error>) -> Void] = [:]
    private var nextId = 1
    private var model = "small"
    private var quitting = false

    func start(model: String) {
        self.model = model
        guard FileManager.default.isExecutableFile(atPath: Engine.python) else {
            status = "Engine missing: \(Engine.python)"
            return
        }
        guard let script = Bundle.main.path(forResource: "engine", ofType: "py") else {
            status = "engine.py missing from app"
            return
        }

        let p = Process()
        p.executableURL = URL(fileURLWithPath: Engine.python)
        p.arguments = ["-u", script]
        var env = ProcessInfo.processInfo.environment
        env["PYTHONUNBUFFERED"] = "1"
        env["HF_HUB_DISABLE_PROGRESS_BARS"] = "1"
        p.environment = env

        let inPipe = Pipe(), outPipe = Pipe()
        p.standardInput = inPipe
        p.standardOutput = outPipe
        p.standardError = Engine.logHandle()

        outPipe.fileHandleForReading.readabilityHandler = { [weak self] h in
            let data = h.availableData
            guard !data.isEmpty else { return }
            DispatchQueue.main.async { self?.consume(data) }
        }
        p.terminationHandler = { [weak self] _ in
            DispatchQueue.main.async { self?.restartAfterCrash() }
        }

        do {
            try p.run()
        } catch {
            status = "Engine failed to start: \(error.localizedDescription)"
            return
        }
        process = p
        input = inPipe.fileHandleForWriting
        load(model: model)
    }

    func load(model: String) {
        self.model = model
        isReady = false
        send(["cmd": "load", "model": model])
    }

    /// Chunks sharing a `session` are one recording; the engine uses earlier chunks as context.
    func transcribe(_ url: URL, language: String, session: Int,
                    completion: @escaping (Result<String, Error>) -> Void) {
        let id = nextId
        nextId += 1
        pending[id] = completion
        var req: [String: Any] = ["cmd": "transcribe", "id": id, "path": url.path, "session": session]
        if !language.isEmpty { req["language"] = language }
        send(req)

        // Never leave the UI stuck if something goes wrong.
        DispatchQueue.main.asyncAfter(deadline: .now() + 120) { [weak self] in
            if let cb = self?.pending.removeValue(forKey: id) {
                cb(.failure(EngineError("Transcription timed out")))
            }
        }
    }

    func stop() {
        quitting = true
        send(["cmd": "quit"])
        process?.terminate()
    }

    // MARK: - Private

    private func send(_ obj: [String: Any]) {
        guard let input, let data = try? JSONSerialization.data(withJSONObject: obj) else { return }
        input.write(data + Data([0x0A]))
    }

    private func consume(_ data: Data) {
        buffer.append(data)
        while let nl = buffer.firstIndex(of: 0x0A) {
            let line = buffer.subdata(in: buffer.startIndex..<nl)
            buffer.removeSubrange(buffer.startIndex...nl)
            guard let msg = try? JSONSerialization.jsonObject(with: line) as? [String: Any] else { continue }
            handle(msg)
        }
    }

    private func handle(_ msg: [String: Any]) {
        let id = msg["id"] as? Int
        switch msg["event"] as? String {
        case "loading":
            isReady = false
            status = "Loading \(msg["model"] as? String ?? model) model… (first time downloads it)"
        case "ready":
            isReady = true
            status = "Ready · \(msg["model"] as? String ?? model) model"
        case "result":
            if let id, let cb = pending.removeValue(forKey: id) {
                cb(.success(msg["text"] as? String ?? ""))
            }
        case "error":
            let text = msg["message"] as? String ?? "Unknown error"
            if let id, let cb = pending.removeValue(forKey: id) {
                cb(.failure(EngineError(text)))
            } else {
                status = "Engine error: \(text)"
            }
        default:
            break
        }
    }

    private func restartAfterCrash() {
        process = nil
        input = nil
        isReady = false
        for (_, cb) in pending { cb(.failure(EngineError("Engine stopped"))) }
        pending.removeAll()
        guard !quitting else { return }
        status = "Engine stopped, restarting…"
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in
            guard let self else { return }
            self.start(model: self.model)
        }
    }

    private static func logHandle() -> FileHandle? {
        let logs = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library/Logs")
        let url = logs.appendingPathComponent("Murmur.log")
        if !FileManager.default.fileExists(atPath: url.path) {
            FileManager.default.createFile(atPath: url.path, contents: nil)
        }
        let h = try? FileHandle(forWritingTo: url)
        h?.seekToEndOfFile()
        return h
    }
}

struct EngineError: LocalizedError {
    let message: String
    init(_ m: String) { message = m }
    var errorDescription: String? { message }
}
