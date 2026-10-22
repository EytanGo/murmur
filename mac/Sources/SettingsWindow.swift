import AppKit
import AVFoundation
import Combine
import ServiceManagement
import SwiftUI

final class SettingsWindow {
    private var window: NSWindow?
    var engineStatus: () -> String = { "" }

    func show() {
        if window == nil {
            let w = NSWindow(
                contentRect: NSRect(x: 0, y: 0, width: 440, height: 520),
                styleMask: [.titled, .closable], backing: .buffered, defer: false)
            w.title = "Murmur"
            w.isReleasedWhenClosed = false
            w.contentView = NSHostingView(rootView: SettingsView(engineStatus: engineStatus))
            w.center()
            window = w
        }
        NSApp.activate(ignoringOtherApps: true)
        window?.makeKeyAndOrderFront(nil)
    }
}

struct SettingsView: View {
    @ObservedObject var s = Settings.shared
    let engineStatus: () -> String

    @State private var micOK = false
    @State private var axOK = false
    @State private var status = ""
    @State private var launchAtLogin = SMAppService.mainApp.status == .enabled
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        Form {
            Section {
                LabeledContent("Shortcut") { ShortcutRecorder() }
                Picker("Mode", selection: $s.mode) {
                    Text("Hold to talk").tag("hold")
                    Text("Tap to start / stop").tag("toggle")
                }
                .pickerStyle(.segmented)
            } footer: {
                Text(s.isHold
                     ? "Hold \(s.keyName) while you speak, let go to type it. Esc cancels."
                     : "Tap \(s.keyName) to start, tap again to type it. Esc cancels.")
                    .font(.caption).foregroundStyle(.secondary)
            }

            Section {
                Picker("Model", selection: $s.model) {
                    ForEach(Settings.models, id: \.id) { Text($0.label).tag($0.id) }
                }
                Picker("Language", selection: $s.language) {
                    ForEach(Settings.languages, id: \.id) { Text($0.label).tag($0.id) }
                }
            } footer: {
                Text(status).font(.caption).foregroundStyle(.secondary)
            }

            Section {
                Toggle("Sound effects", isOn: $s.sounds)
                Toggle("Launch at login", isOn: $launchAtLogin)
                    .onChange(of: launchAtLogin) { _, on in
                        do { on ? try SMAppService.mainApp.register() : try SMAppService.mainApp.unregister() }
                        catch { launchAtLogin = SMAppService.mainApp.status == .enabled }
                    }
            }

            Section("Permissions") {
                permissionRow("Microphone", ok: micOK) {
                    if AVCaptureDevice.authorizationStatus(for: .audio) == .notDetermined {
                        AVCaptureDevice.requestAccess(for: .audio) { _ in }
                    } else {
                        open("Privacy_Microphone")
                    }
                }
                permissionRow("Accessibility (to type the text)", ok: axOK) {
                    let opts = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
                    if !AXIsProcessTrustedWithOptions(opts) { open("Privacy_Accessibility") }
                }
            }
        }
        .formStyle(.grouped)
        .frame(width: 440)
        .fixedSize(horizontal: false, vertical: true)
        .onAppear(perform: refresh)
        .onReceive(tick) { _ in refresh() }
    }

    private func permissionRow(_ title: String, ok: Bool, grant: @escaping () -> Void) -> some View {
        LabeledContent(title) {
            if ok {
                Label("Granted", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
            } else {
                Button("Grant…", action: grant)
            }
        }
    }

    private func refresh() {
        micOK = AVCaptureDevice.authorizationStatus(for: .audio) == .authorized
        axOK = AXIsProcessTrusted()
        status = engineStatus()
    }

    private func open(_ pane: String) {
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?\(pane)") {
            NSWorkspace.shared.open(url)
        }
    }
}

/// Click, then press any key (a lone modifier like Right ⌥ works too).
struct ShortcutRecorder: View {
    @ObservedObject var s = Settings.shared
    @State private var monitor: Any?

    var body: some View {
        Button(s.isRecordingShortcut ? "Press a key…" : s.keyName) {
            s.isRecordingShortcut ? finish() : begin()
        }
        .frame(minWidth: 150)
        .onDisappear(perform: finish)
    }

    private func begin() {
        s.isRecordingShortcut = true
        monitor = NSEvent.addLocalMonitorForEvents(matching: [.keyDown, .flagsChanged]) { e in
            let code = Int(e.keyCode)
            if e.type == .flagsChanged {
                // A modifier going down on its own.
                if Keys.isPressed(code, flags: e.modifierFlags) { s.keyCode = code; finish() }
                return nil
            }
            if code != 53 { // Esc just cancels
                Keys.remember(code, chars: e.charactersIgnoringModifiers)
                s.keyCode = code
            }
            finish()
            return nil
        }
    }

    private func finish() {
        if let m = monitor { NSEvent.removeMonitor(m) }
        monitor = nil
        // Defer so the key that ended recording isn't treated as the shortcut being pressed.
        DispatchQueue.main.async { s.isRecordingShortcut = false }
    }
}
