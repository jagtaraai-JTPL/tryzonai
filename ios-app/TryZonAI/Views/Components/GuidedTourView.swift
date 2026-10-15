import SwiftUI

public struct GuidedTourView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @State private var tourStep = 0

    private let steps: [(icon: String, title: String, subtitle: String, desc: String)] = [
        ("photo.on.rectangle.angled", "1. Curated Designer Catalog", "Explore Outfits or Upload Any Photo", "Browse trending designer pieces across formal, western, and ethnic categories, or simply upload any garment photo from your favorite fashion store."),
        ("person.crop.rectangle.stack", "2. Smart Silhouette Camera", "Precision Alignment & Body Guidance", "Use our built-in AI Camera with live pose silhouettes to snap the perfect photo, or choose a studio model preset."),
        ("sparkles", "3. Neural Virtual Fitting", "Photorealistic Draping & Lighting", "Our advanced neural fashion engine drapes garments naturally over your body contours with photorealistic cloth texture and real shadows."),
        ("slider.horizontal.2.square", "4. Compare & Shop Verified Stores", "Interactive Slider & Stylist Accessories", "Swipe the side-by-side comparison slider to inspect the fit, discover matching accessories, and shop verified partner stores with 1 tap.")
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
