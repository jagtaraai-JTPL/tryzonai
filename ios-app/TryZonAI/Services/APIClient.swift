import Foundation
import Combine
import UIKit

public class APIClient: ObservableObject {
    public static let shared = APIClient()

    @Published public var currentUser: UserProfile?
    @Published public var userCredits: Int = 1
    @Published public var paidCredits: Int = 0
    @Published public var bonusCredits: Int = 0
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

    public var isRealUser: Bool {
        guard let user = currentUser else { return false }
        let lower = user.email.lowercased()
        return !lower.contains("ios_guest_") && !lower.contains("guest_")
    }

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: config)

        if authToken != nil {
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
            "Accept": "application/json",
            "User-Agent": "TryZonAI/1.0 (iOS; AppStore)"
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
        // Guest user is not a real user, so keep isLoggedIn = false
        DispatchQueue.main.async {
            self.isLoggedIn = false
            AuthViewModel.shared.isLoggedIn = false
        }
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
                    self?.bonusCredits = user.bonusCredits
                    let isGuest = user.email.lowercased().contains("ios_guest_") || user.email.lowercased().contains("guest_")
                    let realLoggedIn = !isGuest
                    self?.isLoggedIn = realLoggedIn
                    AuthViewModel.shared.isLoggedIn = realLoggedIn
                    AuthViewModel.shared.currentUser = user
                } else {
                    self?.fetchUserCredits()
                }
            }
        }.resume()
    }

    public func fetchUserCredits() {
        guard authToken != nil else { return }
        var request = URLRequest(url: baseURL.appendingPathComponent("auth/me"))
        request.httpMethod = "GET"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        session.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                if let data = data,
                   let user = try? JSONDecoder().decode(UserProfile.self, from: data) {
                    self?.userCredits = user.credits
                    self?.paidCredits = user.paidCredits
                    self?.bonusCredits = user.bonusCredits
                }
            }
        }.resume()
    }

    public func claimRewardCredit(amount: Int = 1) async throws {
        let count = max(1, amount)
        for _ in 0..<count {
            var request = URLRequest(url: baseURL.appendingPathComponent("tryon/claim-reward-credit"))
            request.httpMethod = "POST"
            makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

            if let (data, _) = try? await session.data(for: request),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let newCredits = json["credits"] as? Int {
                DispatchQueue.main.async {
                    self.userCredits = newCredits
                }
            } else {
                DispatchQueue.main.async {
                    self.userCredits += 1
                }
            }
        }
        fetchUserProfile()
    }

    // MARK: - Auth API
    public func loginWithGoogle(idToken: String) async throws -> AuthResponse {
        let cleanInput = idToken.trimmingCharacters(in: .whitespacesAndNewlines)
        var request = URLRequest(url: baseURL.appendingPathComponent("auth/google"))
        request.httpMethod = "POST"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let bodyData = try JSONSerialization.data(withJSONObject: ["id_token": cleanInput])
        request.httpBody = bodyData

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 401
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let detail = json["detail"] as? String {
                throw NSError(domain: "APIClient", code: statusCode, userInfo: [NSLocalizedDescriptionKey: detail])
            }
            throw NSError(domain: "APIClient", code: statusCode, userInfo: [NSLocalizedDescriptionKey: "Google Sign In failed. Please try again."])
        }

        let authRes = try JSONDecoder().decode(AuthResponse.self, from: data)
        let isGuest = authRes.user.email.lowercased().contains("ios_guest_") || authRes.user.email.lowercased().contains("guest_")
        let realLoggedIn = !isGuest

        DispatchQueue.main.async {
            self.authToken = authRes.token
            self.currentUser = authRes.user
            self.userCredits = authRes.user.credits
            self.paidCredits = authRes.user.paidCredits
            self.isLoggedIn = realLoggedIn
            AuthViewModel.shared.isLoggedIn = realLoggedIn
            AuthViewModel.shared.currentUser = authRes.user
        }
        return authRes
    }

    public func loginWithApple(email: String? = nil, name: String? = nil, identityToken: String? = nil) async throws -> AuthResponse {
        var cleanEmail = (email != nil && !email!.isEmpty) ? email! : ""
        let cleanName = (name != nil && !name!.isEmpty) ? name! : "Apple User"

        if !cleanEmail.isEmpty {
            UserDefaults.standard.set(cleanEmail, forKey: "saved_apple_email")
        } else if let savedEmail = UserDefaults.standard.string(forKey: "saved_apple_email"), !savedEmail.isEmpty {
            cleanEmail = savedEmail
        }

        var request = URLRequest(url: baseURL.appendingPathComponent("auth/apple"))
        request.httpMethod = "POST"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        var bodyObj: [String: Any] = [
            "name": cleanName
        ]
        if !cleanEmail.isEmpty {
            bodyObj["email"] = cleanEmail
        }
        if let token = identityToken, !token.isEmpty {
            bodyObj["id_token"] = token
        } else if !cleanEmail.isEmpty {
            bodyObj["id_token"] = cleanEmail
        }

        request.httpBody = try JSONSerialization.data(withJSONObject: bodyObj)

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 401
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let detail = json["detail"] as? String {
                throw NSError(domain: "APIClient", code: statusCode, userInfo: [NSLocalizedDescriptionKey: detail])
            }
            throw NSError(domain: "APIClient", code: statusCode, userInfo: [NSLocalizedDescriptionKey: "Apple Sign In failed. Please try again."])
        }

        let authRes = try JSONDecoder().decode(AuthResponse.self, from: data)
        DispatchQueue.main.async {
            self.authToken = authRes.token
            self.currentUser = authRes.user
            self.userCredits = authRes.user.credits
            self.paidCredits = authRes.user.paidCredits
            self.isLoggedIn = true
            AuthViewModel.shared.isLoggedIn = true
            AuthViewModel.shared.currentUser = authRes.user
        }
        if !authRes.user.email.isEmpty {
            UserDefaults.standard.set(authRes.user.email, forKey: "saved_apple_email")
        }
        return authRes
    }

    public func login(email: String, password: String) async throws -> AuthResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("auth/login"))
        request.httpMethod = "POST"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let bodyData = try JSONSerialization.data(withJSONObject: ["email": cleanEmail, "password": password])
        request.httpBody = bodyData

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 401
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let detail = json["detail"] as? String {
                throw NSError(domain: "APIClient", code: statusCode, userInfo: [NSLocalizedDescriptionKey: detail])
            }
            throw NSError(domain: "APIClient", code: 401, userInfo: [NSLocalizedDescriptionKey: "Invalid email or password"])
        }

        let authRes = try JSONDecoder().decode(AuthResponse.self, from: data)
        let isGuest = authRes.user.email.lowercased().contains("ios_guest_") || authRes.user.email.lowercased().contains("guest_")
        let realLoggedIn = !isGuest

        DispatchQueue.main.async {
            self.authToken = authRes.token
            self.currentUser = authRes.user
            self.userCredits = authRes.user.credits
            self.paidCredits = authRes.user.paidCredits
            self.isLoggedIn = realLoggedIn
            AuthViewModel.shared.isLoggedIn = realLoggedIn
            AuthViewModel.shared.currentUser = authRes.user
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
        let isGuest = authRes.user.email.lowercased().contains("ios_guest_") || authRes.user.email.lowercased().contains("guest_")
        let realLoggedIn = !isGuest

        DispatchQueue.main.async {
            self.authToken = authRes.token
            self.currentUser = authRes.user
            self.userCredits = authRes.user.credits
            self.paidCredits = authRes.user.paidCredits
            self.isLoggedIn = realLoggedIn
            AuthViewModel.shared.isLoggedIn = realLoggedIn
            AuthViewModel.shared.currentUser = authRes.user
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
        request.setValue("TryZonAI/1.0 (iOS; AppStore)", forHTTPHeaderField: "User-Agent")
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

    // MARK: - Wardrobe Closet API
    public func fetchWardrobeItems() async throws -> [WardrobeItemModel] {
        var request = URLRequest(url: baseURL.appendingPathComponent("wardrobe"))
        request.httpMethod = "GET"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            return []
        }
        let decoder = JSONDecoder()
        return try decoder.decode([WardrobeItemModel].self, from: data)
    }

    public func deleteWardrobeItem(id: Int) async throws {
        var request = URLRequest(url: baseURL.appendingPathComponent("wardrobe/\(id)"))
        request.httpMethod = "DELETE"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        _ = try await session.data(for: request)
    }

    public func addToWardrobe(imageUrl: String) async throws -> WardrobeItemModel {
        var components = URLComponents(url: baseURL.appendingPathComponent("wardrobe"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "image_url", value: imageUrl)]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "POST"
        makeHeaders().forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

        let (data, _) = try await session.data(for: request)
        let decoder = JSONDecoder()
        return try decoder.decode(WardrobeItemModel.self, from: data)
    }

    // MARK: - Auth & Account Management
    public func logout() {
        UserDefaults.standard.removeObject(forKey: "tryzon_session_id")
        UserDefaults.standard.removeObject(forKey: "auth_token")
        UserDefaults.standard.removeObject(forKey: "saved_apple_email")
        authToken = nil
        currentUser = nil
        isLoggedIn = false
        userCredits = 1
        paidCredits = 0
        AuthViewModel.shared.isLoggedIn = false
        AuthViewModel.shared.currentUser = nil
    }

    public func deleteAccount() {
        logout()
    }
}
