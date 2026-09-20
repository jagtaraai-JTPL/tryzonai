import SwiftUI

public struct DailyStyleSettingsView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @AppStorage("daily_style_enabled") private var dailyStyleEnabled: Bool = true
    @AppStorage("daily_style_time") private var dailyStyleTime: String = "08:00 AM"
    @AppStorage("user_gender") private var userGender: String = "Women"
    @State private var toastMessage: String? = nil
    @State private var isTesting: Bool = false

    public init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    public var body: some View {
        NavigationView {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    Spacer(minLength: 10)

                    // Header Visual
                    VStack(spacing: 10) {
                        Circle()
                            .fill(TryZonTheme.primaryGold.opacity(0.18))
                            .frame(width: 72, height: 72)
                            .overlay(
                                Image(systemName: "tshirt.fill")
                                    .font(.system(size: 34))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            )

                        Text("Daily AI Style Engine")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                        Text("Automated daily outfit suggestions powered by AI")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                            .multilineTextAlignment(.center)
                    }

                    // Master Switch
                    Toggle(isOn: $dailyStyleEnabled) {
                        HStack(spacing: 12) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 18))
                                .foregroundColor(TryZonTheme.primaryGold)

                            VStack(alignment: .leading, spacing: 2) {
                                Text("Enable Daily AI Style Engine")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                Text("Generates 1 auto try-on push notification every morning")
                                    .font(.system(size: 11))
                                    .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                            }
                        }
                    }
                    .padding(16)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .cornerRadius(20)
                    .tint(TryZonTheme.primaryGold)

                    if dailyStyleEnabled {
                        // Configuration Section
                        VStack(alignment: .leading, spacing: 12) {
                            Text("ENGINE CONFIGURATION")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(TryZonTheme.primaryGold)
                                .tracking(1)

                            VStack(spacing: 14) {
                                // Time Selector
                                HStack {
                                    Image(systemName: "clock.fill")
                                        .foregroundColor(TryZonTheme.primaryGold)
                                    Text("Delivery Time")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                    Spacer()
                                    Text(dailyStyleTime)
                                        .font(.system(size: 13, weight: .black))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 5)
                                        .background(TryZonTheme.primaryGold.opacity(0.2))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                        .cornerRadius(10)
                                }

                                Divider()

                                // Gender Filter
                                HStack {
                                    Image(systemName: "person.2.fill")
                                        .foregroundColor(TryZonTheme.primaryGold)
                                    Text("Outfit Category")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                    Spacer()
                                    Text(userGender)
                                        .font(.system(size: 13, weight: .black))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 5)
                                        .background(TryZonTheme.primaryGold.opacity(0.2))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                        .cornerRadius(10)
                                }
                            }
                            .padding(16)
                            .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                            .cornerRadius(20)
                        }

                        // Test Daily Style CTA
                        Button(action: testDailyStyleNow) {
                            HStack(spacing: 8) {
                                if isTesting {
                                    ProgressView().tint(.black)
                                    Text("GENERATING DAILY OUTFIT...")
                                        .font(.system(size: 14, weight: .black))
                                } else {
                                    Image(systemName: "bolt.fill")
                                    Text("TEST DAILY AI STYLE NOW ⚡")
                                        .font(.system(size: 14, weight: .black, design: .rounded))
                                }
                            }
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(TryZonTheme.primaryGold)
                            .cornerRadius(25)
                            .shadow(color: TryZonTheme.primaryGold.opacity(0.3), radius: 6, y: 3)
                        }
                        .disabled(isTesting)
                        .buttonStyle(BounceButtonStyle())
                    }

                    Spacer(minLength: 20)
                }
                .padding(20)
            }
            .navigationTitle("Daily AI Style")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 14, weight: .bold))
                }
            }
        }
    }

    private func testDailyStyleNow() {
        isTesting = true
        Task {
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            await MainActor.run {
                self.isTesting = false
                self.showToast("⚡ Instant Daily AI Style push triggered!")
            }
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { toastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { toastMessage = nil }
        }
    }
}
