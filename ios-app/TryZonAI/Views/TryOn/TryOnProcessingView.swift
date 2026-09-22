import SwiftUI

public struct TryOnProcessingView: View {
    let sessionId: Int
    @ObservedObject var apiClient: APIClient
    let userPhotoUrl: String?
    let garmentPhotoUrl: String?
    let userImage: UIImage?
    let garmentImage: UIImage?
    let onCompleted: (TryOnStatusResponse) -> Void
    let onCancel: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var percentage: Int = 0
    @State private var currentStep: Int = 1
    @State private var errorMessage: String? = nil
    @State private var isPolling = true
    @State private var outerRotation: Double = 0
    @State private var innerRotation: Double = 0
    @State private var isBlasting = false
    @State private var scanY: CGFloat = 0.05
    @State private var shimmerX: CGFloat = -0.4

    public init(
        sessionId: Int,
        apiClient: APIClient,
        userPhotoUrl: String? = nil,
        garmentPhotoUrl: String? = nil,
        userImage: UIImage? = nil,
        garmentImage: UIImage? = nil,
        onCompleted: @escaping (TryOnStatusResponse) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.sessionId = sessionId
        self.apiClient = apiClient
        self.userPhotoUrl = userPhotoUrl
        self.garmentPhotoUrl = garmentPhotoUrl
        self.userImage = userImage
        self.garmentImage = garmentImage
        self.onCompleted = onCompleted
        self.onCancel = onCancel
    }

    private struct TimelineStepData: Identifiable {
        let id: Int
        let title: String
        let subtitle: String
    }

    private let timelineSteps = [
        TimelineStepData(id: 1, title: "Analyzing photo fidelity", subtitle: "Checking image quality and pose..."),
        TimelineStepData(id: 2, title: "Extracting garment mesh", subtitle: "Understanding fabric and texture..."),
        TimelineStepData(id: 3, title: "AI virtual draping", subtitle: "Fitting outfit to your body shape..."),
        TimelineStepData(id: 4, title: "Lighting adjustment", subtitle: "Adding realistic shadows and lights..."),
        TimelineStepData(id: 5, title: "Final render generation", subtitle: "Putting the final touches...")
    ]

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            // Soft Radial Aura Behind Central Ring
            RadialGradient(
                gradient: Gradient(colors: [
                    TryZonTheme.primaryGold.opacity(errorMessage != nil ? 0.05 : 0.16),
                    TryZonTheme.primaryGold.opacity(0.03),
                    Color.clear
                ]),
                center: .init(x: 0.5, y: 0.28),
                startRadius: 20,
                endRadius: 280
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // ── 1. TOP CANCEL & NAVIGATION ROW ──
                HStack {
                    Button(action: {
                        isPolling = false
                        onCancel()
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                    }

                    Spacer()

                    Text("AI Virtual Fitting")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)

                    Spacer()

                    Button(action: {
                        isPolling = false
                        onCancel()
                    }) {
                        Text("Cancel")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(TryZonTheme.primaryGold)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 10)
                .padding(.bottom, 6)

                if let err = errorMessage {
                    // ERROR STATE CARD
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 20) {
                            Spacer(minLength: 30)

                            VStack(spacing: 16) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .font(.system(size: 54))
                                    .foregroundColor(.red)

                                Text("Processing Update")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.white)

                                Text(err)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.white.opacity(0.75))
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 16)

