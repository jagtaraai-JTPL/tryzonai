import SwiftUI

public struct ThemeSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage("is_dark_theme") private var isDarkTheme: Bool = true
    @AppStorage("use_system_theme") private var useSystemTheme: Bool = false

    public init() {}

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
                                Image(systemName: isDarkTheme ? "moon.stars.fill" : "sun.max.fill")
                                    .font(.system(size: 36))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            )

                        Text("Appearance & Themes")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                        Text("Customize TryZon AI visual studio styling")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                    }

                    // Theme Mode Selector Cards
                    VStack(alignment: .leading, spacing: 12) {
                        Text("SELECT VISUAL THEME")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(1)

                        HStack(spacing: 14) {
                            // Dark Theme Card
                            Button(action: {
                                useSystemTheme = false
                                isDarkTheme = true
                            }) {
                                VStack(spacing: 10) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 16)
                                            .fill(Color(red: 18/255, green: 18/255, blue: 18/255))
                                            .frame(height: 80)
                                            .overlay(
                                                VStack(spacing: 4) {
                                                    Capsule().fill(TryZonTheme.primaryGold).frame(width: 40, height: 6)
                                                    RoundedRectangle(cornerRadius: 6).fill(Color.white.opacity(0.12)).frame(width: 50, height: 24)
                                                }
                                            )
                                    }

                                    HStack {
                                        Text("Dark Mode 🌙")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.white)
                                        if !useSystemTheme && isDarkTheme {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundColor(TryZonTheme.primaryGold)
                                        }
                                    }
                                }
                                .padding(12)
                                .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                                .cornerRadius(20)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 20)
                                        .stroke(!useSystemTheme && isDarkTheme ? TryZonTheme.primaryGold : Color.clear, lineWidth: 2)
                                )
                            }
                            .buttonStyle(BounceButtonStyle())

                            // Light Theme Card
                            Button(action: {
                                useSystemTheme = false
                                isDarkTheme = false
                            }) {
                                VStack(spacing: 10) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 16)
                                            .fill(Color(red: 250/255, green: 249/255, blue: 246/255))
                                            .frame(height: 80)
                                            .overlay(
                                                VStack(spacing: 4) {
                                                    Capsule().fill(Color.black.opacity(0.8)).frame(width: 40, height: 6)
                                                    RoundedRectangle(cornerRadius: 6).fill(Color.black.opacity(0.08)).frame(width: 50, height: 24)
                                                }
                                            )
                                    }

                                    HStack {
                                        Text("Light Mode ☀️")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                        if !useSystemTheme && !isDarkTheme {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundColor(TryZonTheme.primaryGold)
                                        }
                                    }
                                }
                                .padding(12)
                                .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                                .cornerRadius(20)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 20)
                                        .stroke(!useSystemTheme && !isDarkTheme ? TryZonTheme.primaryGold : Color.clear, lineWidth: 2)
                                )
                            }
                            .buttonStyle(BounceButtonStyle())
                        }
                    }

                    // System Theme Sync Toggle Card
                    Toggle(isOn: $useSystemTheme) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Sync with iOS System Theme")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                            Text("Automatically switch theme based on iOS system dark mode settings")
                                .font(.system(size: 11))
                                .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        }
                    }
                    .padding(16)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .cornerRadius(18)
                    .tint(TryZonTheme.primaryGold)

                    Spacer(minLength: 20)
                }
                .padding(20)
            }
            .navigationTitle("Theme Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 14, weight: .bold))
                }
            }
        }
    }
}
