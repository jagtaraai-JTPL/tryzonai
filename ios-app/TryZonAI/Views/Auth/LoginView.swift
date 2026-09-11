import SwiftUI
import AuthenticationServices

public struct LoginView: View {
    @ObservedObject var apiClient: APIClient
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var currentHeroIndex = 0

    let onNavigateToRegister: () -> Void

    private let heroOutfits: [(String, String)] = [
        ("https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_mens_italian_riviera_linen_suit.webp", "Monaco Atelier Linen Suit 🇲🇨"),
        ("https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_formal_ceo_black_suit_1778055862174.webp", "New York Executive Tuxedo 🇺🇸"),
        ("https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_women_teal_knit_dress.webp", "Seoul K-Style Minimalist 🇰🇷"),
        ("https://tryzonai.com/api/v1/outfits/premium_catalog/ai_premium_outfit_royal_queen_emerald_gold_1778038089053.webp", "Dubai Royal Velvet Couture 🇦🇪"),
    ]

    public init(apiClient: APIClient, onNavigateToRegister: @escaping () -> Void) {
        self.apiClient = apiClient
        self.onNavigateToRegister = onNavigateToRegister
    }

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 18) {
                Spacer(minLength: 10)

                // ── 1. DOMINANT HERO FASHION CANVAS (Matching Android Rounded 30dp) ──
                ZStack(alignment: .bottomLeading) {
                    AsyncImage(url: URL(string: heroOutfits[currentHeroIndex].0)) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            TryZonTheme.surfaceVariantColor(for: colorScheme)
                        }
                    }
                    .frame(height: 220)
                    .frame(maxWidth: .infinity)
                    .clipped()

                    LinearGradient(
                        colors: [Color.clear, Color.black.opacity(0.75)],
                        startPoint: .top,
                        endPoint: .bottom
                    )

                    VStack(alignment: .leading, spacing: 3) {
                        Text(heroOutfits[currentHeroIndex].1)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                        Text("AI Virtual Outfit Fitting • 1 Free Try Daily")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.white.opacity(0.8))
                    }
                    .padding(16)
                }
                .frame(height: 220)
                .cornerRadius(30)
                .clipped()
                .shadow(color: Color.black.opacity(0.15), radius: 8, y: 4)

                // ── 2. HEADER BRANDING & DYNAMIC LOGO ──
                VStack(spacing: 6) {
                    DynamicAppLogo(width: 56, height: 56)
                        .shadow(color: TryZonTheme.primaryGold.opacity(0.3), radius: 8)

                    Text("Enter Your Virtual Studio")
                        .font(.system(size: 22, weight: .black, design: .rounded))
                        .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                    Text("Your private wardrobe, one tap away.")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        .multilineTextAlignment(.center)
                }

                if let err = errorMessage {
                    Text(err)
                        .font(.caption.bold())
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                }

                // ── 3. FLOATING ROUND PILL SOCIAL LOGIN BUTTONS ──
                VStack(spacing: 12) {
                    // Google Pill Button (Gol shape height 50)
                    Button(action: {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        performGoogleSignIn()
                    }) {
                        HStack(spacing: 10) {
                            if isLoading {
                                ProgressView()
                                    .tint(colorScheme == .dark ? .black : .primary)
                                Text("Signing in...")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(colorScheme == .dark ? .black : .primary)
                            } else {
                                Text("G")
                                    .font(.system(size: 20, weight: .black, design: .rounded))
                                    .foregroundColor(Color(red: 66/255, green: 133/255, blue: 244/255))
                                Text("Continue with Google")
                                    .font(.system(size: 14.5, weight: .bold))
                                    .foregroundColor(colorScheme == .dark ? .black : .primary)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(colorScheme == .dark ? Color.white : Color(.systemBackground))
                        .clipShape(Capsule())
                        .contentShape(Capsule())
                        .shadow(color: Color.black.opacity(0.12), radius: 6, y: 3)
                        .overlay(
                            Capsule().stroke(Color.primary.opacity(0.1), lineWidth: 1)
                        )
                    }
                    .buttonStyle(PlainButtonStyle())

                    // Apple Pill Button (Gol shape height 50)
                    SignInWithAppleButton(
                        .continue,
                        onRequest: { request in
                            request.requestedScopes = [.fullName, .email]
                        },
                        onCompletion: { result in
                            handleAppleSignIn(result)
                        }
                    )
                    .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
                    .frame(height: 50)
                    .clipShape(Capsule())
                    .contentShape(Capsule())

                    // Guest Login Link
                    Button(action: { dismiss() }) {
                        HStack(spacing: 6) {
                            Image(systemName: "sparkles")
                                .font(.system(size: 13))
                            Text("✦ 1 free try-on every day · No card required")
                                .font(.system(size: 11.5, weight: .semibold))
                        }
                        .foregroundColor(TryZonTheme.primaryGold)
                        .padding(.vertical, 6)
                        .contentShape(Rectangle())
                    }
                }
                .padding(.horizontal, 8)

                // Divider Line
                HStack {
                    Rectangle().fill(TryZonTheme.textColor(for: colorScheme).opacity(0.12)).frame(height: 1)
                    Text("OR EMAIL")
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                    Rectangle().fill(TryZonTheme.textColor(for: colorScheme).opacity(0.12)).frame(height: 1)
                }

                // ── 4. EMAIL & PASSWORD FORM (Pill Curved inputs) ──
                VStack(spacing: 10) {
                    HStack {
                        Image(systemName: "envelope.fill")
                            .foregroundColor(TryZonTheme.primaryGold)
                        TextField("Email Address", text: $email)
                            .font(.system(size: 13))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                            .autocapitalization(.none)
                    }
                    .padding(14)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .clipShape(Capsule())

                    HStack {
                        Image(systemName: "lock.fill")
                            .foregroundColor(TryZonTheme.primaryGold)
                        SecureField("Password", text: $password)
                            .font(.system(size: 13))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                    }
                    .padding(14)
                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                    .clipShape(Capsule())
                }

                // Sign In CTA Button (Pill shape)
                Button(action: performLogin) {
                    HStack {
                        if isLoading {
                            ProgressView().tint(.black)
                        } else {
                            Text("SIGN IN WITH EMAIL 🚀")
                                .font(.system(size: 14, weight: .black, design: .rounded))
                                .foregroundColor(.black)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(TryZonTheme.primaryGold)
                    .clipShape(Capsule())
                    .shadow(color: TryZonTheme.primaryGold.opacity(0.4), radius: 8, y: 3)
                }
                .buttonStyle(BounceButtonStyle())

                // Register Link
                Button(action: onNavigateToRegister) {
                    HStack {
                        Text("Don't have an account?")
                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                        Text("Register Now")
                            .foregroundColor(TryZonTheme.primaryGold)
                            .fontWeight(.bold)
                    }
                    .font(.system(size: 13))
                }

                Spacer(minLength: 20)
            }
            .padding(20)
        }
        .background(TryZonTheme.backgroundColor(for: colorScheme).ignoresSafeArea())
        .onAppear {
            Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { _ in
                withAnimation(.easeInOut(duration: 0.6)) {
                    currentHeroIndex = (currentHeroIndex + 1) % heroOutfits.count
                }
            }
        }
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
        errorMessage = nil
        Task {
            do {
                _ = try await apiClient.loginWithGoogle()
                DispatchQueue.main.async {
                    self.isLoading = false
                    AuthViewModel.shared.isLoggedIn = true
                    AuthViewModel.shared.currentUser = self.apiClient.currentUser
                    dismiss()
                }
            } catch {
                DispatchQueue.main.async {
                    self.isLoading = false
                    self.errorMessage = "Sign in failed: \(error.localizedDescription)"
                }
            }
        }
    }

    private func handleAppleSignIn(_ result: Result<ASAuthorization, Error>) {
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
                        self.errorMessage = "Apple Sign In failed: \(error.localizedDescription)"
                    }
                }
            }
        case .failure(let error):
            errorMessage = error.localizedDescription
        }
    }
}