                                Button(action: {
                                    errorMessage = nil
                                    percentage = 0
                                    currentStep = 1
                                    isPolling = true
                                    startPollingLoop()
                                }) {
                                    Text("RETRY PROCESSING")
                                        .font(.system(size: 14, weight: .black))
                                        .foregroundColor(.black)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 48)
                                        .background(TryZonTheme.primaryGold)
                                        .cornerRadius(14)
                                }
                                .padding(.horizontal, 12)

                                Button(action: onCancel) {
                                    Text("GO BACK")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                }
                            }
                            .padding(24)
                            .background(TryZonTheme.darkSurface)
                            .cornerRadius(24)
                            .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.red.opacity(0.3), lineWidth: 1.5))
                            .padding(.horizontal, 16)
                        }
                    }
                } else {
                    // PROCESSING CONTENT SCROLLVIEW
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 14) {

                            // ── 2. MAIN HEADER TITLE ──
                            VStack(spacing: 3) {
                                HStack(spacing: 4) {
                                    Text("Processing your")
                                        .font(.system(size: 22, weight: .black, design: .rounded))
                                        .foregroundColor(.white)
                                    Text("Try-On...")
                                        .font(.system(size: 22, weight: .black, design: .rounded))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                }

                                Text("Almost ready! Our AI is creating your perfect look.")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(.white.opacity(0.6))
                            }
                            .padding(.top, 4)

                            // ── 3. TIME CONFIDENCE PILL ──
                            HStack(spacing: 6) {
                                Image(systemName: "clock.fill")
                                    .font(.system(size: 11))
                                    .foregroundColor(TryZonTheme.primaryGold)
                                Text("Usually takes 15–20 seconds • Please wait")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(TryZonTheme.primaryGold.opacity(0.12))
                            .cornerRadius(20)
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(TryZonTheme.primaryGold.opacity(0.35), lineWidth: 1))

                            // ── 4. DUAL PHOTO CARDS & AI CENTRAL RING ──
                            VStack(spacing: 12) {
                                HStack(spacing: 16) {
                                    // Left Card: YOUR PHOTO
                                    PhotoPreviewCardView(
                                        title: "YOUR PHOTO",
                                        imageUrl: userPhotoUrl,
                                        uiImage: userImage,
                                        isScanning: true,
                                        scanY: scanY
                                    )

                                    // Chevrons Connection Indicator
                                    HStack(spacing: -3) {
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(TryZonTheme.primaryGold.opacity(0.5))
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(TryZonTheme.primaryGold)
                                    }

                                    // Right Card: AI TRY-ON
                                    PhotoPreviewCardView(
                                        title: "AI TRY-ON",
                                        imageUrl: garmentPhotoUrl,
                                        uiImage: garmentImage,
                                        isDraping: true,
                                        shimmerX: shimmerX
                                    )
                                }

                                // Central AI Progress Ring showing 0-100% Ticker
                                ZStack {
                                    // Outer rotating ring
                                    Circle()
                                        .stroke(
                                            AngularGradient(
                                                gradient: Gradient(colors: [TryZonTheme.primaryGold, Color.clear, TryZonTheme.primaryGold.opacity(0.6), Color.clear]),
                                                center: .center
                                            ),
                                            lineWidth: 2.5
                                        )
                                        .frame(width: 114, height: 114)
                                        .rotationEffect(.degrees(outerRotation))

                                    // Inner counter-rotating ring
                                    Circle()
                                        .stroke(
                                            AngularGradient(
                                                gradient: Gradient(colors: [Color.clear, Color.yellow, Color.clear, TryZonTheme.primaryGold]),
                                                center: .center
                                            ),
                                            lineWidth: 1.5
                                        )
                                        .frame(width: 98, height: 98)
                                        .rotationEffect(.degrees(innerRotation))

                                    // Core Progress Display
                                    Circle()
                                        .fill(TryZonTheme.primaryGold.opacity(0.12))
                                        .frame(width: 80, height: 80)
                                        .overlay(Circle().stroke(TryZonTheme.primaryGold.opacity(0.75), lineWidth: 1.5))
                                        .overlay(
                                            VStack(spacing: 1) {
                                                Text("\(percentage)%")
                                                    .font(.system(size: 22, weight: .black, design: .rounded))
                                                    .foregroundColor(TryZonTheme.primaryGold)

                                                Text("AI RENDERING")
                                                    .font(.system(size: 7.5, weight: .black))
                                                    .foregroundColor(.white)
                                                    .tracking(1.0)

                                                Image(systemName: "sparkles")
                                                    .font(.system(size: 10))
                                                    .foregroundColor(TryZonTheme.primaryGold)
                                            }
                                        )
                                }
                            }
                            .padding(.vertical, 4)

                            // ── 5. AI FASHION PROCESSING CAPABILITY CARD ──
                            HStack(spacing: 10) {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 18))
                                    .foregroundColor(TryZonTheme.primaryGold)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text("AI Fashion Processing")
                                        .font(.system(size: 12.5, weight: .black))
                                        .foregroundColor(.white)

                                    Text("Realistic fit • Natural lighting • Personalized try-on")
                                        .font(.system(size: 10.5))
                                        .foregroundColor(.white.opacity(0.65))
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(TryZonTheme.darkSurface)
                            .cornerRadius(18)
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold.opacity(0.22), lineWidth: 1))

                            // ── 6. PROCESSING STEPS TIMELINE ──
                            VStack(spacing: 10) {
                                ForEach(timelineSteps) { step in
                                    let isActive = step.id == currentStep
                                    let isCompleted = step.id < currentStep

                                    HStack(spacing: 12) {
                                        ZStack {
                                            Circle()
                                                .fill(isCompleted ? TryZonTheme.primaryGold : (isActive ? TryZonTheme.primaryGold.opacity(0.15) : Color.white.opacity(0.05)))
                                                .frame(width: 32, height: 32)
                                                .overlay(
                                                    Circle()
                                                        .stroke(
                                                            isActive ? TryZonTheme.primaryGold : (isCompleted ? Color.clear : Color.white.opacity(0.15)),
                                                            lineWidth: isActive ? 2 : 1
                                                        )
                                                )

                                            if isCompleted {
                                                Image(systemName: "checkmark")
                                                    .font(.system(size: 13, weight: .bold))
                                                    .foregroundColor(.black)
                                            } else if isActive {
                                                ProgressView()
                                                    .tint(TryZonTheme.primaryGold)
                                                    .scaleEffect(0.7)
                                            } else {
                                                Text("\(step.id)")
                                                    .font(.system(size: 11, weight: .bold))
                                                    .foregroundColor(.white.opacity(0.4))
                                            }
                                        }

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(step.title)
                                                .font(.system(size: 13, weight: (isActive || isCompleted) ? .bold : .medium))
                                                .foregroundColor(isCompleted ? .white : (isActive ? TryZonTheme.primaryGold : .white.opacity(0.4)))

                                            Text(step.subtitle)
                                                .font(.system(size: 10.5))
                                                .foregroundColor(.white.opacity(0.5))
                                        }

                                        Spacer()
                                    }
                                }
                            }
                            .padding(14)
                            .background(TryZonTheme.darkSurface)
                            .cornerRadius(20)
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(TryZonTheme.primaryGold.opacity(0.22), lineWidth: 1))

                            // ── 7. BOTTOM REASSURANCE CARD ──
                            HStack(spacing: 8) {
                                Image(systemName: "lock.shield.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(TryZonTheme.primaryGold)

                                Text("Your photo & pose data is processed securely & deleted after fitting.")
                                    .font(.system(size: 10.5, weight: .medium))
                                    .foregroundColor(.white.opacity(0.7))
                                    .multilineTextAlignment(.leading)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(Color.black.opacity(0.4))
                            .cornerRadius(14)
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.1), lineWidth: 1))
                            .padding(.bottom, 16)
                        }
                        .padding(.horizontal, 16)
                    }
                }
            }
        }
        .onAppear {
            startAnimations()
            startPollingLoop()
        }
    }

    private func startAnimations() {
        withAnimation(.linear(duration: 6.5).repeatForever(autoreverses: false)) {
            outerRotation = 360
        }
        withAnimation(.linear(duration: 4.5).repeatForever(autoreverses: false)) {
            innerRotation = -360
        }
        withAnimation(.linear(duration: 1.8).repeatForever(autoreverses: true)) {
            scanY = 0.95
        }
        withAnimation(.linear(duration: 2.0).repeatForever(autoreverses: false)) {
            shimmerX = 1.4
        }
    }

    private func startPollingLoop() {
        Task {
            var stepCounter = 0
            while isPolling {
                do {
                    try await Task.sleep(nanoseconds: 150_000_000) // 150ms smooth ticker update
                    stepCounter += 1

                    await MainActor.run {
                        if self.percentage < 95 {
                            self.percentage += 1
                            self.currentStep = min(5, (self.percentage / 20) + 1)
                        }
                    }

                    // Poll status every 2 seconds (~13 cycles of 150ms)
                    if stepCounter % 13 == 0 {
                        let status = try await apiClient.pollTaskStatus(sessionId: sessionId)

                        if status.isCompleted {
                            await MainActor.run {
                                self.percentage = 100
                                self.currentStep = 5
                                self.isPolling = false
                                self.isBlasting = true
                            }
                            try? await Task.sleep(nanoseconds: 600_000_000)
                            await MainActor.run {
                                self.onCompleted(status)
                            }
                            break
                        } else if status.isFailed {
                            await MainActor.run {
                                self.isPolling = false
                                self.errorMessage = "Failed to process AI Try-On. Please try another photo."
                            }
                            break
                        }
                    }
                } catch {
                    if stepCounter > 200 { // ~30 sec timeout
                        await MainActor.run {
                            self.isPolling = false
                            self.errorMessage = error.localizedDescription
                        }
                        break
                    }
                }
            }
        }
    }
}

