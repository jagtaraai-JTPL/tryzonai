import SwiftUI

public struct TryOnProcessingView: View {
    let sessionId: Int
    @ObservedObject var apiClient: APIClient
    let onCompleted: (TryOnStatusResponse) -> Void
    let onCancel: () -> Void

    @State private var progress: Double = 0.15
    @State private var currentStepIndex: Int = 0
    @State private var errorMessage: String? = nil
    @State private var isPolling = true
    @State private var pulseScale: CGFloat = 1.0

    private let steps = [
        "Uploading Photo & Garment to GPU Pipeline...",
        "Segmenting Body Pose & Garment Features...",
        "Running AI Neural Diffusion Fitting Pipeline...",
        "Polishing Ultra-HD Details & Fabric Texture..."
    ]

    private let tips = [
        "💡 Tip: High resolution, well-lit photos give the most realistic outfit fit!",
        "✨ Pro Feature: Upgrade to Pro for 100% Zero-Ad VIP Turbo GPU Processing!",
        "👗 Inspiration: You can try on any outfit directly from Myntra, Ajio, or Amazon links!"
    ]

    @State private var currentTipIndex = 0

    public init(sessionId: Int, apiClient: APIClient, onCompleted: @escaping (TryOnStatusResponse) -> Void, onCancel: @escaping () -> Void) {
        self.sessionId = sessionId
        self.apiClient = apiClient
        self.onCompleted = onCompleted
        self.onCancel = onCancel
    }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 28) {
                Spacer()

                // Animated Pulse Logo Ring
                ZStack {
                    Circle()
                        .stroke(TryZonTheme.primaryGold.opacity(0.2), lineWidth: 12)
                        .frame(width: 140, height: 140)

                    Circle()
                        .trim(from: 0, to: CGFloat(progress))
                        .stroke(
                            AngularGradient(gradient: Gradient(colors: [TryZonTheme.primaryGold, Color.yellow, TryZonTheme.primaryGold]), center: .center),
                            style: StrokeStyle(lineWidth: 10, lineCap: .round)
                        )
                        .frame(width: 140, height: 140)
                        .rotationEffect(.degrees(-90))
                        .animation(.easeInOut(duration: 0.8), value: progress)

                    VStack(spacing: 4) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 36))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .scaleEffect(pulseScale)

                        Text("\(Int(progress * 100))%")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                    }
                }
                .onAppear {
                    withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                        pulseScale = 1.2
                    }
                }

                // Status Message Step Indicator
                VStack(spacing: 8) {
                    Text("TRYZON AI NEURAL FITTING")
                        .font(.system(size: 12, weight: .black))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(2)

                    Text(steps[currentStepIndex])
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .id(currentStepIndex)
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                }

                // Styling Tip Card
                VStack(alignment: .leading, spacing: 6) {
                    Text("DID YOU KNOW?")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(TryZonTheme.primaryGold.opacity(0.8))

                    Text(tips[currentTipIndex])
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.8))
                        .lineSpacing(3)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(TryZonTheme.surfaceVariant)
                .cornerRadius(14)
                .padding(.horizontal, 24)

                // Error Message if failed
                if let err = errorMessage {
                    VStack(spacing: 8) {
                        Text(err)
                            .font(.caption.bold())
                            .foregroundColor(.red)
                            .multilineTextAlignment(.center)

                        Button("Retry / Back") {
                            onCancel()
                        }
                        .font(.caption.bold())
                        .foregroundColor(TryZonTheme.primaryGold)
                    }
                    .padding(.horizontal, 24)
                }

                Spacer()

                // Cancel Button
                Button(action: {
                    isPolling = false
                    onCancel()
                }) {
                    Text("Cancel Fitting")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white.opacity(0.5))
                        .padding(.vertical, 12)
                }
            }
            .padding(24)
        }
        .task {
            startPollingLoop()
        }
    }

    private func startPollingLoop() {
        Task {
            var stepCounter = 0
            while isPolling {
                do {
                    try await Task.sleep(nanoseconds: 1_500_000_000)
                    stepCounter += 1

                    let status = try await apiClient.pollTaskStatus(sessionId: sessionId)

                    DispatchQueue.main.async {
                        if stepCounter < 4 {
                            self.currentStepIndex = stepCounter % steps.count
                            self.progress = min(0.85, 0.2 + Double(stepCounter) * 0.2)
                        }

                        if status.isCompleted {
                            self.progress = 1.0
                            self.isPolling = false
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                onCompleted(status)
                            }
                        } else if status.isFailed {
                            self.isPolling = false
                            self.errorMessage = "Failed to process AI Try-On. Please try another photo."
                        }
                    }
                } catch {
                    // Continue polling unless explicit error
                    if stepCounter > 40 {
                        DispatchQueue.main.async {
                            self.isPolling = false
                            self.errorMessage = error.localizedDescription
                        }
                    }
                }
            }
        }
    }
}
