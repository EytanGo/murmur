import AppKit
import Combine

/// All user settings, persisted in UserDefaults.
final class Settings: ObservableObject {
    static let shared = Settings()
    private let d = UserDefaults.standard

    @Published var keyCode: Int { didSet { d.set(keyCode, forKey: "keyCode") } }
    @Published var mode: String { didSet { d.set(mode, forKey: "mode") } }          // "hold" | "toggle"
    @Published var model: String { didSet { d.set(model, forKey: "model") } }
    @Published var language: String { didSet { d.set(language, forKey: "language") } } // "" = auto
    @Published var sounds: Bool { didSet { d.set(sounds, forKey: "sounds") } }

    /// True while the settings window is capturing a new shortcut.
    @Published var isRecordingShortcut = false

    var isHold: Bool { mode == "hold" }
    var isModifierKey: Bool { Keys.modifierMask[keyCode] != nil }
    var keyName: String { Keys.name(for: keyCode) }

    private init() {
        d.register(defaults: [
            "keyCode": 61, // Right Option
            "mode": "hold",
            "model": "small",
            "language": "",
            "sounds": true,
        ])
        keyCode = d.integer(forKey: "keyCode")
        mode = d.string(forKey: "mode") ?? "hold"
        model = d.string(forKey: "model") ?? "small"
        language = d.string(forKey: "language") ?? ""
        sounds = d.bool(forKey: "sounds")
    }

    static let models: [(id: String, label: String)] = [
        ("tiny", "Tiny · fastest"),
        ("base", "Base · fast"),
        ("small", "Small · balanced"),
        ("medium", "Medium · accurate"),
        ("turbo", "Large v3 Turbo · very accurate"),
        ("large-v3", "Large v3 · most accurate, slowest"),
    ]

    static let languages: [(id: String, label: String)] = [
        ("", "Auto-detect"),
        ("en", "English"),
        ("he", "Hebrew"),
        ("es", "Spanish"),
        ("fr", "French"),
        ("de", "German"),
        ("it", "Italian"),
        ("pt", "Portuguese"),
        ("ru", "Russian"),
        ("ar", "Arabic"),
        ("yi", "Yiddish"),
        ("zh", "Chinese"),
        ("ja", "Japanese"),
    ]
}

enum Keys {
    /// Device-specific modifier bits, so left and right keys are told apart.
    static let modifierMask: [Int: UInt] = [
        54: 0x10,       // Right Command
        55: 0x08,       // Left Command
        56: 0x02,       // Left Shift
        60: 0x04,       // Right Shift
        58: 0x20,       // Left Option
        61: 0x40,       // Right Option
        59: 0x01,       // Left Control
        62: 0x2000,     // Right Control
        63: 0x800000,   // Fn / Globe
    ]

    static let names: [Int: String] = [
        54: "Right ⌘ Command", 55: "Left ⌘ Command", 56: "Left ⇧ Shift", 60: "Right ⇧ Shift",
        58: "Left ⌥ Option", 61: "Right ⌥ Option", 59: "Left ⌃ Control", 62: "Right ⌃ Control",
        63: "Fn / 🌐 Globe",
        122: "F1", 120: "F2", 99: "F3", 118: "F4", 96: "F5", 97: "F6", 98: "F7", 100: "F8",
        101: "F9", 109: "F10", 103: "F11", 111: "F12", 105: "F13", 107: "F14", 113: "F15",
        106: "F16", 64: "F17", 79: "F18", 80: "F19", 90: "F20",
        49: "Space", 36: "Return", 48: "Tab", 51: "Delete", 117: "Forward Delete",
        115: "Home", 119: "End", 116: "Page Up", 121: "Page Down",
        123: "←", 124: "→", 125: "↓", 126: "↑",
    ]

    /// Names for ordinary keys learned while recording a shortcut.
    static var learned: [Int: String] = [:]

    static func name(for code: Int) -> String {
        names[code] ?? learned[code] ?? UserDefaults.standard.string(forKey: "keyName.\(code)") ?? "Key \(code)"
    }

    static func remember(_ code: Int, chars: String?) {
        guard names[code] == nil, let c = chars?.uppercased(), !c.isEmpty else { return }
        learned[code] = c
        UserDefaults.standard.set(c, forKey: "keyName.\(code)")
    }

    static func isPressed(_ code: Int, flags: NSEvent.ModifierFlags) -> Bool {
        guard let mask = modifierMask[code] else { return false }
        return flags.rawValue & mask != 0
    }
}
