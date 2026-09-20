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
            VStack(spacing: 20) {
                Spacer(minLength: 10)

                // ── 1. GOOGLE BRAND HEADER ──
                VStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 72, height: 72)
                            .shadow(color: Color.black.opacity(0.12), radius: 10, y: 4)

                        Text("G")
                            .font(.system(size: 38, weight: .black, design: .rounded))
                            .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))
                    }

                    Text("Sign in with Google")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                    Text("Choose an account to continue to TryZon AI")
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

                // ── 2. QUICK GOOGLE ACCOUNT SELECTOR ──
                VStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("GOOGLE EMAIL ADDRESS")
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

                    // Quick Suggested Accounts (if empty)
                    if googleEmail.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("QUICK SUGGESTIONS")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))

                            Button(action: {
                                googleEmail = "user_\(UUID().uuidString.prefix(5).lowercased())@gmail.com"
                            }) {
                                HStack(spacing: 12) {
                                    Image(systemName: "person.crop.circle.badge.plus")
                                        .font(.system(size: 20))
                                        .foregroundColor(TryZonTheme.primaryGold)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Create Instant Google Guest Account")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                        Text("Claim 2 FREE Bonus Try-On Credits instantly")
                                            .font(.system(size: 11))
                                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                                    }
                                    Spacer()
                                }
                                .padding(12)
                                .background(TryZonTheme.surfaceVariantColor(for: colorScheme).opacity(0.6))
                                .cornerRadius(12)
                            }
                        }
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
                        .frame(height: 52)
                        .background(TryZonTheme.primaryGold)
                        .cornerRadius(26)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.3), radius: 6, y: 3)
                    }
                    .disabled(isLoading)
                    .buttonStyle(BounceButtonStyle())

                    // Or Web Browser Login Option
                    Button(action: { showSafariWebLogin = true }) {
                        HStack(spacing: 8) {
                            Image(systemName: "safari")
                                .font(.system(size: 14))
                            Text("Open Google Web Login Sheet 🌐")
                                .font(.system(size: 12.5, weight: .semibold))
                        }
                        .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))
                        .padding(.vertical, 8)
                    }
                }
                .padding(.horizontal, 20)

                // ── 3. PRIVACY & FOOTER ──
                Spacer()

                VStack(spacing: 6) {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.seal.fill")
                            .foregroundColor(TryZonTheme.primaryGold)
                            .font(.system(size: 12))
                        Text("2 Welcome Bonus Credits Granted Automatically")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                    }

                    Text("To continue, Google will share your name, email address, and profile picture with TryZon AI.")
                        .font(.system(size: 10, weight: .regular))
                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
                .padding(.bottom, 20)
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
                if let url = URL(string: "https://accounts.google.com/signin") {
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
