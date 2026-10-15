import SwiftUI
import AVFoundation
import PhotosUI

// MARK: - CameraCaptureView — High-Fashion AI Studio Camera with Pose Alignment
public struct CameraCaptureView: View {
    let initialMode: CameraTargetMode
    let onPhotoConfirmed: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    @StateObject private var cameraManager = CameraManager()
    @State private var targetMode: CameraTargetMode
    @State private var timerDelaySeconds: Int = 0 // 0, 3, 5
    @State private var countdownRemaining: Int? = nil
    @State private var showTipsSheet: Bool = false
    @State private var showPhotoReview: Bool = false
    @State private var capturedReviewImage: UIImage? = nil
    @State private var showGalleryPicker: Bool = false
    @State private var selectedGalleryItem: PhotosPickerItem? = nil

    public enum CameraTargetMode: String {
        case person = "Person Photo 👤"
        case garment = "Garment Photo 👗"
    }

    public init(initialMode: CameraTargetMode = .person, onPhotoConfirmed: @escaping (UIImage) -> Void) {
        self.initialMode = initialMode
        self._targetMode = State(initialValue: initialMode)
        self.onPhotoConfirmed = onPhotoConfirmed
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if cameraManager.hasPermission {
                // Live AVFoundation Camera Preview
                CameraPreviewView(session: cameraManager.session)
                    .ignoresSafeArea()
                    .onTapGesture { location in
                        cameraManager.focus(at: location)
                    }

                // Pose & Silhouette Alignment Guides
                silhouetteOverlay

                // Top Controls & Mode Switcher
                VStack(spacing: 0) {
                    topBarControls
                        .padding(.horizontal, 16)
                        .padding(.top, 10)

                    modeSelectorPill
                        .padding(.top, 10)

                    Spacer()

                    // Bottom Floating Shutter Controls
                    bottomControls
                        .padding(.horizontal, 24)
                        .padding(.bottom, 28)
                }

                // Self-Timer Countdown Overlay Screen
                if let count = countdownRemaining {
                    ZStack {
                        Color.black.opacity(0.65).ignoresSafeArea()
                        VStack(spacing: 12) {
                            Text("\(count)")
                                .font(.system(size: 110, weight: .black, design: .rounded))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .shadow(color: TryZonTheme.primaryGold.opacity(0.6), radius: 20)
                            Text("HOLD STILL 📸")
                                .font(.system(size: 16, weight: .black))
                                .foregroundColor(.white)
                                .tracking(2)
                        }
                    }
                    .transition(.opacity)
                }
            } else {
                // Permission Denied / Unavailable View
                permissionDeniedView
            }
        }
        .onAppear {
            cameraManager.checkPermissionAndStart()
        }
        .onDisappear {
            cameraManager.stopSession()
        }
        .onChange(of: cameraManager.capturedImage) { newImg in
            if let img = newImg {
                capturedReviewImage = img
                showPhotoReview = true
            }
        }
        .onChange(of: selectedGalleryItem) { newItem in
            Task {
                if let data = try? await newItem?.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    await MainActor.run {
                        capturedReviewImage = img
                        showPhotoReview = true
                    }
                }
            }
        }
        .photosPicker(isPresented: $showGalleryPicker, selection: $selectedGalleryItem, matching: .images)
        .sheet(isPresented: $showTipsSheet) {
            CameraTipsSheet(targetMode: targetMode)
        }
        .fullScreenCover(isPresented: $showPhotoReview) {
            if let img = capturedReviewImage {
                PhotoReviewView(
                    image: img,
                    onRetake: {
                        capturedReviewImage = nil
                        showPhotoReview = false
                        cameraManager.capturedImage = nil
                    },
                    onConfirm: {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        onPhotoConfirmed(img)
                        showPhotoReview = false
                        dismiss()
                    }
                )
            }
        }
    }

    // ─────────────────────────────────────
    // MARK: - TOP BAR CONTROLS
    // ─────────────────────────────────────
    private var topBarControls: some View {
        HStack(spacing: 14) {
            Button(action: { dismiss() }) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 28))
                    .foregroundColor(.white.opacity(0.8))
            }

            Spacer()

            // Timer Toggle (Off -> 3s -> 5s)
            Button(action: cycleTimer) {
                HStack(spacing: 4) {
                    Image(systemName: timerDelaySeconds > 0 ? "timer" : "timer")
                        .font(.system(size: 16, weight: .bold))
                    Text(timerDelaySeconds > 0 ? "\(timerDelaySeconds)s" : "Off")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundColor(timerDelaySeconds > 0 ? .black : .white)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(timerDelaySeconds > 0 ? TryZonTheme.primaryGold : Color.black.opacity(0.6))
                .cornerRadius(100)
                .overlay(RoundedRectangle(cornerRadius: 100).stroke(Color.white.opacity(0.2), lineWidth: 1))
            }

            // Flash Toggle
            Button(action: { cameraManager.toggleFlash() }) {
                Image(systemName: cameraManager.flashIconName)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(cameraManager.isFlashActive ? TryZonTheme.primaryGold : .white)
                    .frame(width: 38, height: 38)
                    .background(Color.black.opacity(0.6))
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
            }

            // Tips Button
            Button(action: { showTipsSheet = true }) {
                Image(systemName: "lightbulb.fill")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(TryZonTheme.primaryGold)
                    .frame(width: 38, height: 38)
                    .background(Color.black.opacity(0.6))
                    .clipShape(Circle())
                    .overlay(Circle().stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
            }
        }
    }

    // ─────────────────────────────────────
    // MARK: - MODE SELECTOR PILL
    // ─────────────────────────────────────
    private var modeSelectorPill: some View {
        HStack(spacing: 4) {
            modeButton(mode: .person, title: "Person Photo 👤")
            modeButton(mode: .garment, title: "Garment Flat-Lay 👗")
        }
        .padding(4)
        .background(Color.black.opacity(0.75))
        .cornerRadius(100)
        .overlay(RoundedRectangle(cornerRadius: 100).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
    }

    private func modeButton(mode: CameraTargetMode, title: String) -> some View {
        let isSelected = targetMode == mode
        return Button(action: {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            withAnimation(.spring(response: 0.25)) { targetMode = mode }
        }) {
            Text(title)
                .font(.system(size: 12, weight: isSelected ? .black : .semibold))
                .foregroundColor(isSelected ? .black : .white.opacity(0.8))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(isSelected ? TryZonTheme.primaryGold : Color.clear)
                .cornerRadius(100)
        }
    }

    // ─────────────────────────────────────
    // MARK: - SILHOUETTE ALIGNMENT OVERLAY
    // ─────────────────────────────────────
    private var silhouetteOverlay: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height

            ZStack {
                if targetMode == .person {
                    // Person Pose Guidelines
                    // 1. Head Oval
                    Ellipse()
                        .stroke(TryZonTheme.primaryGold.opacity(0.65), style: StrokeStyle(lineWidth: 2, dash: [6, 4]))
                        .frame(width: w * 0.38, height: h * 0.22)
                        .position(x: w / 2, y: h * 0.32)

                    // 2. Torso & Shoulder Box
                    RoundedRectangle(cornerRadius: 24)
                        .stroke(TryZonTheme.primaryGold.opacity(0.5), style: StrokeStyle(lineWidth: 2, dash: [8, 6]))
                        .frame(width: w * 0.76, height: h * 0.44)
                        .position(x: w / 2, y: h * 0.62)

                    // Instruction Pill
                    VStack {
                        Spacer()
                        HStack(spacing: 6) {
                            Image(systemName: "person.crop.circle.badge.checkmark")
                                .foregroundColor(TryZonTheme.primaryGold)
                            Text("Fit Head & Upper Body Inside Guide")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(20)
                        .overlay(RoundedRectangle(cornerRadius: 20).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
                        .padding(.bottom, 120)
                    }
                } else {
                    // Garment Flat-Lay Guidelines
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(TryZonTheme.primaryGold.opacity(0.65), style: StrokeStyle(lineWidth: 2.5, dash: [10, 6]))
                        .frame(width: w * 0.82, height: h * 0.55)
                        .position(x: w / 2, y: h * 0.48)

                    // Instruction Pill
                    VStack {
                        Spacer()
                        HStack(spacing: 6) {
                            Image(systemName: "tshirt.fill")
                                .foregroundColor(TryZonTheme.primaryGold)
                            Text("Place Outfit Flat on Plain Background or Hanger")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(20)
                        .overlay(RoundedRectangle(cornerRadius: 20).stroke(TryZonTheme.primaryGold.opacity(0.4), lineWidth: 1))
                        .padding(.bottom, 120)
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────
    // MARK: - BOTTOM SHUTTER CONTROLS
    // ─────────────────────────────────────
    private var bottomControls: some View {
        HStack(spacing: 36) {
            // Gallery Picker Button
            Button(action: { showGalleryPicker = true }) {
                ZStack {
                    Circle()
                        .fill(Color.black.opacity(0.6))
                        .frame(width: 48, height: 48)
                        .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.system(size: 20))
                        .foregroundColor(.white)
                }
            }

            Spacer()

            // Big Shutter Button
            Button(action: triggerCaptureSequence) {
                ZStack {
                    Circle()
                        .stroke(TryZonTheme.primaryGold, lineWidth: 4)
                        .frame(width: 76, height: 76)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.5), radius: 10)

                    Circle()
                        .fill(Color.white)
                        .frame(width: 62, height: 62)
                }
            }
            .buttonStyle(BounceButtonStyle())

            Spacer()

            // Flip Camera Button (Front <-> Back)
            Button(action: {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                cameraManager.flipCamera()
            }) {
                ZStack {
                    Circle()
                        .fill(Color.black.opacity(0.6))
                        .frame(width: 48, height: 48)
                        .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
                    Image(systemName: "arrow.triangle.2.circlepath.camera")
                        .font(.system(size: 20))
                        .foregroundColor(.white)
                }
            }
        }
    }

    private func cycleTimer() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        switch timerDelaySeconds {
        case 0: timerDelaySeconds = 3
        case 3: timerDelaySeconds = 5
        default: timerDelaySeconds = 0
        }
    }

    private func triggerCaptureSequence() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        if timerDelaySeconds > 0 {
            Task {
                var remaining = timerDelaySeconds
                while remaining > 0 {
                    await MainActor.run { countdownRemaining = remaining }
                    try? await Task.sleep(nanoseconds: 1_000_000_000)
                    remaining -= 1
                }
                await MainActor.run {
                    countdownRemaining = nil
                    cameraManager.capturePhoto()
                }
            }
        } else {
            cameraManager.capturePhoto()
        }
    }

    // ─────────────────────────────────────
    // MARK: - PERMISSION DENIED VIEW
    // ─────────────────────────────────────
    private var permissionDeniedView: some View {
        VStack(spacing: 20) {
            Image(systemName: "camera.fill")
                .font(.system(size: 56))
                .foregroundColor(TryZonTheme.primaryGold)

            Text("Camera Access Required")
                .font(.system(size: 22, weight: .black, design: .rounded))
                .foregroundColor(.white)

            Text("TryZon AI needs camera access to capture your body photo with live alignment guidelines for virtual outfit fitting.")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.75))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button(action: {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }) {
                Text("OPEN SETTINGS")
                    .font(.system(size: 14, weight: .black))
                    .foregroundColor(.black)
                    .padding(.horizontal, 28)
                    .padding(.vertical, 12)
                    .background(TryZonTheme.primaryGold)
                    .cornerRadius(12)
            }

            Button("Choose from Gallery Instead") {
                showGalleryPicker = true
            }
            .font(.system(size: 13, weight: .bold))
            .foregroundColor(TryZonTheme.primaryGold)
            .padding(.top, 10)
        }
    }
}

