import SwiftUI

public struct FashionPreferencesView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage("user_gender") private var userGender: String = "Women"
    @AppStorage("preferred_style") private var preferredStyle: String = "Casual & Streetwear"

    private let genders: [(id: String, icon: String, title: String, desc: String)] = [
        ("Women", "person.fill", "Women's Fashion 👩", "Dresses, gowns, ethnic sarees, tops & accessories"),
        ("Men", "person.fill", "Men's Fashion 👨", "Suits, tuxedos, casual shirts, jackets & streetwear"),
        ("Unisex", "person.2.fill", "Unisex & All 👫", "All high-fashion catalog outfits for any gender")
    ]

    private let styles: [String] = [
        "Casual & Streetwear", "Executive & Formal Suits", "Royal Evening Couture", "Korean Minimalist", "Traditional & Ethnic", "Athletic & Gymwear"
    ]

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
                                Image(systemName: "hand.sparkles.fill")
                                    .font(.system(size: 34))
                                    .foregroundColor(TryZonTheme.primaryGold)
                            )

                        Text("Fashion Preferences")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))

                        Text("Tailor catalog recommendations and AI outfit fitting")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                    }

                    // Gender Preference Cards
                    VStack(alignment: .leading, spacing: 12) {
                        Text("PRIMARY OUTFIT CATEGORY")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(1)

                        VStack(spacing: 10) {
                            ForEach(genders, id: \.id) { g in
                                Button(action: { userGender = g.id }) {
                                    HStack(spacing: 14) {
                                        Circle()
                                            .fill(userGender == g.id ? TryZonTheme.primaryGold : Color.gray.opacity(0.2))
                                            .frame(width: 42, height: 42)
                                            .overlay(
                                                Image(systemName: g.icon)
                                                    .foregroundColor(userGender == g.id ? .black : TryZonTheme.textColor(for: colorScheme))
                                            )

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(g.title)
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                            Text(g.desc)
                                                .font(.system(size: 11))
                                                .foregroundColor(TryZonTheme.subtextColor(for: colorScheme))
                                        }

                                        Spacer()

                                        if userGender == g.id {
                                            Image(systemName: "checkmark.circle.fill")
                                                .font(.system(size: 20))
                                                .foregroundColor(TryZonTheme.primaryGold)
                                        }
                                    }
                                    .padding(14)
                                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                                    .cornerRadius(18)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 18)
                                            .stroke(userGender == g.id ? TryZonTheme.primaryGold : Color.clear, lineWidth: 2)
                                    )
                                }
                                .buttonStyle(BounceButtonStyle())
                            }
                        }
                    }

                    // Style Genre Preference
                    VStack(alignment: .leading, spacing: 12) {
                        Text("PREFERRED STYLE GENRES")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(TryZonTheme.primaryGold)
                            .tracking(1)

                        VStack(spacing: 8) {
                            ForEach(styles, id: \.self) { st in
                                Button(action: { preferredStyle = st }) {
                                    HStack {
                                        Text(st)
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(TryZonTheme.textColor(for: colorScheme))
                                        Spacer()
                                        if preferredStyle == st {
                                            Image(systemName: "checkmark")
                                                .font(.system(size: 13, weight: .bold))
                                                .foregroundColor(TryZonTheme.primaryGold)
                                        }
                                    }
                                    .padding(12)
                                    .background(TryZonTheme.surfaceVariantColor(for: colorScheme))
                                    .cornerRadius(14)
                                }
                            }
                        }
                    }

                    Spacer(minLength: 20)
                }
                .padding(20)
            }
            .navigationTitle("Fashion Preferences")
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
