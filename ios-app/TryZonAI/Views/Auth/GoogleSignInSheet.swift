import SwiftUI
import SafariServices

/// SafariViewController wrapper for SwiftUI
public struct SafariView: UIViewControllerRepresentable {
    let url: URL

    public init(url: URL) {
        self.url = url
    }

    public func makeUIViewController(context: Context) -> SFSafariViewController {
        let config = SFSafariViewController.Configuration()
        config.entersReaderIfAvailable = false
        let safariVC = SFSafariViewController(url: url, configuration: config)
        safariVC.preferredControlTintColor = .systemBlue
        return safariVC
    }

    public func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

public struct GoogleSignInSheet: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @AppStorage("saved_google_email") private var savedGoogleEmail: String = ""
    @State private var googleEmail: String = ""
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var showSafariWebLogin: Bool = false

    let onLoginSuccess: () -> Void

    public init(apiClient: APIClient, initialEmail: String = "", onLoginSuccess: @escaping () -> Void) {
        self.apiClient = apiClient
        self._googleEmail = State(initialValue: initialEmail)
        self.onLoginSuccess = onLoginSuccess
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 18) {
                Spacer(minLength: 10)

                // ── 1. GOOGLE BRAND HEADER ──
                VStack(spacing: 10) {
                    ZStack {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 68, height: 68)
                            .shadow(color: Color.black.opacity(0.12), radius: 8, y: 3)

                        Text("G")
                            .font(.system(size: 36, weight: .black, design: .rounded))
                            .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))
                    }

                    Text("Sign in with Google")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                    Text("Select a Google account to continue to TryZon AI")
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

                // ── 2. SAVED 1-TAP GOOGLE ACCOUNT CARD (IF SAVED) ──
                if !savedGoogleEmail.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("SAVED GOOGLE ACCOUNT (1-TAP SIGN IN)")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)

                        Button(action: {
                            googleEmail = savedGoogleEmail
                            performGoogleSignIn()
                        }) {
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(Color(red: 66/255, green: 133/255, blue: 244/255).opacity(0.18))
                                    .frame(width: 44, height: 44)
                                    .overlay(
                                        Text("G")
                                            .font(.system(size: 20, weight: .black))
                                            .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))
                                    )

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(savedGoogleEmail)
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                    Text("✦ Instant 1-Tap Google Sign-In")
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

                // ── 3. ACCOUNT SELECTOR & INPUT ──
                VStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(savedGoogleEmail.isEmpty ? "ENTER GOOGLE EMAIL" : "OR USE ANOTHER GOOGLE ACCOUNT")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))

                        HStack {
                            Text("G")
                                .font(.system(size: 16, weight: .black))
                                .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))

                            TextField("e.g. alex.fashion@gmail.com", text: $googleEmail)
                                .font(.system(size: 14, weight: .medium))
                                .autocapitalization(.none)
                                .keyboardType(.emailAddress)

                            if !googleEmail.isEmpty {
                                Button(action: { googleEmail = "" }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                        .padding(14)
                        .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                        .cornerRadius(14)
                    }

                    // Primary Google Sign In Button
                    Button(action: performGoogleSignIn) {
                        HStack(spacing: 10) {
                            if isLoading {
                                ProgressView()
                                    .tint(.black)
                                Text("Connecting to Google...")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.black)
                            } else {
                                Text("G")
                                    .font(.system(size: 20, weight: .black))
                                    .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))

                                Text("Continue with Google")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.black)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(25)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.3), radius: 6, y: 3)
                    }
                    .disabled(isLoading)
                    .buttonStyle(BounceButtonStyle())

                    // Or Web Browser Login Option
                    Button(action: { showSafariWebLogin = true }) {
                        HStack(spacing: 8) {
                            Image(systemName: "safari")
                                .font(.system(size: 14))
                            Text("Select Account from Google Safari Web Sheet 🌐")
                                .font(.system(size: 12.5, weight: .semibold))
                        }
                        .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))
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

                    Text("Google will securely share your email & profile to authenticate with TryZon AI.")
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
            .sheet(isPresented: $showSafariWebLogin) {
                if let url = URL(string: "https://accounts.google.com/AccountChooser") {
                    SafariView(url: url)
                }
            }
        }
    }

    private func performGoogleSignIn() {
        let cleanEmail = googleEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanEmail.isEmpty else {
            errorMessage = "Please enter your Google Email address."
            return
        }

        guard cleanEmail.contains("@") else {
            errorMessage = "Please enter a valid Google email address."
            return
        }

        isLoading = true
        errorMessage = nil

        Task {
            do {
                _ = try await apiClient.loginWithGoogle(idToken: cleanEmail)
                await MainActor.run {
                    self.isLoading = false
                    self.savedGoogleEmail = cleanEmail
                    self.onLoginSuccess()
                    self.dismiss()
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    self.errorMessage = "Google Sign In Failed: \(error.localizedDescription)"
                }
            }
        }
    }
}
