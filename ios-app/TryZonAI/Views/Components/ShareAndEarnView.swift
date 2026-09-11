import SwiftUI
import UIKit

public struct ShareAndEarnView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var isCopied: Bool = false
    @State private var showShareSheet: Bool = false
    @State private var hasClaimedReward: Bool = false

    private let referralLink = "https://tryzonai.com?ref=TRYZON_VIP"
    private let shareMessage = "Try any outfit virtually on TryZon AI! Download & get 2 FREE credits: https://tryzonai.com"

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

                    Text("🎁")
                        .font(.system(size: 48))
                }

                // Title & Description
                VStack(spacing: 8) {
                    Text("REFER & EARN")
                        .font(.system(size: 24, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(1)

                    Text("Invite your friends to TryZon AI and get 2 FREE Try-On credits for every install!")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }

                // Referral Link Copy Box
                VStack(alignment: .leading, spacing: 8) {
                    Text("YOUR EXCLUSIVE REFERRAL LINK")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .tracking(1)

                    HStack {
                        Text(referralLink)
                            .font(.system(size: 12, weight: .semibold, design: .monospaced))
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
                            Text("More Share Options")
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

                if hasClaimedReward {
                    Text("🎉 +2 Bonus Credits added to your balance!")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.green)
                        .transition(.opacity)
                }

                Spacer(minLength: 20)
            }
            .padding(.top, 16)
        }
        .sheet(isPresented: $showShareSheet) {
            ActivityViewControllerWrapper(activityItems: [shareMessage])
        }
    }

    private func copyLink() {
        UIPasteboard.general.string = referralLink
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        withAnimation {
            isCopied = true
        }

        if !hasClaimedReward {
            hasClaimedReward = true
            Task {
                try? await apiClient.claimRewardCredit(amount: 2)
            }
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation {
                isCopied = false
            }
        }
    }

    private func shareToWhatsApp() {
        let encodedMessage = shareMessage.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        if let whatsappURL = URL(string: "whatsapp://send?text=\(encodedMessage)"),
           UIApplication.shared.canOpenURL(whatsappURL) {
            UIApplication.shared.open(whatsappURL)
        } else {
            triggerShareSheet()
            return
        }

        if !hasClaimedReward {
            hasClaimedReward = true
            Task {
                try? await apiClient.claimRewardCredit(amount: 2)
            }
        }
    }

    private func triggerShareSheet() {
        showShareSheet = true
        if !hasClaimedReward {
            hasClaimedReward = true
            Task {
                try? await apiClient.claimRewardCredit(amount: 2)
            }
        }
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
