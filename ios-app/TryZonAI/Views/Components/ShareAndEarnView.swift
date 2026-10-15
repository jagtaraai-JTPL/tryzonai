import SwiftUI
import UIKit

public struct ShareAndEarnView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var isCopied: Bool = false
    @State private var showShareSheet: Bool = false

    private let shareLink = "https://tryzonai.com"
    private let shareMessage = "Try on any outfit virtually with TryZon AI Virtual Fitting Room! 🤩 Download now: https://tryzonai.com"

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public var body: some View {
        ZStack {
            TryZonTheme.backgroundColor(for: colorScheme)
                .ignoresSafeArea()

            VStack(spacing: 22) {
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

                // Visual Hero Icon
                ZStack {
                    Circle()
                        .fill(TryZonTheme.primaryGold.opacity(0.15))
                        .frame(width: 84, height: 84)

                    Image(systemName: "sparkles")
                        .font(.system(size: 42))
                        .foregroundColor(TryZonTheme.primaryGold)
                }

                // Title & Description
                VStack(spacing: 8) {
                    Text("INVITE FRIENDS & SHARE")
                        .font(.system(size: 24, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(1)

                    Text("Share TryZon AI Virtual Fitting Studio with your friends and family to help them style outfits effortlessly.")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }

                // Referral Link Copy Box
                VStack(alignment: .leading, spacing: 8) {
                    Text("APP LINK")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(1)

                    HStack {
                        Text(shareLink)
                            .font(.system(size: 13, weight: .semibold, design: .monospaced))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                            .lineLimit(1)

                        Spacer()

                        Button(action: copyLink) {
                            HStack(spacing: 4) {
                                Image(systemName: isCopied ? "checkmark" : "doc.on.doc")
                                Text(isCopied ? "Copied!" : "Copy")
                            }
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.black)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(TryZonTheme.primaryGold)
                            .clipShape(Capsule())
                        }
                    }
                    .padding(12)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .cornerRadius(14)
                }
                .padding(.horizontal, 20)

                Spacer(minLength: 10)

                // Action Buttons
                VStack(spacing: 12) {
                    // WhatsApp Share
                    Button(action: shareToWhatsApp) {
                        HStack(spacing: 8) {
                            Text("💬")
                            Text("Share via WhatsApp")
                                .font(.system(size: 15, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color(red: 37/255, green: 211/255, blue: 102/255))
                        .clipShape(Capsule())
                        .shadow(color: Color.green.opacity(0.3), radius: 8, y: 3)
                    }
                    .buttonStyle(BounceButtonStyle())

                    // General Share Sheet
                    Button(action: triggerShareSheet) {
                        HStack(spacing: 8) {
                            Image(systemName: "square.and.arrow.up.fill")
                            Text("Share via Messages / AirDrop")
                                .font(.system(size: 15, weight: .bold))
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(TryZonTheme.primaryGold)
                        .clipShape(Capsule())
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.4), radius: 8, y: 3)
                    }
                    .buttonStyle(BounceButtonStyle())
                }
                .padding(.horizontal, 24)

                Spacer(minLength: 20)
            }
            .padding(.top, 16)
        }
        .sheet(isPresented: $showShareSheet) {
            ActivityViewControllerWrapper(activityItems: [shareMessage])
        }
    }

    private func copyLink() {
        UIPasteboard.general.string = shareLink
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        withAnimation {
            isCopied = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation {
                isCopied = false
            }
        }
    }

    private func shareToWhatsApp() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        let encodedMessage = shareMessage.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        if let whatsappURL = URL(string: "whatsapp://send?text=\(encodedMessage)"),
           UIApplication.shared.canOpenURL(whatsappURL) {
            UIApplication.shared.open(whatsappURL)
        } else {
            triggerShareSheet()
        }
    }

    private func triggerShareSheet() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        showShareSheet = true
    }
}

// Activity View Controller Wrapper for UIKit Share Sheet
struct ActivityViewControllerWrapper: UIViewControllerRepresentable {
    var activityItems: [Any]
    var applicationActivities: [UIActivity]? = nil

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
