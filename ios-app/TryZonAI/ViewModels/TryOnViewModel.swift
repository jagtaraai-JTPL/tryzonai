import SwiftUI
import Combine

public class TryOnViewModel: ObservableObject {
    @Published public var selectedPersonImage: UIImage? = nil
    @Published public var selectedGarmentImage: UIImage? = nil
    @Published public var selectedCategory: String = "Tops"
    @Published public var garmentStoreUrl: String = ""

    @Published public var isProcessing: Bool = false
    @Published public var activeSessionId: Int? = nil
    @Published public var completedStatusResponse: TryOnStatusResponse? = nil
    @Published public var errorMessage: String? = nil

    private let apiClient = APIClient.shared

    public init() {}

    public func reset() {
        selectedGarmentImage = nil
        garmentStoreUrl = ""
        isProcessing = false
        activeSessionId = nil
        completedStatusResponse = nil
        errorMessage = nil
    }

    public func startTryOn(productId: String? = nil, onSessionCreated: @escaping (Int) -> Void) async {
        guard let person = selectedPersonImage, let garment = selectedGarmentImage else {
            DispatchQueue.main.async {
                self.errorMessage = "Please upload both your photo and garment image."
            }
            return
        }

        DispatchQueue.main.async {
            self.isProcessing = true
            self.errorMessage = nil
        }

        do {
            let submission = try await apiClient.generateTryOn(
                personImage: person,
                garmentImage: garment,
                category: selectedCategory,
                productId: productId
            )

            DispatchQueue.main.async {
                self.activeSessionId = submission.session_id
                onSessionCreated(submission.session_id)
            }
        } catch {
            DispatchQueue.main.async {
                self.isProcessing = false
                self.errorMessage = error.localizedDescription
            }
        }
    }
}
