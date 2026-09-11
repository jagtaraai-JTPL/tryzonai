import SwiftUI
import AuthenticationServices

public struct RegisterView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String? = nil

    let onNavigateToLogin: () -> Void

    public init(apiClient: APIClient, onNavigateToLogin: @escaping () -> Void) {
        self.apiClient = apiClient
        self.onNavigateToLogin = onNavigateToLogin
    }

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 20) {
                Spacer(minLength: 20)

                VStack(spacing: 8) {
                    Text("Create Account ✨")
                        .font(.system(size: 22, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.primaryGold)

                    Text("Get 2 FREE Welcome Bonus Credits & Unlimited Closet")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }

                // ── 1. SOCIAL QUICK SIGN UP (Google & Apple) ──
                VStack(spacing: 12) {
                    Button(action: performGoogleSignUp) {
                        HStack(spacing: 12) {
                            Text("G")
                                .font(.system(size: 18, weight: .black, design: .rounded))
                                .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))
                            Text("Sign up with Google")
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

                    SignInWithAppleButton(
                        .signUp,
                        onRequest: { request in
                            request.requestedScopes = [.fullName, .email]
                        },
                        onCompletion: { result in
                            handleAppleSignUp(result)
                        }
                    )
                    .signInWithAppleButtonStyle(.white)
                    .frame(height: 48)
                    .cornerRadius(16)
                }

                // Divider Line
                HStack {
                    Rectangle().fill(Color.white.opacity(0.12)).frame(height: 1)
                    Text("OR EMAIL REGISTER")
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(.white.opacity(0.4))
                    Rectangle().fill(Color.white.opacity(0.12)).frame(height: 1)
                }

                // ── 2. FORM INPUTS ──
                VStack(spacing: 12) {
                    HStack {
                        Image(systemName: "person.fill")
                            .foregroundColor(TryZonTheme.primaryGold)
                        TextField("Full Name", text: $name)
                            .font(.system(size: 13))
                            .foregroundColor(.white)
                    }
                    .padding(14)
                    .background(TryZonTheme.surfaceVariant)
                    .cornerRadius(14)

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
                        SecureField("Password (min 6 chars)", text: $password)
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

                ShimmeringGoldButton(title: isLoading ? "CREATING ACCOUNT..." : "REGISTER FOR FREE 🚀", subtitle: "Claim 2 Welcome Bonus Credits") {
                    performRegister()
                }

                Button(action: onNavigateToLogin) {
                    HStack {
                        Text("Already have an account?")
                            .foregroundColor(.white.opacity(0.6))
                        Text("Sign In")
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

    private func performRegister() {
        guard !name.isEmpty, !email.isEmpty, !password.isEmpty else {
            errorMessage = "Please fill in all fields."
            return
        }

        isLoading = true
        errorMessage = nil

        Task {
            do {
                _ = try await apiClient.register(name: name, email: email, password: password)
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

    private func performGoogleSignUp() {
        isLoading = true
        errorMessage = nil
        Task {
            do {
                _ = try await apiClient.loginWithGoogle()
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

    private func handleAppleSignUp(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let auth):
            isLoading = true
            var userEmail: String? = nil
            var userName: String? = nil
            if let appleIDCredential = auth.credential as? ASAuthorizationAppleIDCredential {
                userEmail = appleIDCredential.email
                if let name = appleIDCredential.fullName {
                    userName = "\(name.givenName ?? "") \(name.familyName ?? "")".trimmingCharacters(in: .whitespaces)
                }
            }
            Task {
                do {
                    _ = try await apiClient.loginWithApple(email: userEmail, name: userName)
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
        case .failure(let error):
            errorMessage = error.localizedDescription
        }
    }
}
