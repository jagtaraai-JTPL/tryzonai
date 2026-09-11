import SwiftUI

public struct LoginView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss

    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String? = nil

    let onNavigateToRegister: () -> Void

    public var body: some View {
        VStack(spacing: 24) {
            Spacer()

            // Header Branding
            VStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(TryZonTheme.primaryGold.opacity(0.15))
                        .frame(width: 70, height: 70)
                    Image(systemName: "sparkles")
                        .font(.system(size: 32))
                        .foregroundColor(TryZonTheme.primaryGold)
                }

                Text("Welcome Back ⚡")
                    .font(.system(size: 22, weight: .black, design: .rounded))
                    .foregroundColor(.white)

                Text("Sign in to your TryZon AI Virtual Fitting Room")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.7))
            }

            // Input Form
            VStack(spacing: 14) {
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
                .cornerRadius(12)

                HStack {
                    Image(systemName: "lock.fill")
                        .foregroundColor(TryZonTheme.primaryGold)
                    SecureField("Password", text: $password)
                        .font(.system(size: 13))
                        .foregroundColor(.white)
                }
                .padding(14)
                .background(TryZonTheme.surfaceVariant)
                .cornerRadius(12)
            }

            if let err = errorMessage {
                Text(err)
                    .font(.caption.bold())
                    .foregroundColor(.red)
            }

            // Sign In CTA
            ShimmeringGoldButton(title: isLoading ? "LOGGING IN..." : "SIGN IN TO TRYZON 🚀", subtitle: "Access Your Wardrobe & Credits") {
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

            Spacer()
        }
        .padding(24)
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
}
