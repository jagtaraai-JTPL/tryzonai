import Foundation
import Combine
import UIKit

public class APIClient: ObservableObject {
    public static let shared = APIClient()

    @Published public var currentUser: UserProfile?
    @Published public var userCredits: Int = 3
    @Published public var paidCredits: Int = 0
    @Published public var isLoggedIn: Bool = false
    @Published public var isLoading: Bool = false
    @Published public var errorMessage: String?

    // CRITICAL FIX: Backend router prefix is /api/v1
    private let baseURL = URL(string: "https://tryzonai.com/api/v1")!
    private let session: URLSession

    public var sessionID: String {
        if let existing = UserDefaults.standard.string(forKey: "tryzon_session_id") {
            return existing
        }
        let newID = UUID().uuidString
        UserDefaults.standard.set(newID, forKey: "tryzon_session_id")
        return newID
    }

    public var deviceID: String {
        if let existing = UserDefaults.standard.string(forKey: "tryzon_device_id") {
            return existing
        }
        let newID = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        UserDefaults.standard.set(newID, forKey: "tryzon_device_id")
        return newID
    }

    public var authToken: String? {
        get { UserDefaults.standard.string(forKey: "auth_token") }
        set {
            if let val = newValue {
                UserDefaults.standard.set(val, forKey: "auth_token")
            } else {
                UserDefaults.standard.removeObject(forKey: "auth_token")
            }
        }
    }

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: config)

        if authToken != nil {
            isLoggedIn = true
            fetchUserProfile()
        } else {
            fetchUserCredits()
        }
    }

    // MARK: - Headers
    public func makeHeaders() -> [String: String] {
        var headers: [String: String] = [
            "Content-Type": "application/json",
            "X-Session-ID": sessionID,
            "X-Device-ID": deviceID,
            "Accept": "application/json"
        ]
        if let token = authToken {
            headers["Authorization"] = "Bearer \(token)"
        }
        return headers
    }

    // MARK: - Ensure Auth Token for Guests
    public func ensureSessionAuthToken() async throws -> String {
        if let token = authToken, !token.isEmpty {
            return token
        }

        // Register anonymous session token so guest try-on succeeds seamlessly
        let anonId = String(UUID().uuidString.prefix(8))
        let email = "ios_guest_\(anonId)@tryzon.ai"
        let name = "iOS Guest"
        let pass = "GuestPass123!"

        let res = try await register(name: name, email: email, password: pass)
        return res.token
    }

    // MARK: - User Profile & Credits API
    public func fetchUserProfile() {
        guard authToken != nil else {
            fetchUserCredits()
            return
        }

        var request = URLRequest(url: baseURL.appendingPathComponent("auth/me"))
        request.httpMethod = "GET"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        session.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let data = data,
                   let user = try? JSONDecoder().decode(UserProfile.self, from: data) {
                    self?.currentUser = user
                    self?.userCredits = user.credits
                    self?.paidCredits = user.paidCredits
                    self?.isLoggedIn = true
                } else {
                    self?.fetchUserCredits()
                }
            }
        }.resume()
    }

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

    // MARK: - Auth API
    public func login(email: String, password: String) async throws -> AuthResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("auth/login"))
        request.httpMethod = "POST"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let bodyData = try JSONSerialization.data(withJSONObject: ["email": cleanEmail, "password": password])
        request.httpBody = bodyData

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let detail = json["detail"] as? String {
                throw NSError(domain: "APIClient", code: (response as? HTTPURLResponse)?.statusCode ?? 401, userInfo: [NSLocalizedDescriptionKey: detail])
            }
            throw NSError(domain: "APIClient", code: 401, userInfo: [NSLocalizedDescriptionKey: "Invalid email or password"])
        }

        let authRes = try JSONDecoder().decode(AuthResponse.self, from: data)
        DispatchQueue.main.async {
            self.authToken = authRes.token
            self.currentUser = authRes.user
            self.userCredits = authRes.user.credits
            self.paidCredits = authRes.user.paidCredits
            self.isLoggedIn = true
        }
        return authRes
    }

    public func register(name: String, email: String, password: String) async throws -> AuthResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("auth/register"))
        request.httpMethod = "POST"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let bodyData = try JSONSerialization.data(withJSONObject: ["name": name, "email": cleanEmail, "password": password])
        request.httpBody = bodyData

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let detail = json["detail"] as? String {
                throw NSError(domain: "APIClient", code: (response as? HTTPURLResponse)?.statusCode ?? 400, userInfo: [NSLocalizedDescriptionKey: detail])
            }
            throw NSError(domain: "APIClient", code: 400, userInfo: [NSLocalizedDescriptionKey: "Registration failed. Email might already exist."])
        }

        let authRes = try JSONDecoder().decode(AuthResponse.self, from: data)
        DispatchQueue.main.async {
            self.authToken = authRes.token
            self.currentUser = authRes.user
            self.userCredits = authRes.user.credits
            self.paidCredits = authRes.user.paidCredits
            self.isLoggedIn = true
        }
        return authRes
    }

    // MARK: - Multipart Image Upload for Try-On
    public func generateTryOn(personImage: UIImage, garmentImage: UIImage, category: String, productId: String? = nil) async throws -> TryOnSubmissionResponse {
        let token = try await ensureSessionAuthToken()

        var tryonURL = baseURL.appendingPathComponent("tryon")
        if let pid = productId, !pid.isEmpty {
            var components = URLComponents(url: tryonURL, resolvingAgainstBaseURL: false)!
            components.queryItems = [URLQueryItem(name: "product_id", value: pid)]
            tryonURL = components.url!
        }

        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: tryonURL)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.setValue(sessionID, forHTTPHeaderField: "X-Session-ID")
        request.setValue(deviceID, forHTTPHeaderField: "X-Device-ID")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        var body = Data()

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
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let detail = json["detail"] as? String {
                throw NSError(domain: "APIClient", code: (response as? HTTPURLResponse)?.statusCode ?? 500, userInfo: [NSLocalizedDescriptionKey: detail])
            }
            throw NSError(domain: "APIClient", code: 500, userInfo: [NSLocalizedDescriptionKey: "Failed to submit Try-On task"])
        }

        let decoder = JSONDecoder()
        let result = try decoder.decode(TryOnSubmissionResponse.self, from: data)

        if let rem = result.credits_remaining {
            DispatchQueue.main.async {
                self.userCredits = rem
            }
        }
        return result
    }

    // MARK: - Task Status Polling
    public func pollTaskStatus(sessionId: Int) async throws -> TryOnStatusResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("tryon/status/\(sessionId)"))
        request.httpMethod = "GET"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw NSError(domain: "APIClient", code: 404, userInfo: [NSLocalizedDescriptionKey: "Task status not found"])
        }

        let decoder = JSONDecoder()
        return try decoder.decode(TryOnStatusResponse.self, from: data)
    }

    // MARK: - Catalog API
    public func fetchCatalog(category: String? = nil, gender: String? = nil, search: String? = nil) async throws -> [CatalogItem] {
        var components = URLComponents(url: baseURL.appendingPathComponent("catalog"), resolvingAgainstBaseURL: false)!
        var queryItems: [URLQueryItem] = [URLQueryItem(name: "limit", value: "30")]
        if let cat = category, cat != "All" {
            queryItems.append(URLQueryItem(name: "category", value: cat))
        }
        if let g = gender, g != "All" {
            queryItems.append(URLQueryItem(name: "gender", value: g))
        }
        if let q = search, !q.isEmpty {
            queryItems.append(URLQueryItem(name: "search", value: q))
        }
        components.queryItems = queryItems

        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let (data, _) = try await session.data(for: request)
        let decoder = JSONDecoder()
        let res = try decoder.decode(CatalogResponse.self, from: data)
        return res.products
    }

    // MARK: - History API
    public func fetchHistory() async throws -> [TryOnHistoryItem] {
        var request = URLRequest(url: baseURL.appendingPathComponent("tryon/history"))
        request.httpMethod = "GET"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let (data, _) = try await session.data(for: request)
        let decoder = JSONDecoder()
        return try decoder.decode([TryOnHistoryItem].self, from: data)
    }

    // MARK: - Auth & Account Management
    public func logout() {
        UserDefaults.standard.removeObject(forKey: "tryzon_session_id")
        UserDefaults.standard.removeObject(forKey: "auth_token")
        authToken = nil
        currentUser = nil
        isLoggedIn = false
        userCredits = 1
        paidCredits = 0
    }

    public func deleteAccount() {
        logout()
    }
}