// ─────────────────────────────────────
// MARK: - CAMERA MANAGER (AVFoundation)
// ─────────────────────────────────────
final class CameraManager: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate {
    @Published var hasPermission: Bool = false
    @Published var capturedImage: UIImage? = nil
    @Published var isFlashActive: Bool = false
    @Published var flashIconName: String = "bolt.slash.fill"

    let session = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    private var currentPosition: AVCaptureDevice.Position = .front
    private var flashMode: AVCaptureDevice.FlashMode = .off

    func checkPermissionAndStart() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            hasPermission = true
            setupSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    self.hasPermission = granted
                    if granted { self.setupSession() }
                }
            }
        default:
            hasPermission = false
        }
    }

    private func setupSession() {
        session.beginConfiguration()
        session.sessionPreset = .photo

        // Configure Input
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: currentPosition),
           let input = try? AVCaptureDeviceInput(device: device) {
            if session.canAddInput(input) { session.addInput(input) }
        }

        // Configure Output
        if session.canAddOutput(photoOutput) {
            session.addOutput(photoOutput)
        }

        session.commitConfiguration()

        DispatchQueue.global(qos: .userInitiated).async {
            self.session.startRunning()
        }
    }

    func stopSession() {
        if session.isRunning {
            DispatchQueue.global(qos: .userInitiated).async {
                self.session.stopRunning()
            }
        }
    }

    func flipCamera() {
        session.beginConfiguration()
        for input in session.inputs { session.removeInput(input) }

        currentPosition = (currentPosition == .front) ? .back : .front

        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: currentPosition),
           let input = try? AVCaptureDeviceInput(device: device) {
            if session.canAddInput(input) { session.addInput(input) }
        }
        session.commitConfiguration()
    }

    func toggleFlash() {
        switch flashMode {
        case .off:
            flashMode = .on
            isFlashActive = true
            flashIconName = "bolt.fill"
        case .on:
            flashMode = .auto
            isFlashActive = true
            flashIconName = "bolt.badge.a.fill"
        default:
            flashMode = .off
            isFlashActive = false
            flashIconName = "bolt.slash.fill"
        }
    }

    func focus(at point: CGPoint) {
        guard let device = (session.inputs.first as? AVCaptureDeviceInput)?.device else { return }
        do {
            try device.lockForConfiguration()
            if device.isFocusPointOfInterestSupported {
                device.focusPointOfInterest = point
                device.focusMode = .autoFocus
            }
            device.unlockForConfiguration()
        } catch {}
    }

    func capturePhoto() {
        let settings = AVCapturePhotoSettings()
        if photoOutput.supportedFlashModes.contains(flashMode) {
            settings.flashMode = flashMode
        }
        photoOutput.capturePhoto(with: settings, delegate: self)
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard let data = photo.fileDataRepresentation(), let img = UIImage(data: data) else { return }
        // Fix mirror orientation for front camera
        let finalImage: UIImage
        if currentPosition == .front, let cg = img.cgImage {
            finalImage = UIImage(cgImage: cg, scale: img.scale, orientation: .leftMirrored)
        } else {
            finalImage = img
        }
        DispatchQueue.main.async {
            self.capturedImage = finalImage
        }
    }
}

