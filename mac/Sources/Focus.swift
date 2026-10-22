import AppKit
import ApplicationServices

/// Makes sure there's a text box to paste into. If you forgot to click one,
/// finds the main text box in the front window and focuses it for you.
enum Focus {
    enum Target {
        case ready    // a text box has focus (it already did, or we focused one)
        case unknown  // this app doesn't expose its UI to Accessibility; paste blindly
        case none     // definitely nowhere to type
    }

    private static let textRoles: Set<String> = ["AXTextField", "AXTextArea", "AXComboBox"]

    /// Browsers that need the VoiceOver-style switch before they expose page content.
    private static let chromiumBrowsers: Set<String> = [
        "com.google.Chrome", "com.google.Chrome.beta", "com.brave.Browser", "com.microsoft.edgemac",
        "company.thebrowser.Browser", "com.vivaldi.Vivaldi", "com.operasoftware.Opera",
    ]

    /// When we asked each app (by process id) to turn on its accessibility tree.
    private static var wokenAt: [pid_t: Date] = [:]
    private static let lock = NSLock()

    /// Chrome and Electron apps only build their accessibility tree when asked, and
    /// it takes a moment. Murmur calls this as soon as you switch apps so the tree is
    /// ready by the time you finish speaking. Returns when the app was first woken.
    @discardableResult
    static func wake(_ front: NSRunningApplication) -> Date {
        lock.lock()
        defer { lock.unlock() }
        if let at = wokenAt[front.processIdentifier] { return at }
        let app = AXUIElementCreateApplication(front.processIdentifier)
        AXUIElementSetMessagingTimeout(app, 0.3)
        AXUIElementSetAttributeValue(app, "AXManualAccessibility" as CFString, kCFBooleanTrue)
        if chromiumBrowsers.contains(front.bundleIdentifier ?? "") {
            AXUIElementSetAttributeValue(app, "AXEnhancedUserInterface" as CFString, kCFBooleanTrue)
        }
        let now = Date()
        wokenAt[front.processIdentifier] = now
        return now
    }

    /// Runs Accessibility queries, so call it off the main thread.
    static func prepare() -> Target {
        guard let front = NSWorkspace.shared.frontmostApplication else { return .unknown }
        let app = AXUIElementCreateApplication(front.processIdentifier)
        AXUIElementSetMessagingTimeout(app, 0.3)
        let woken = wake(front)

        // Electron apps (Claude, Slack, VS Code…) can take a few seconds after waking to
        // report what's focused. Wait for it rather than guess.
        var current = focused(app)
        while current == nil && Date().timeIntervalSince(woken) < 3.5 {
            usleep(150_000)
            current = focused(app)
        }
        // Still can't see inside this app: paste anyway (the text also stays on the clipboard).
        guard let f = current else { return .unknown }
        if isTextInput(f) { return .ready }

        // Focus is somewhere we can see and it isn't a text box: you forgot to click one.
        guard let window = element(app, kAXFocusedWindowAttribute) ?? element(app, kAXMainWindowAttribute) else {
            return .none
        }
        var search = Search()
        search.walk(window, inToolbar: false, depth: 0)
        guard let best = search.best else {
            return search.visited < 10 ? .unknown : .none
        }

        AXUIElementSetAttributeValue(best.element, kAXFocusedAttribute as CFString, kCFBooleanTrue)
        usleep(120_000)
        if let f = focused(app), isTextInput(f) {
            usleep(150_000) // browsers report focus a beat before the caret is really there
            return .ready
        }

        // Some apps ignore programmatic focus; click the box instead.
        click(best.frame)
        usleep(150_000)
        return .ready
    }

    // MARK: - Finding the main text box

    private struct Candidate {
        let element: AXUIElement
        let frame: CGRect
        let score: CGFloat
    }

    private struct Search {
        var visited = 0
        var best: Candidate?
        private let deadline = Date().addingTimeInterval(0.6)

        mutating func walk(_ el: AXUIElement, inToolbar: Bool, depth: Int) {
            guard visited < 4000, depth < 60, Date() < deadline else { return }
            visited += 1
            let role = string(el, kAXRoleAttribute) ?? ""
            // Skip menus and toolbars (address bars, search bars in the title area).
            if role == "AXMenuBar" || role == "AXMenu" { return }
            let toolbar = inToolbar || role == "AXToolbar"

            if !toolbar, textRoles.contains(role), let c = candidate(el, role: role) {
                if c.score > (best?.score ?? 0) { best = c }
            }
            for child in children(el) {
                walk(child, inToolbar: toolbar, depth: depth + 1)
            }
        }

