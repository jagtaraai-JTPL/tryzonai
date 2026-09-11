import SwiftUI

public struct TryZonTheme {
    public static let primaryGold = Color(red: 201/255, green: 168/255, blue: 76/255) // #C9A84C
    public static let goldLight = Color(red: 235/255, green: 205/255, blue: 120/255)
    public static let darkBackground = Color(red: 18/255, green: 18/255, blue: 20/255) // #121214
    public static let darkSurface = Color(red: 26/255, green: 26/255, blue: 30/255) // #1A1A1E
    public static let surfaceVariant = Color(red: 36/255, green: 36/255, blue: 42/255) // #24242A
    public static let cardBorder = Color.white.opacity(0.12)
}

public struct ShimmeringGoldButton: View {
    let title: String
    let subtitle: String?
    let iconName: String?
    let action: () -> Void
    var isEnabled: Bool = true

    @State private var shimmerOffset: CGFloat = -1.0

    public init(title: String, subtitle: String? = nil, iconName: String? = "sparkles", isEnabled: Bool = true, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.iconName = iconName
        self.isEnabled = isEnabled
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(isEnabled ? TryZonTheme.primaryGold : Color.gray.opacity(0.3))

                if isEnabled {
                    GeometryReader { geo in
                        Rectangle()
                            .fill(
                                LinearGradient(
                                    gradient: Gradient(colors: [Color.clear, Color.white.opacity(0.4), Color.clear]),
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .rotationEffect(.degrees(25))
                            .offset(x: shimmerOffset * geo.size.width * 1.5)
                    }
                    .mask(RoundedRectangle(cornerRadius: 16))
                }

                HStack(spacing: 8) {
                    if let icon = iconName {
                        Image(systemName: icon)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(isEnabled ? .black : .white.opacity(0.6))
                    }

                    VStack(spacing: 2) {
                        Text(title)
                            .font(.system(size: 14, weight: .black, design: .rounded))
                            .foregroundColor(isEnabled ? .black : .white.opacity(0.6))

                        if let sub = subtitle {
                            Text(sub)
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(isEnabled ? .black.opacity(0.8) : .white.opacity(0.4))
                        }
                    }
                }
                .padding(.vertical, 14)
                .padding(.horizontal, 16)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
        }
        .disabled(!isEnabled)
        .onAppear {
            withAnimation(Animation.linear(duration: 2.5).repeatForever(autoreverses: false)) {
                shimmerOffset = 1.0
            }
        }
    }
}
