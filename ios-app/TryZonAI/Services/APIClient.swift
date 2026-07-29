import Foundation
import Combine
#if canImport(UIKit)
import UIKit
#endif

public class APIClient: ObservableObject {
    public static let shared = APIClient()

    @Published public var currentUser: UserProfile?
    @Published public var userCredits: Int = 3
    @Published public var isLoading: Bool = false
    @Published public var errorMessage: String?

    private let baseURL = URL(string: "https://tryzonai.com/api")!
    private let session: URLSession

    private var sessionID: String {
        if let existing = UserDefaults.standard.string(forKey: "tryzon_session_id") {
            return existing
        }
        let newID = UUID().uuidString
        UserDefaults.standard.set(newID, forKey: "tryzon_session_id")
        return newID
    }

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: config)
        
        fetchUserCredits()
    }

    // MARK: - Headers
    private func makeHeaders() -> [String: String] {
        return [
            "Content-Type": "application/json",
            "X-Session-ID": sessionID,
            "Accept": "application/json"
        ]
    }

    // MARK: - User & Credits API
    public func fetchUserCredits() {
        var request = URLRequest(url: baseURL.appendingPathComponent("user/credits"))
        request.httpMethod = "GET"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        session.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let data = data,
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let credits = json["credits"] as? Int {
                    self?.userCredits = credits
                }
            }
        }.resume()
    }

    // MARK: - Multipart Image Upload for Try-On
    public func generateTryOn(personImage: UIImage, garmentImage: UIImage, category: String) async throws -> TryOnTaskResponse {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: baseURL.appendingPathComponent("try-on/generate"))
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.setValue(sessionID, forHTTPHeaderField: "X-Session-ID")

        var body = Data()
        
        // Append Category
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"category\"\r\n\r\n".data(using: .utf8)!)
        body.append("\(category)\r\n".data(using: .utf8)!)

        // Append Person Image
        if let personData = personImage.jpegData(compressionQuality: 0.85) {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"person_image\"; filename=\"person.jpg\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
            body.append(personData)
            body.append("\r\n".data(using: .utf8)!)
        }

        // Append Garment Image
        if let garmentData = garmentImage.jpegData(compressionQuality: 0.85) {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"garment_image\"; filename=\"garment.jpg\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
            body.append(garmentData)
            body.append("\r\n".data(using: .utf8)!)
        }

        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw NSError(domain: "APIClient", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to submit Try-On task"])
        }

        let decoder = JSONDecoder()
        let result = try decoder.decode(TryOnTaskResponse.self, from: data)
        
        // Update credits balance locally
        fetchUserCredits()
        return result
    }

    // MARK: - Task Status Polling
    public func pollTaskStatus(taskId: String) async throws -> TryOnTaskResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("try-on/status/\(taskId)"))
        request.httpMethod = "GET"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let (data, _) = try await session.data(for: request)
        let decoder = JSONDecoder()
        return try decoder.decode(TryOnTaskResponse.self, from: data)
    }
}
