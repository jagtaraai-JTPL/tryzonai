import SwiftUI
import UIKit

public struct HomeScreen: View {
    @StateObject private var apiClient = APIClient.shared
    @State private var showingUploadSheet = false
    @State private var showingPremiumSheet = false

    public init() {}

    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // MARK: - Header & Credits Counter
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("TryZon AI")
                                .font(.system(size: 26, weight: .bold, design: .rounded))
                                .foregroundColor(.primary)
                            Text("Virtual Outfit Fitting Room")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }

                        Spacer()

                        // Free Daily Credits Badge
                        Button(action: { showingPremiumSheet = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "sparkles")
                                    .foregroundColor(.yellow)
                                Text("\(apiClient.userCredits) Credits")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.primary)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.yellow.opacity(0.15))
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(Color.yellow.opacity(0.5), lineWidth: 1)
                            )
                        }
                    }
                    .padding(.horizontal)

                    // MARK: - Launch Offer Banner
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("🔥 50% OFF LAUNCH OFFER")
                                .font(.system(size: 11, weight: .black))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(Color.yellow)
                                .foregroundColor(.black)
                                .cornerRadius(8)
                            Spacer()
                        }

                        Text("Try On Any Clothes Instantly!")
                            .font(.title3.bold())
                            .foregroundColor(.white)

                        Text("Upload your body photo & any garment picture to generate realistic AI fitting room results.")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))

                        Button(action: { showingUploadSheet = true }) {
                            HStack {
                                Image(systemName: "camera.fill")
                                Text("Start Virtual Try-On ⚡")
                                    .fontWeight(.bold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.yellow)
                            .foregroundColor(.black)
                            .cornerRadius(12)
                        }
                        .padding(.top, 6)
                    }
                    .padding(16)
                    .background(
                        LinearGradient(gradient: Gradient(colors: [Color.purple, Color.indigo]), startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .cornerRadius(20)
                    .padding(.horizontal)

                    // MARK: - Random Trial 🎲 Showcase
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("🎲 Try Random Outfit")
                                .font(.headline)
                            Spacer()
                            Text("1-Tap Try")
                                .font(.caption.bold())
                                .foregroundColor(.purple)
                        }

                        Text("Not sure what to try? Pick a random outfit from our trending catalog!")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        Button(action: { showingUploadSheet = true }) {
                            HStack {
                                Image(systemName: "die.face.5.fill")
                                Text("Surprise Me With an Outfit ✨")
                                    .font(.subheadline.bold())
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color.purple.opacity(0.12))
                            .foregroundColor(.purple)
                            .cornerRadius(12)
                        }
                    }
                    .padding(16)
                    .background(Color(UIColor.secondarySystemBackground))
                    .cornerRadius(16)
                    .padding(.horizontal)

                    // MARK: - How It Works Section
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Simple 3-Step Fitting")
                            .font(.headline)
                            .padding(.horizontal)

                        HStack(spacing: 12) {
                            StepCard(icon: "person.crop.square.fill", title: "1. Your Photo", desc: "Upload clear photo")
                            StepCard(icon: "tshirt.fill", title: "2. Garment", desc: "Any clothing image")
                            StepCard(icon: "sparkles", title: "3. Magic AI", desc: "Instant result")
                        }
                        .padding(.horizontal)
                    }

                    Spacer(minLength: 30)
                }
                .padding(.top, 10)
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showingUploadSheet) {
                TryOnUploadView()
            }
            .sheet(isPresented: $showingPremiumSheet) {
                PremiumView()
            }
        }
    }
}

struct StepCard: View {
    let icon: String
    let title: String
    let desc: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(.purple)
            Text(title)
                .font(.caption.bold())
            Text(desc)
                .font(.system(size: 10))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(12)
    }
}
