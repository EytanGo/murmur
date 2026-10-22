import AppKit

/// Watches the keyboard globally for the chosen shortcut.
final class HotkeyMonitor {
    var onPress: () -> Void = {}
    var onRelease: () -> Void = {}
    var onOtherKey: () -> Void = {}
    var onEscape: () -> Void = {}

    private(set) var isDown = false
    private var monitors: [Any] = []

    func start() {
        stop()
        let mask: NSEvent.EventTypeMask = [.keyDown, .keyUp, .flagsChanged]
        if let g = NSEvent.addGlobalMonitorForEvents(matching: mask, handler: { [weak self] in self?.handle($0) }) {
            monitors.append(g)
        }
        if let l = NSEvent.addLocalMonitorForEvents(matching: mask, handler: { [weak self] e in
            self?.handle(e)
            return e
        }) {
            monitors.append(l)
        }
    }

    func stop() {
        monitors.forEach(NSEvent.removeMonitor)
        monitors.removeAll()
        isDown = false
    }

    private func handle(_ e: NSEvent) {
        let s = Settings.shared
        if s.isRecordingShortcut { return }
        let code = Int(e.keyCode)

        switch e.type {
        case .flagsChanged:
            guard s.isModifierKey, code == s.keyCode else {
                // Another modifier changed while ours is held (e.g. ⌥ then ⌘).
                if isDown && Keys.modifierMask[code] != nil { onOtherKey() }
                return
            }
            let down = Keys.isPressed(code, flags: e.modifierFlags)
            if down && !isDown { isDown = true; onPress() }
            else if !down && isDown { isDown = false; onRelease() }

        case .keyDown:
            if !s.isModifierKey && code == s.keyCode {
                if !e.isARepeat && !isDown { isDown = true; onPress() }
                return
            }
            if code == 53 { onEscape(); return }
            if isDown { onOtherKey() }

        case .keyUp:
            if !s.isModifierKey && code == s.keyCode && isDown {
                isDown = false
                onRelease()
            }

        default:
            break
        }
    }
}
