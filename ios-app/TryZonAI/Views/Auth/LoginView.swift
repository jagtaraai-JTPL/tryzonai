import SwiftUI
import AuthenticationServices

public struct LoginView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss

    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String? = nil

    let onNavigateToRegister: () -> Void

    public init(apiClient: APIClient, onNavigateToRegister: @escaping () -> Void) {
        self.apiClient = apiClient
        self.onNavigateToRegister = onNavigateToRegister
    }

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 20) {
                Spacer(minLength: 20)

                // Header Branding
                VStack(spacing: 8) {
                    Image("AppLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 72, height: 72)
                        .cornerRadius(18)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.4), radius: 10)

                    Text("Welcome Back ⚡")
                        .font(.system(size: 22, weight: .black, design: .rounded))
                        .foregroundColor(.white)

                    Text("Sign in to your TryZon AI Virtual Fitting Room")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }

                // ── 1. SOCIAL SIGN IN BUTTONS (Matching Android) ──
                VStack(spacing: 12) {
                    // Google Sign In Button
                    Button(action: performGoogleSignIn) {
                        HStack(spacing: 12) {
                            Text("G")
                                .font(.system(size: 18, weight: .black, design: .rounded))
                                .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))
                            Text("Continue with Google")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.black)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(Color.white)
                        .cornerRadius(16)
                        .shadow(color: Color.black.opacity(0.2), radius: 4, y: 2)
                    }
                    .buttonStyle(BounceButtonStyle())

                    // Apple Sign In Button
                    SignInWithAppleButton(
                        .continue,
                        onRequest: { request in
                            request.requestedScopes = [.fullName, .email]
                        },
                        onCompletion: { result in
                            handleAppleSignIn(result)
                        }
                    )
                    .signInWithAppleButtonStyle(.white)
                    .frame(height: 48)
                    .cornerRadius(16)

                    // Continue as Guest Button
                    Button(action: {
                        dismiss()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 13))
                            Text("Continue as Guest (1st Try Free)")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundColor(TryZonTheme.primaryGold)
                        .padding(.vertical, 8)
                    }
                }
                .padding(.horizontal, 4)

                // Divider Line
                HStack {
                    Rectangle().fill(Color.white.opacity(0.12)).frame(height: 1)
                    Text("OR EMAIL")
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(.white.opacity(0.4))
                    Rectangle().fill(Color.white.opacity(0.12)).frame(height: 1)
                }

                // ── 2. EMAIL & PASSWORD FORM ──
                VStack(spacing: 12) {
                    HStack {
                        Image(systemName: "envelope.fill")
                            .foregroundColor(TryZonTheme.primaryGold)
                        TextField("Email Address", text: $email)
                            .font(.system(size: 13))
                            .foregroundColor(.white)
                            .autocapitalization(.none)
                    }
                    .padding(14)
                    .background(TryZonTheme.surfaceVariant)
                    .cornerRadius(14)

                    HStack {
                        Image(systemName: "lock.fill")
                            .foregroundColor(TryZonTheme.primaryGold)
                        SecureField("Password", text: $password)
                            .font(.system(size: 13))
                            .foregroundColor(.white)
                    }
                    .padding(14)
                    .background(TryZonTheme.surfaceVariant)
                    .cornerRadius(14)
                }

                if let err = errorMessage {
                    Text(err)
                        .font(.caption.bold())
                        .foregroundColor(.red)
                }

                // Sign In CTA
                ShimmeringGoldButton(title: isLoading ? "LOGGING IN..." : "SIGN IN WITH EMAIL 🚀", subtitle: "Access Your Wardrobe & Credits") {
                    performLogin()
                }

                // Register Link
                Button(action: onNavigateToRegister) {
                    HStack {
                        Text("Don't have an account?")
                            .foregroundColor(.white.opacity(0.6))
                        Text("Register Now")
                            .foregroundColor(TryZonTheme.primaryGold)
                            .fontWeight(.bold)
                    }
                    .font(.system(size: 13))
                }

                Spacer(minLength: 20)
            }
            .padding(24)
        }
        .background(TryZonTheme.darkBackground.ignoresSafeArea())
    }

    private func performLogin() {
        guard !email.isEmpty, !password.isEmpty else {
            errorMessage = "Please enter both email and password."
            return
        }

        isLoading = true
        errorMessage = nil

        Task {
            do {
                _ = try await apiClient.login(email: email, password: password)
                DispatchQueue.main.async {
                    self.isLoading = false
                    dismiss()
                }
            } catch {
                DispatchQueue.main.async {
                    self.isLoading = false
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func performGoogleSignIn() {
        isLoading = true
        Task {
            // Simulated One-Tap Google Auth Sync
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            DispatchQueue.main.async {
                self.isLoading = false
                dismiss()
            }
        }
    }

    private func handleAppleSignIn(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success:
            dismiss()
        case .failure(let error):
            errorMessage = error.localizedDescription
        }
    }
}
