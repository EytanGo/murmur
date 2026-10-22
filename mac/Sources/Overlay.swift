import AppKit
import SwiftUI

/// The little floating pill at the bottom of the screen.
final class OverlayModel: ObservableObject {
    enum Phase: Equatable { case recording, transcribing, notice(String) }
    @Published var phase: Phase = .recording
    @Published var visible = false
    @Published var level: CGFloat = 0
}

final class Overlay {
    let model = OverlayModel()
    private let panel: NSPanel
    private var hideWork: DispatchWorkItem?
    private static let size = NSSize(width: 360, height: 90)

    init() {
        panel = NSPanel(
            contentRect: NSRect(origin: .zero, size: Overlay.size),
            styleMask: [.borderless, .nonactivatingPanel],
            backing: .buffered, defer: false)
        panel.level = .statusBar
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary, .ignoresCycle]
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = false
        panel.ignoresMouseEvents = true
        panel.contentView = NSHostingView(rootView: OverlayView(m: model))
    }

    func show(_ phase: OverlayModel.Phase) {
        hideWork?.cancel()
        model.phase = phase
        if !panel.isVisible {
            position()
            model.level = 0
            panel.orderFrontRegardless()
            DispatchQueue.main.async { self.model.visible = true }
        } else {
            model.visible = true
        }
    }

    /// Shows a short message in the pill, then fades it away.
    func flash(_ text: String, for seconds: TimeInterval = 2.5) {
        show(.notice(text))
        let work = DispatchWorkItem { [weak self] in self?.hide() }
        hideWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: work)
    }

    func hide() {
        model.visible = false
        let work = DispatchWorkItem { [weak self] in self?.panel.orderOut(nil) }
        hideWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: work)
    }

    /// Smooths raw mic levels so the bars move fluidly.
    func push(level: CGFloat) {
        let current = model.level
        let k: CGFloat = level > current ? 0.55 : 0.25
        model.level = current + (level - current) * k
    }

    private func position() {
        let screen = NSScreen.screens.first { NSMouseInRect(NSEvent.mouseLocation, $0.frame, false) } ?? NSScreen.main
        guard let f = screen?.visibleFrame else { return }
        panel.setFrameOrigin(NSPoint(x: f.midX - Overlay.size.width / 2, y: f.minY + 14))
    }
}

struct OverlayView: View {
    @ObservedObject var m: OverlayModel
    private let bars = 11

    var body: some View {
        Group {
            if case .notice(let text) = m.phase {
                Text(text)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .padding(.horizontal, 16)
                    .frame(height: 36)
            } else {
                TimelineView(.animation) { tl in
                    let t = tl.date.timeIntervalSinceReferenceDate
                    HStack(spacing: 3) {
                        ForEach(0..<bars, id: \.self) { i in
                            Capsule()
                                .fill(Color.white.opacity(m.phase == .recording ? 0.95 : 0.75))
                                .frame(width: 3, height: height(i, t))
                        }
                    }
                    .frame(width: 118, height: 36)
                }
            }
        }
        .background(
            Capsule()
                .fill(Color.black.opacity(0.85))
                .overlay(Capsule().strokeBorder(Color.white.opacity(0.14), lineWidth: 1))
                .shadow(color: .black.opacity(0.35), radius: 12, y: 4)
        )
        .scaleEffect(m.visible ? 1 : 0.5, anchor: .bottom)
        .opacity(m.visible ? 1 : 0)
        .animation(.spring(response: 0.32, dampingFraction: 0.72), value: m.visible)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .padding(.bottom, 12)
    }

    private func height(_ i: Int, _ t: TimeInterval) -> CGFloat {
        let mid = Double(bars - 1) / 2
        let fromCenter = abs(Double(i) - mid) / (mid + 1)
        switch m.phase {
        case .recording:
            // Taller in the middle, each bar wobbling at its own pace, scaled by voice level.
            let envelope = 1 - fromCenter * 0.65
            let wobble = 0.55 + 0.45 * sin(t * 9 + Double(i) * 1.7)
            let idle = 0.12 + 0.08 * sin(t * 3 + Double(i))
            return 4 + 20 * CGFloat(max(idle, Double(m.level) * envelope * wobble))
        case .transcribing, .notice:
            // A soft wave travelling left to right while Whisper works.
            let wave = max(0, sin(t * 6 - Double(i) * 0.75))
            return 4 + 10 * CGFloat(wave * wave)
        }
    }
}
