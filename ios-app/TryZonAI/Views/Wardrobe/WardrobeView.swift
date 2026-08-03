import SwiftUI
import UIKit

public struct WardrobeView: View {
    public init() {}

    public var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                Spacer()

                Image(systemName: "tshirt.fill")
                    .font(.system(size: 64))
                    .foregroundColor(.purple.opacity(0.6))

                Text("Your Virtual Wardrobe")
                    .font(.title2.bold())

                Text("Saved try-on results and outfits will automatically appear here.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                Spacer()
            }
            .navigationTitle("My Wardrobe")
        }
    }
}
