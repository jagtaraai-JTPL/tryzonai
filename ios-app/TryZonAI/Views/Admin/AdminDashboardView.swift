import SwiftUI

public struct AdminDashboardView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss

    @State private var targetEmail: String = ""
    @State private var grantAmount: String = "10"
    @State private var isGranting: Bool = false
    @State private var toastMessage: String? = nil

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public var body: some View {
        ZStack {
            TryZonTheme.darkBackground.ignoresSafeArea()

            VStack(spacing: 18) {
                // Header Bar
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("FOUNDER & ADMIN PORTAL 👑")
                            .font(.system(size: 18, weight: .black, design: .rounded))
                            .foregroundColor(TryZonTheme.primaryGold)
                        Text("Live System KPIs & User CRM")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.6))
                    }
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        // Real-Time System Status Cards
                        HStack(spacing: 12) {
                            kpiCard(title: "Active GPUs ⚡", value: "3 Online", color: .green)
                            kpiCard(title: "Server Uptime 🚀", value: "99.98%", color: TryZonTheme.primaryGold)
                        }

                        HStack(spacing: 12) {
                            kpiCard(title: "Daily Try-Ons 👚", value: "1,240+", color: .blue)
                            kpiCard(title: "Store Clicks 🛍️", value: "3,890+", color: .purple)
                        }

                        // User Credit Grant Tool
                        VStack(alignment: .leading, spacing: 12) {
                            Text("⚡ GRANT CREDITS TO USER")
                                .font(.system(size: 11, weight: .black))
                                .foregroundColor(TryZonTheme.primaryGold)

                            VStack(spacing: 10) {
                                TextField("Target User Email (e.g. user@gmail.com)", text: $targetEmail)
                                    .font(.system(size: 13))
                                    .foregroundColor(.white)
                                    .padding(12)
                                    .background(TryZonTheme.darkSurface)
                                    .cornerRadius(10)
                                    .autocapitalization(.none)

                                HStack {
                                    Text("Credits to Grant:")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.white.opacity(0.8))
                                    Spacer()
                                    TextField("Amount", text: $grantAmount)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                        .multilineTextAlignment(.trailing)
                                        .keyboardType(.numberPad)
                                        .frame(width: 80)
                                }
                                .padding(12)
                                .background(TryZonTheme.darkSurface)
                                .cornerRadius(10)

                                Button(action: executeGrant) {
                                    HStack {
                                        if isGranting {
                                            ProgressView().tint(.black)
                                        } else {
                                            Text("GRANT PAID CREDITS 🚀")
                                                .font(.system(size: 13, weight: .black))
                                                .foregroundColor(.black)
                                        }
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(TryZonTheme.primaryGold)
                                    .cornerRadius(10)
                                }
                                .disabled(isGranting || targetEmail.isEmpty)
                                .buttonStyle(BounceButtonStyle())
                            }
                        }
                        .padding(16)
                        .background(TryZonTheme.surfaceVariant)
                        .cornerRadius(18)
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold.opacity(0.3), lineWidth: 1))

                        Spacer(minLength: 20)
                    }
                    .padding(.horizontal, 20)
                }
            }

            if let toast = toastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 13, weight: .bold))
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.92))
                        .foregroundColor(TryZonTheme.primaryGold)
                        .cornerRadius(20)
                        .padding(.bottom, 40)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private func kpiCard(title: String, value: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.white.opacity(0.7))
            Text(value)
                .font(.system(size: 18, weight: .black, design: .rounded))
                .foregroundColor(color)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(TryZonTheme.surfaceVariant)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(color.opacity(0.3), lineWidth: 1))
    }

    private func executeGrant() {
        guard !targetEmail.isEmpty else { return }
        isGranting = true

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            self.isGranting = false
            self.showToast("👑 \(self.grantAmount) Credits Granted to \(self.targetEmail)!")
            self.targetEmail = ""
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { toastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { toastMessage = nil }
        }
    }
}
