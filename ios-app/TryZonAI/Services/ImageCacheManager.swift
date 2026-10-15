//
//  ImageCacheManager.swift
//  TryZonAI — High-Performance In-Memory + Disk Caching System
//

import SwiftUI
import UIKit

// MARK: - Thread-Safe Image Cache Manager
public final class ImageCacheManager {
    public static let shared = ImageCacheManager()

    private let memoryCache = NSCache<NSString, UIImage>()
    private var inFlightTasks: [String: Task<UIImage?, Never>] = [:]
    private let lock = NSLock()

    private init() {
        // High performance limits: 250 images, 120MB memory limit
        memoryCache.countLimit = 250
        memoryCache.totalCostLimit = 120 * 1024 * 1024
    }

    public func image(for key: String) -> UIImage? {
        lock.lock()
        defer { lock.unlock() }
        return memoryCache.object(forKey: key as NSString)
    }

    public func cacheImage(_ image: UIImage, for key: String) {
        lock.lock()
        defer { lock.unlock() }
        let cost = Int(image.size.width * image.size.height * 4)
        memoryCache.setObject(image, forKey: key as NSString, cost: cost)
    }

    public func loadImage(from url: URL) async -> UIImage? {
        let key = url.absoluteString

        if let cached = image(for: key) {
            return cached
        }

        // Deduplicate simultaneous requests for identical URLs
        lock.lock()
        if let existingTask = inFlightTasks[key] {
            lock.unlock()
            return await existingTask.value
        }

        let task = Task<UIImage?, Never> {
            do {
                var request = URLRequest(url: url)
                request.cachePolicy = .returnCacheDataElseLoad
                request.timeoutInterval = 25

                let (data, response) = try await URLSession.shared.data(for: request)
                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode),
                      let image = UIImage(data: data) else {
                    return nil
                }

                self.cacheImage(image, for: key)
                return image
            } catch {
                return nil
            }
        }

        inFlightTasks[key] = task
        lock.unlock()

        let result = await task.value

        lock.lock()
        inFlightTasks.removeValue(forKey: key)
        lock.unlock()

        return result
    }

    public func clearCache() {
        lock.lock()
        defer { lock.unlock() }
        memoryCache.removeAllObjects()
    }
}

// MARK: - High-Performance Cached Async Image View
public struct CachedAsyncImage<Content: View, Placeholder: View>: View {
    let url: URL?
    let content: ((Image) -> Content)?
    let placeholder: (() -> Placeholder)?
    let phaseContent: ((AsyncImagePhase) -> Content)?

    @State private var loadedImage: UIImage?
    @State private var hasFailed: Bool = false

    public init(
        url: URL?,
        @ViewBuilder content: @escaping (Image) -> Content,
        @ViewBuilder placeholder: @escaping () -> Placeholder
    ) {
        self.url = url
        self.content = content
        self.placeholder = placeholder
        self.phaseContent = nil
    }

    public var body: some View {
        Group {
            if let phaseContent = phaseContent {
                if let img = loadedImage {
                    phaseContent(.success(Image(uiImage: img)))
                } else if hasFailed {
                    phaseContent(.failure(URLError(.cannotDecodeContentData)))
                } else {
                    phaseContent(.empty)
                        .task(id: url?.absoluteString) {
                            await fetchImage()
                        }
                }
            } else {
                if let img = loadedImage, let content = content {
                    content(Image(uiImage: img))
                } else if let placeholder = placeholder {
                    placeholder()
                        .task(id: url?.absoluteString) {
                            await fetchImage()
                        }
                }
            }
        }
    }

    private func fetchImage() async {
        guard let url = url else {
            await MainActor.run { hasFailed = true }
            return
        }
        if let cached = ImageCacheManager.shared.image(for: url.absoluteString) {
            await MainActor.run { self.loadedImage = cached }
            return
        }
        let downloaded = await ImageCacheManager.shared.loadImage(from: url)
        await MainActor.run {
            if let downloaded = downloaded {
                self.loadedImage = downloaded
            } else {
                self.hasFailed = true
            }
        }
    }
}

extension CachedAsyncImage where Placeholder == EmptyView {
    public init(
        url: URL?,
        @ViewBuilder content: @escaping (AsyncImagePhase) -> Content
    ) {
        self.url = url
        self.content = nil
        self.placeholder = nil
        self.phaseContent = content
    }
}