// ─────────────────────────────────────
// MARK: - PREVIEW LAYER VIEW
// ─────────────────────────────────────
struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.videoPreviewLayer.session = session
        view.videoPreviewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {}

    class PreviewUIView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var videoPreviewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

// ─────────────────────────────────────
// MARK: - PHOTO REVIEW VIEW
// ─────────────────────────────────────
struct PhotoReviewView: View {
    let image: UIImage
    let onRetake: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            VStack {
                HStack {
                    Text("Photo Preview")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)

                Spacer()

                HStack(spacing: 16) {
                    Button(action: onRetake) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.counterclockwise")
                            Text("RETAKE")
                        }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.white.opacity(0.18))
                        .cornerRadius(14)
                    }

                    Button(action: onConfirm) {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                            Text("USE PHOTO ⚡")
                        }
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(14)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 36)
            }
        }
    }
}

// ─────────────────────────────────────
// MARK: - CAMERA TIPS SHEET
// ─────────────────────────────────────
struct CameraTipsSheet: View {
    let targetMode: CameraCaptureView.CameraTargetMode
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(alignment: .leading, spacing: 20) {
                HStack {
                    Text("AI Studio Camera Guide 📸")
                        .font(.system(size: 20, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(.top, 14)

                Text(targetMode == .person ? "Follow these tips to get the highest accuracy virtual fitting:" : "Follow these tips for the cleanest garment extraction:")
                    .font(.system(size: 13))
                    .foregroundColor(.white.opacity(0.7))

                if targetMode == .person {
                    tipItem("💡 Good Lighting", "Face natural light or a bright room. Avoid dark shadows across your torso.")
                    tipItem("🧍 Full or Upper Body Pose", "Stand 6–8 feet back. Keep head, shoulders, and chest clearly visible inside the guide.")
                    tipItem("🎨 Plain Background", "A simple solid background (wall, door) produces the cleanest virtual try-on drape.")
                    tipItem("👕 Fitted Clothing", "Wearing fitted clothes helps our neural engine map your exact body shape.")
                } else {
                    tipItem("👔 Flat-Lay or Hanger", "Lay the garment flat on a plain table or bed, or hang it against a plain wall.")
                    tipItem("💡 Even Lighting", "Ensure even lighting so fabric texture and color are preserved faithfully.")
                    tipItem("📐 No Obstructions", "Make sure no hands, tags, or other garments overlap the piece.")
                }

                Spacer()

                Button(action: { dismiss() }) {
                    Text("GOT IT")
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(14)
                }
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 20)
        }
    }

    private func tipItem(_ title: String, _ desc: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(TryZonTheme.primaryGold)
            Text(desc)
                .font(.system(size: 12.5))
                .foregroundColor(.white.opacity(0.8))
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(12)
    }
}
