import AppKit
import ApplicationServices

/// Types the transcript into the front app by pasting it. If no text box has
/// focus, Focus finds one. If there's truly nowhere to type, the text is left
/// on the clipboard so nothing you said is lost.
enum Paster {
    enum Outcome {
        case pasted
        case copiedOnly
    }

    static func insert(_ text: String, completion: @escaping (Outcome) -> Void) {
        let pb = NSPasteboard.general
        let saved = snapshot(pb)
        pb.clearContents()
        pb.setString(text, forType: .string)
        let ourChange = pb.changeCount

        guard AXIsProcessTrusted() else { return completion(.copiedOnly) }

        DispatchQueue.global(qos: .userInitiated).async {
            let target = Focus.prepare()
            DispatchQueue.main.async {
                switch target {
                case .none:
                    completion(.copiedOnly)
                case .ready, .unknown:
                    pressPaste()
                    // When we couldn't confirm a text box, keep the text on the clipboard as a backup.
                    if case .ready = target {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                            guard pb.changeCount == ourChange else { return }
                            restore(saved, to: pb)
                        }
                    }
                    completion(.pasted)
                }
            }
        }
    }

    /// Presses ⌘V as a full sequence: ⌘ down, V down, V up, ⌘ up.
    /// Releasing ⌘ explicitly matters: otherwise macOS can think it's still held.
    private static func pressPaste() {
        let src = CGEventSource(stateID: .combinedSessionState)
        let steps: [(key: CGKeyCode, down: Bool, flags: CGEventFlags)] = [
            (55, true, .maskCommand),   // ⌘
            (9, true, .maskCommand),    // V
            (9, false, .maskCommand),
            (55, false, []),
        ]
        for s in steps {
            let e = CGEvent(keyboardEventSource: src, virtualKey: s.key, keyDown: s.down)
            e?.flags = s.flags
            e?.post(tap: .cghidEventTap)
        }
    }

    private static func snapshot(_ pb: NSPasteboard) -> [[NSPasteboard.PasteboardType: Data]] {
        (pb.pasteboardItems ?? []).map { item in
            var d: [NSPasteboard.PasteboardType: Data] = [:]
            for t in item.types { if let v = item.data(forType: t) { d[t] = v } }
            return d
        }
    }

    private static func restore(_ items: [[NSPasteboard.PasteboardType: Data]], to pb: NSPasteboard) {
        pb.clearContents()
        guard !items.isEmpty else { return }
        let objs: [NSPasteboardItem] = items.map { dict in
            let item = NSPasteboardItem()
            for (t, v) in dict { item.setData(v, forType: t) }
            return item
        }
        pb.writeObjects(objs)
    }
}
