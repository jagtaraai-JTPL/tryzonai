import SwiftUI

public struct GuidedTourView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @State private var tourStep = 0

    private let steps: [(icon: String, title: String, subtitle: String, desc: String)] = [
        ("photo.on.rectangle.angled", "1. Pick an Outfit or Garment", "Explore AI Catalog or Upload Your Own Photo", "Browse thousands of high-fashion catalog outfits or upload any clothing item screenshot from Myntra, Ajio, Amazon, Zara or Shein."),
        ("person.crop.rectangle.stack", "2. Choose Model or Body Photo", "Select High-Definition Base Photo", "Select from studio model presets or upload your own body photo. TryZon AI seamlessly warps and drapes the outfit onto your body with realistic lighting."),
        ("sparkles", "3. 1-Tap AI Virtual Fitting", "Instant High-Speed Rendering", "Tap 'TRY ON THIS OUTFIT' to process via our VIP ComfyUI GPU cluster. Get ultra-realistic 4K results in seconds with zero effort!"),
        ("gift.fill", "4. Daily Rewards & 2 Free Credits", "Earn Free Credits Every Day", "Enjoy 1 Free Try every day. Spin the Daily Wheel, watch short video ads, or refer friends to earn extra try-on credits forever!")
    ]

    public var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                Spacer()

                // Step Visual Card
                VStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .fill(TryZonTheme.primaryGold.opacity(0.18))
                            .frame(width: 90, height: 90)

                        Image(systemName: steps[tourStep].icon)
                            .font(.system(size: 42, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                    }

                    VStack(spacing: 6) {
                        Text(steps[tourStep].title)
                            .font(.system(size: 22, weight: .black, design: .rounded))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                            .multilineTextAlignment(.center)

                        Text(steps[tourStep].subtitle)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)

                        Text(steps[tourStep].desc)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 16)
                            .padding(.top, 4)
                    }
                }
                .padding(24)
                .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                .cornerRadius(28)
                .padding(.horizontal, 20)

                Spacer()

                // Step Indicators & Controls
                VStack(spacing: 16) {
                    HStack(spacing: 8) {
                        ForEach(0..<steps.count, id: \.self) { idx in
                            Capsule()
                                .fill(idx == tourStep ? TryZonTheme.primaryGold : Color.gray.opacity(0.3))
                                .frame(width: idx == tourStep ? 24 : 8, height: 8)
                        }
                    }

                    Button(action: {
                        if tourStep < steps.count - 1 {
                            withAnimation { tourStep += 1 }
                        } else {
                            dismiss()
                        }
                    }) {
                        Text(tourStep < steps.count - 1 ? "NEXT STEP ➔" : "GET STARTED NOW 🚀")
                            .font(.system(size: 15, weight: .black, design: .rounded))
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(TryZonTheme.primaryGold)
                            .cornerRadius(26)
                            .shadow(color: TryZonTheme.primaryGold.opacity(0.3), radius: 6, y: 3)
                    }
                    .padding(.horizontal, 24)
                    .buttonStyle(BounceButtonStyle())
                }
                .padding(.bottom, 30)
            }
            .navigationTitle("Guided App Tour 🎓")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Skip") { dismiss() }
                        .font(.system(size: 14, weight: .bold))
                }
            }
        }
    }
}
