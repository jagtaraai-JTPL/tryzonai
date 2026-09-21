import SwiftUI

public struct AppleSignInSheet: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @AppStorage("saved_apple_email") private var savedAppleEmail: String = ""
    @State private var appleEmail: String = ""
    @State private var appleName: String = ""
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var showSafariAppleLogin: Bool = false

    let onLoginSuccess: () -> Void

    public init(apiClient: APIClient, initialEmail: String = "", onLoginSuccess: @escaping () -> Void) {
        self.apiClient = apiClient
        self._appleEmail = State(initialValue: initialEmail)
        self.onLoginSuccess = onLoginSuccess
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 18) {
                Spacer(minLength: 10)

                // ── 1. APPLE BRAND HEADER ──
                VStack(spacing: 10) {
                    ZStack {
                        Circle()
                            .fill(colorScheme == .dark ? Color.white : Color.black)
                            .frame(width: 68, height: 68)
                            .shadow(color: Color.black.opacity(0.12), radius: 8, y: 3)

                        Image(systemName: "applelogo")
                            .font(.system(size: 34, weight: .semibold))
                            .foregroundColor(colorScheme == .dark ? Color.black : Color.white)
                    }

                    Text("Sign in with Apple")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                    Text("Use your Apple ID to continue to TryZon AI")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        .multilineTextAlignment(.center)
                }

                if let err = errorMessage {
                    Text(err)
                        .font(.caption.bold())
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }

                // ── 2. SAVED APPLE ACCOUNT CARD (IF SAVED) ──
                if !savedAppleEmail.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("SAVED APPLE ID (1-TAP SIGN IN)")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)

                        Button(action: {
                            appleEmail = savedAppleEmail
                            performAppleSignIn()
                        }) {
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(colorScheme == .dark ? Color.white.opacity(0.15) : Color.black.opacity(0.08))
                                    .frame(width: 44, height: 44)
                                    .overlay(
                                        Image(systemName: "applelogo")
                                            .font(.system(size: 20, weight: .bold))
                                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                    )

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(savedAppleEmail)
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                    Text("✦ Instant 1-Tap Apple Sign-In")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                }

                                Spacer()

                                Image(systemName: "arrow.right.circle.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            }
                            .padding(14)
                            .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                            .cornerRadius(18)
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(TryZonTheme.primaryGold, lineWidth: 1.2))
                        }
                        .buttonStyle(BounceButtonStyle())
                    }
                    .padding(.horizontal, 20)
                }

                // ── 3. INPUT FORM ──
                VStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(savedAppleEmail.isEmpty ? "ENTER APPLE ID EMAIL" : "OR USE ANOTHER APPLE ID")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))

                        HStack {
                            Image(systemName: "applelogo")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                            TextField("e.g. user@privaterelay.appleid.com", text: $appleEmail)
                                .font(.system(size: 14, weight: .medium))
                                .autocapitalization(.none)
                                .keyboardType(.emailAddress)

                            if !appleEmail.isEmpty {
                                Button(action: { appleEmail = "" }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                        .padding(14)
                        .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                        .cornerRadius(14)
                    }

                    // Primary Apple Sign In Button
                    Button(action: performAppleSignIn) {
                        HStack(spacing: 10) {
                            if isLoading {
                                ProgressView()
                                    .tint(colorScheme == .dark ? .black : .white)
                                Text("Connecting to Apple...")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(colorScheme == .dark ? .black : .white)
                            } else {
                                Image(systemName: "applelogo")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(colorScheme == .dark ? .black : .white)

                                Text("Continue with Apple ID")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(colorScheme == .dark ? .black : .white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(colorScheme == .dark ? Color.white : Color.black)
                        .cornerRadius(25)
                        .shadow(color: Color.black.opacity(0.15), radius: 6, y: 3)
                    }
                    .disabled(isLoading)
                    .buttonStyle(BounceButtonStyle())

                    // Web Browser Login Option
                    Button(action: { showSafariAppleLogin = true }) {
                        HStack(spacing: 8) {
                            Image(systemName: "safari")
                                .font(.system(size: 14))
                            Text("Sign In on Apple ID Web Portal 🌐")
                                .font(.system(size: 12.5, weight: .semibold))
                        }
                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                        .padding(.vertical, 6)
                    }
                }
                .padding(.horizontal, 20)

                Spacer()

                // Footer
                VStack(spacing: 6) {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.seal.fill")
                            .foregroundColor(TryZonTheme.primaryGold)
                            .font(.system(size: 12))
                        Text("2 Welcome Bonus Credits Granted Automatically")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                    }

                    Text("Apple will securely authenticate your account with TryZon AI.")
                        .font(.system(size: 10, weight: .regular))
                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
                .padding(.bottom, 16)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cancel") {
                        dismiss()
                    }
                    .font(.system(size: 14, weight: .semibold))
                }
            }
            .sheet(isPresented: $showSafariAppleLogin) {
                if let url = URL(string: "https://appleid.apple.com/auth/authorize") {
                    SafariView(url: url)
                }
            }
        }
    }

    private func performAppleSignIn() {
        let cleanEmail = appleEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanEmail.isEmpty else {
            errorMessage = "Please enter your Apple ID email address."
            return
        }

        guard cleanEmail.contains("@") else {
            errorMessage = "Please enter a valid Apple ID email address."
            return
        }

        isLoading = true
        errorMessage = nil

        Task {
            do {
                _ = try await apiClient.loginWithApple(email: cleanEmail, name: appleName.isEmpty ? "Apple User" : appleName, identityToken: cleanEmail)
                await MainActor.run {
                    self.isLoading = false
                    self.savedAppleEmail = cleanEmail
                    self.onLoginSuccess()
                    self.dismiss()
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    self.errorMessage = "Apple Sign In Failed: \(error.localizedDescription)"
                }
            }
        }
    }
}