// ── PHOTO PREVIEW CARD HELPER VIEW ──
private struct PhotoPreviewCardView: View {
    let title: String
    let imageUrl: String?
    var uiImage: UIImage? = nil
    var isScanning: Bool = false
    var scanY: CGFloat = 0.05
    var isDraping: Bool = false
    var shimmerX: CGFloat = -0.4

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                if let uiImg = uiImage {
                    Image(uiImage: uiImg)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else if let urlStr = imageUrl, let url = URL(string: urlStr) {
                    AsyncImage(url: url) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            TryZonTheme.darkSurface
                        }
                    }
                } else {
                    ZStack {
                        TryZonTheme.darkSurface
                        Image(systemName: isScanning ? "person.fill" : "tshirt.fill")
                            .font(.system(size: 32))
                            .foregroundColor(TryZonTheme.primaryGold.opacity(0.5))
                    }
                }

                // Scanning Line Effect
                if isScanning {
                    GeometryReader { geo in
                        Rectangle()
                            .fill(
                                LinearGradient(
                                    colors: [.clear, TryZonTheme.primaryGold, .white, TryZonTheme.primaryGold, .clear],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(height: 2.5)
                            .offset(y: geo.size.height * scanY)
                    }
                }

                // Title Badge Pill at Bottom
                VStack {
                    Spacer()
                    HStack(spacing: 3) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 8))
                            .foregroundColor(TryZonTheme.primaryGold)
                        Text(title)
                            .font(.system(size: 7.5, weight: .black))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.black.opacity(0.8))
                    .cornerRadius(10)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(TryZonTheme.primaryGold.opacity(0.7), lineWidth: 0.8))
                    .padding(.bottom, 6)
                }
            }
        }
        .frame(width: 88, height: 124)
        .cornerRadius(18)
        .shadow(color: TryZonTheme.primaryGold.opacity(0.25), radius: 6, y: 3)
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold.opacity(0.7), lineWidth: 1.2))
    }
}
