import SwiftUI

public struct RegisterView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String? = nil

    let onNavigateToLogin: () -> Void

    public var body: some View {
        VStack(spacing: 24) {
            Spacer()

            VStack(spacing: 8) {
                Text("Create Account ✨")
                    .font(.system(size: 22, weight: .black, design: .rounded))
                    .foregroundColor(TryZonTheme.primaryGold)

                Text("Get 2 FREE Welcome Bonus Credits & Unlimited Closet")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.7))
            }

            VStack(spacing: 14) {
                HStack {
                    Image(systemName: "person.fill")
                        .foregroundColor(TryZonTheme.primaryGold)
                    TextField("Full Name", text: $name)
                        .font(.system(size: 13))
                        .foregroundColor(.white)
                }
                .padding(14)
                .background(TryZonTheme.surfaceVariant)
                .cornerRadius(12)

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
                    SecureField("Password (min 6 chars)", text: $password)
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

            Spacer()
        }
        .padding(24)
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
}