        private func candidate(_ el: AXUIElement, role: String) -> Candidate? {
            let sub = string(el, kAXSubroleAttribute) ?? ""
            if sub == "AXSecureTextField" { return nil }                    // never passwords
            if bool(el, kAXEnabledAttribute) == false { return nil }
            guard isEditable(el), let frame = frameOf(el), frame.width > 20, frame.height > 10 else { return nil }

            var score = frame.width * frame.height
            if role == "AXTextArea" { score *= 2 }                         // message bodies, composers
            let label = [kAXDescriptionAttribute, kAXTitleAttribute, kAXPlaceholderValueAttribute, kAXIdentifierAttribute]
                .compactMap { string(el, $0) }.joined(separator: " ").lowercased()
            if sub == "AXSearchField" || label.contains("search") { score *= 0.2 }
            return Candidate(element: el, frame: frame, score: score)
        }
    }

    // MARK: - Helpers

    static func isTextInput(_ el: AXUIElement) -> Bool {
        let role = string(el, kAXRoleAttribute) ?? ""
        if textRoles.contains(role) { return true }
        // Rich editors on the web (Gmail, Notion, ChatGPT) are contenteditable.
        if element(el, "AXEditableAncestor") != nil { return true }
        return isEditable(el) && hasAttribute(el, kAXSelectedTextRangeAttribute)
    }

    private static func isEditable(_ el: AXUIElement) -> Bool {
        var settable = DarwinBoolean(false)
        AXUIElementIsAttributeSettable(el, kAXValueAttribute as CFString, &settable)
        return settable.boolValue || element(el, "AXEditableAncestor") != nil
    }

    private static func focused(_ app: AXUIElement) -> AXUIElement? {
        element(app, kAXFocusedUIElementAttribute)
    }

    private static func value(_ el: AXUIElement, _ attr: String) -> CFTypeRef? {
        var v: CFTypeRef?
        return AXUIElementCopyAttributeValue(el, attr as CFString, &v) == .success ? v : nil
    }

    private static func element(_ el: AXUIElement, _ attr: String) -> AXUIElement? {
        guard let v = value(el, attr), CFGetTypeID(v) == AXUIElementGetTypeID() else { return nil }
        return (v as! AXUIElement)
    }

    private static func string(_ el: AXUIElement, _ attr: String) -> String? {
        value(el, attr) as? String
    }

    private static func bool(_ el: AXUIElement, _ attr: String) -> Bool? {
        value(el, attr) as? Bool
    }

    private static func children(_ el: AXUIElement) -> [AXUIElement] {
        (value(el, kAXChildrenAttribute) as? [AXUIElement]) ?? []
    }

    private static func hasAttribute(_ el: AXUIElement, _ attr: String) -> Bool {
        value(el, attr) != nil
    }

    private static func frameOf(_ el: AXUIElement) -> CGRect? {
        guard let p = value(el, kAXPositionAttribute), let s = value(el, kAXSizeAttribute),
              CFGetTypeID(p) == AXValueGetTypeID(), CFGetTypeID(s) == AXValueGetTypeID() else { return nil }
        var point = CGPoint.zero, size = CGSize.zero
        AXValueGetValue(p as! AXValue, .cgPoint, &point)
        AXValueGetValue(s as! AXValue, .cgSize, &size)
        return CGRect(origin: point, size: size)
    }

    /// Clicks the middle of a box (screen coordinates, top-left origin), then puts the mouse back.
    private static func click(_ frame: CGRect) {
        let target = CGPoint(x: frame.midX, y: frame.midY)
        let original = CGEvent(source: nil)?.location ?? target
        let src = CGEventSource(stateID: .combinedSessionState)
        for type in [CGEventType.leftMouseDown, .leftMouseUp] {
            CGEvent(mouseEventSource: src, mouseType: type, mouseCursorPosition: target, mouseButton: .left)?
                .post(tap: .cghidEventTap)
            usleep(20_000)
        }
        CGEvent(mouseEventSource: src, mouseType: .mouseMoved, mouseCursorPosition: original, mouseButton: .left)?
            .post(tap: .cghidEventTap)
    }
}
