import SwiftUI

public struct TryOnProcessingView: View {
    let sessionId: Int
    @ObservedObject var apiClient: APIClient
    let onCompleted: (TryOnStatusResponse) -> Void
    let onCancel: () -> Void

    @State private var progress: Double = 0.15
    @State private var currentStepIndex: Int = 0
    @State private var currentTipIndex: Int = 0
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
        "👗 Inspiration: You can try on any outfit directly from Myntra, Ajio, or Amazon links!",
        "⚡ Speed: ComfyUI GPU cluster processes fits in under 5 seconds!",
        "🔒 Privacy: Your photo & body pose data is processed privately and securely."
    ]

    public init(sessionId: Int, apiClient: APIClient, onCompleted: @escaping (TryOnStatusResponse) -> Void, onCancel: @escaping () -> Void) {
        self.sessionId = sessionId
        self.apiClient = apiClient
        self.onCompleted = onCompleted
        self.onCancel = onCancel
    }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer(minLength: 20)

                // Header Title
                VStack(spacing: 4) {
                    Text("TRYZON AI NEURAL FITTING")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(2.5)

                    Text("Creating Your Virtual Fit ✨")
                        .font(.system(size: 22, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                }

                Spacer(minLength: 10)

                // Animated Dual-Ring Pulse Gauge
                ZStack {
                    // Outer static ring
                    Circle()
                        .stroke(TryZonTheme.surfaceVariant, lineWidth: 14)
                        .frame(width: 160, height: 160)

                    // Inner animated progress arc
                    Circle()
                        .trim(from: 0, to: CGFloat(progress))
                        .stroke(
                            AngularGradient(gradient: Gradient(colors: [TryZonTheme.primaryGold, Color.yellow, TryZonTheme.primaryGold]), center: .center),
                            style: StrokeStyle(lineWidth: 12, lineCap: .round)
                        )
                        .frame(width: 160, height: 160)
                        .rotationEffect(.degrees(-90))
                        .animation(.easeInOut(duration: 0.8), value: progress)

                    VStack(spacing: 6) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 38))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .scaleEffect(pulseScale)

                        Text("\(Int(progress * 100))%")
                            .font(.system(size: 20, weight: .black, design: .rounded))
                            .foregroundColor(.white)
                    }
                }
                .onAppear {
                    withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                        pulseScale = 1.18
                    }
                }

                // Status Step Message Indicator
                VStack(spacing: 8) {
                    Text(steps[currentStepIndex])
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .id(currentStepIndex)
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                        .animation(.easeInOut(duration: 0.5), value: currentStepIndex)

                    Text("Powered by ComfyUI High-Speed GPU Cluster")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.5))
                }

                Spacer(minLength: 10)

                // Rotating Styling Tip Card
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("DID YOU KNOW?")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(1)
                        Spacer()
                    }

                    Text(tips[currentTipIndex])
                        .font(.system(size: 12.5, weight: .medium))
                        .foregroundColor(.white.opacity(0.85))
                        .lineSpacing(3)
                        .id(currentTipIndex)
                        .transition(.opacity)
                        .animation(.easeInOut(duration: 0.6), value: currentTipIndex)
                }
                .padding(16)
                .background(TryZonTheme.surfaceVariant)
                .cornerRadius(18)
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold.opacity(0.2), lineWidth: 1))
                .padding(.horizontal, 20)

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

                Spacer(minLength: 20)

                // Cancel Button
                Button(action: {
                    isPolling = false
                    onCancel()
                }) {
                    Text("Cancel Fitting")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white.opacity(0.5))
                        .padding(.vertical, 12)
                }
            }
            .padding(20)
        }
        .task {
            startPollingLoop()
            startTipTimer()
        }
    }

    private func startPollingLoop() {
        Task {
            var stepCounter = 0
            while isPolling {
                do {
                    try await Task.sleep(nanoseconds: 1_400_000_000)
                    stepCounter += 1

                    let status = try await apiClient.pollTaskStatus(sessionId: sessionId)

                    DispatchQueue.main.async {
                        if stepCounter < 4 {
                            self.currentStepIndex = stepCounter % steps.count
                            self.progress = min(0.88, 0.2 + Double(stepCounter) * 0.2)
                        }

                        if status.isCompleted {
                            self.progress = 1.0
                            self.isPolling = false
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                                onCompleted(status)
                            }
                        } else if status.isFailed {
                            self.isPolling = false
                            self.errorMessage = "Failed to process AI Try-On. Please try another photo."
                        }
                    }
                } catch {
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

    private func startTipTimer() {
        Timer.scheduledTimer(withTimeInterval: 4.0, repeats: true) { _ in
            withAnimation(.easeInOut(duration: 0.6)) {
                currentTipIndex = (currentTipIndex + 1) % tips.count
            }
        }
    }
}
