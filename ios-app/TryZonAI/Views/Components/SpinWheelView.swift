import SwiftUI

public struct SpinWheelView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var rotationDegrees: Double = 0
    @State private var isSpinning: Bool = false
    @State private var wonPrize: PrizeItem? = nil
    @State private var showRewardAlert: Bool = false
    @State private var hasSpunTodayState: Bool = false

    public struct PrizeItem: Identifiable {
        public let id = UUID()
        public let label: String
        public let icon: String
        public let credits: Int
        public let color: Color
    }

    private let prizes: [PrizeItem] = [
        PrizeItem(label: "1 Credit", icon: "⚡", credits: 1, color: Color(red: 0.95, green: 0.6, blue: 0.1)),
        PrizeItem(label: "1 Credit", icon: "🎁", credits: 1, color: Color(red: 0.6, green: 0.3, blue: 0.9)),
        PrizeItem(label: "2 Credits", icon: "🚀", credits: 2, color: Color(red: 0.1, green: 0.6, blue: 0.95)),
        PrizeItem(label: "1 Credit", icon: "🌟", credits: 1, color: Color(red: 0.95, green: 0.75, blue: 0.1)),
        PrizeItem(label: "1 Credit", icon: "⚡", credits: 1, color: Color(red: 0.1, green: 0.8, blue: 0.7)),
        PrizeItem(label: "2 Credits VIP", icon: "👑", credits: 2, color: Color(red: 0.95, green: 0.3, blue: 0.6))
    ]

    private var canSpinToday: Bool {
        let lastSpinTime = UserDefaults.standard.double(forKey: "tryzon_last_spin_time")
        if lastSpinTime == 0 { return true }

        let lastDate = Date(timeIntervalSince1970: lastSpinTime)
        return !Calendar.current.isDateInToday(lastDate)
    }

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public var body: some View {
        ZStack {
            TryZonTheme.backgroundColor(for: colorScheme)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                // Header Bar
                HStack {
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 26))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme).opacity(0.6))
                    }
                }
                .padding(.horizontal, 20)

                // Title Branding
                VStack(spacing: 6) {
                    Text("🎡 DAILY SPIN & WIN")
                        .font(.system(size: 24, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(1)

                    Text(canSpinToday && !hasSpunTodayState ? "Spin once daily to win bonus try-on credits!" : "You've already spun today! Come back tomorrow 🌙")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                }

                Spacer(minLength: 10)

                // Wheel Container with Needle Pointer
                ZStack(alignment: .top) {
                    // Wheel Canvas
                    ZStack {
                        ForEach(0..<prizes.count, id: \.self) { index in
                            WheelSliceShape(startAngle: Angle(degrees: Double(index) * 60.0 - 90.0),
                                            endAngle: Angle(degrees: Double(index + 1) * 60.0 - 90.0))
                                .fill(prizes[index].color)
                                .overlay(
                                    WheelSliceShape(startAngle: Angle(degrees: Double(index) * 60.0 - 90.0),
                                                    endAngle: Angle(degrees: Double(index + 1) * 60.0 - 90.0))
                                        .stroke(Color.black.opacity(0.15), lineWidth: 1.5)
                                )

                            // Slice Text & Emoji Content
                            VStack(spacing: 2) {
                                Text(prizes[index].icon)
                                    .font(.system(size: 20))
                                Text(prizes[index].label)
                                    .font(.system(size: 11, weight: .black, design: .rounded))
                                    .foregroundColor(.white)
                                    .shadow(color: .black.opacity(0.5), radius: 2)
                            }
                            .offset(y: -95)
                            .rotationEffect(Angle(degrees: Double(index) * 60.0 + 30.0))
                        }
                    }
                    .frame(width: 280, height: 280)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(TryZonTheme.primaryGold, lineWidth: 6))
                    .shadow(color: TryZonTheme.primaryGold.opacity(0.4), radius: 16, y: 4)
                    .rotationEffect(Angle(degrees: rotationDegrees))

                    // Center Hub Button / Pin
                    Circle()
                        .fill(LinearGradient(colors: [TryZonTheme.primaryGold, Color.orange], startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: 54, height: 54)
                        .overlay(
                            Text("TRY")
                                .font(.system(size: 12, weight: .black, design: .rounded))
                                .foregroundColor(.black)
                        )
                        .shadow(color: Color.black.opacity(0.3), radius: 4)
                        .offset(y: 113)

                    // Top Pointer Needle (▼)
                    NeedlePointerShape()
                        .fill(Color.red)
                        .frame(width: 24, height: 30)
                        .shadow(color: Color.black.opacity(0.3), radius: 2, y: 2)
                        .offset(y: -12)
                }
                .padding(.vertical, 10)

                Spacer(minLength: 10)

                // Spin Action Button
                Button(action: spinWheel) {
                    HStack(spacing: 8) {
                        if isSpinning {
                            ProgressView()
                                .tint(.black)
                            Text("SPINNING...")
                                .font(.system(size: 16, weight: .black, design: .rounded))
                                .foregroundColor(.black)
                        } else if !canSpinToday || hasSpunTodayState {
                            Text("ALREADY SPUN TODAY 🌙")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundColor(.white.opacity(0.6))
                        } else {
                            Text("SPIN NOW! 🎰")
                                .font(.system(size: 16, weight: .black, design: .rounded))
                                .foregroundColor(.black)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background((!canSpinToday || hasSpunTodayState) ? Color.gray.opacity(0.4) : (isSpinning ? Color.gray : TryZonTheme.primaryGold))
                    .clipShape(Capsule())
                    .shadow(color: TryZonTheme.primaryGold.opacity(canSpinToday && !hasSpunTodayState ? 0.4 : 0), radius: 10, y: 4)
                    .padding(.horizontal, 30)
                }
                .disabled(isSpinning || !canSpinToday || hasSpunTodayState)
                .buttonStyle(BounceButtonStyle())

                Spacer(minLength: 20)
            }
            .padding(.top, 16)

            // Reward Win Popup Modal Overlay
            if showRewardAlert, let prize = wonPrize {
                Color.black.opacity(0.65)
                    .ignoresSafeArea()
                    .transition(.opacity)

                VStack(spacing: 16) {
                    Text(prize.icon)
                        .font(.system(size: 64))
                        .padding(.top, 10)

                    Text("CONGRATULATIONS! 🎉")
                        .font(.system(size: 18, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)

                    Text("You won \(prize.label) (\(prize.credits) Try-On Credit\(prize.credits > 1 ? "s" : ""))!")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)

                    Button(action: {
                        showRewardAlert = false
                        dismiss()
                    }) {
                        Text("CLAIM CREDIT 🚀")
                            .font(.system(size: 14, weight: .black, design: .rounded))
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(TryZonTheme.primaryGold)
                            .clipShape(Capsule())
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 16)
                }
                .frame(maxWidth: 300)
                .background(TryZonTheme.surfaceColor(for: colorScheme))
                .cornerRadius(24)
                .overlay(RoundedRectangle(cornerRadius: 24).stroke(TryZonTheme.primaryGold, lineWidth: 2))
                .shadow(color: TryZonTheme.primaryGold.opacity(0.5), radius: 20)
                .transition(.scale.combined(with: .opacity))
            }
        }
    }

    private func spinWheel() {
        guard canSpinToday, !hasSpunTodayState, !isSpinning else { return }
        isSpinning = true
        wonPrize = nil
        showRewardAlert = false

        // Record spin timestamp immediately so user can't spin twice
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "tryzon_last_spin_time")
        hasSpunTodayState = true

        let impactMed = UIImpactFeedbackGenerator(style: .medium)
        impactMed.impactOccurred()

        // Choose random winning index (e.g. index 0, 1, 3, 4 = 1 credit, index 2, 5 = 2 credits)
        let winningIndex = Int.random(in: 0..<prizes.count)
        let winningPrize = prizes[winningIndex]

        // 360 * 5 full spins + slice offset
        let sliceAngle = 360.0 / Double(prizes.count)
        let targetAngle = 360.0 * 6.0 + (360.0 - Double(winningIndex) * sliceAngle - (sliceAngle / 2.0))

        withAnimation(.easeOut(duration: 4.0)) {
            rotationDegrees += targetAngle
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 4.1) {
            isSpinning = false
            wonPrize = winningPrize
            
            Task {
                try? await apiClient.claimRewardCredit(amount: winningPrize.credits)
            }
            
            let impactHeavy = UIImpactFeedbackGenerator(style: .heavy)
            impactHeavy.impactOccurred()

            withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                showRewardAlert = true
            }
        }
    }
}

// Custom Wheel Slice Shape
struct WheelSliceShape: Shape {
    let startAngle: Angle
    let endAngle: Angle

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2.0
        path.move(to: center)
        path.addArc(center: center, radius: radius, startAngle: startAngle, endAngle: endAngle, clockwise: false)
        path.closeSubpath()
        return path
    }
}

// Custom Pointer Needle Shape
struct NeedlePointerShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}
