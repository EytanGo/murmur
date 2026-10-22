import AppKit
// Draws the Murmur icon: a white waveform on a deep purple rounded square.
let out = CommandLine.arguments[1]
let sizes = [16, 32, 64, 128, 256, 512, 1024]
let set = "\(out)/Murmur.iconset"
try? FileManager.default.createDirectory(atPath: set, withIntermediateDirectories: true)
func render(_ px: Int) -> Data {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: px, pixelsHigh: px, bitsPerSample: 8,
        samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    let s = CGFloat(px), inset = s * 0.1
    let r = NSRect(x: inset, y: inset, width: s - 2 * inset, height: s - 2 * inset)
    let path = NSBezierPath(roundedRect: r, xRadius: r.width * 0.225, yRadius: r.width * 0.225)
    NSGradient(starting: NSColor(red: 0.36, green: 0.24, blue: 0.78, alpha: 1),
               ending: NSColor(red: 0.12, green: 0.08, blue: 0.30, alpha: 1))!.draw(in: path, angle: -90)
    let heights: [CGFloat] = [0.18, 0.34, 0.56, 0.40, 0.66, 0.40, 0.56, 0.34, 0.18]
    let bw = r.width * 0.05, gap = r.width * 0.035
    let total = CGFloat(heights.count) * bw + CGFloat(heights.count - 1) * gap
    var x = r.midX - total / 2
    NSColor.white.setFill()
    for h in heights {
        let bh = r.height * h
        NSBezierPath(roundedRect: NSRect(x: x, y: r.midY - bh / 2, width: bw, height: bh), xRadius: bw / 2, yRadius: bw / 2).fill()
        x += bw + gap
    }
    NSGraphicsContext.restoreGraphicsState()
    return rep.representation(using: .png, properties: [:])!
}
for p in sizes where p <= 512 {
    try! render(p).write(to: URL(fileURLWithPath: "\(set)/icon_\(p)x\(p).png"))
    try! render(p * 2).write(to: URL(fileURLWithPath: "\(set)/icon_\(p)x\(p)@2x.png"))
}
