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
        // High performance limits: 150 images, 80MB memory limit
        memoryCache.countLimit = 150
        memoryCache.totalCostLimit = 80 * 1024 * 1024

        NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.clearCache()
        }
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
                }
            } else {
                if let img = loadedImage, let content = content {
                    content(Image(uiImage: img))
                } else if let placeholder = placeholder {
                    placeholder()
                }
            }
        }
        .onAppear {
            if loadedImage == nil, let url = url, let cached = ImageCacheManager.shared.image(for: url.absoluteString) {
                self.loadedImage = cached
            }
        }
        .task(id: url?.absoluteString) {
            if loadedImage == nil {
                await fetchImage()
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

// MARK: - High-Performance UIImage Optimization Extensions
extension UIImage {
    /// Normalizes orientation and downscales image to fit within maxDimension preserving aspect ratio.
    public func optimizedForUpload(maxDimension: CGFloat = 1920) -> UIImage {
        let maxCurrent = max(size.width, size.height)
        let scale = maxCurrent > maxDimension ? (maxDimension / maxCurrent) : 1.0
        let targetSize = CGSize(width: size.width * scale, height: size.height * scale)

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        return renderer.image { _ in
            self.draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }

    /// Crops the image to a target aspect ratio (default 9:16) centered on the photo, orientation-safe.
    public func centerCroppedToAspect(targetRatio: CGFloat = 9.0 / 16.0) -> UIImage {
        let normalized = self.optimizedForUpload(maxDimension: 1920)
        let currentRatio = normalized.size.width / normalized.size.height
        let cropRect: CGRect

        if currentRatio > targetRatio {
            let newWidth = normalized.size.height * targetRatio
            let xOffset = (normalized.size.width - newWidth) / 2.0
            cropRect = CGRect(x: xOffset, y: 0, width: newWidth, height: normalized.size.height)
        } else {
            let newHeight = normalized.size.width / targetRatio
            let yOffset = (normalized.size.height - newHeight) / 2.0
            cropRect = CGRect(x: 0, y: yOffset, width: normalized.size.width, height: newHeight)
        }

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        let renderer = UIGraphicsImageRenderer(size: cropRect.size, format: format)
        return renderer.image { _ in
            normalized.draw(at: CGPoint(x: -cropRect.origin.x, y: -cropRect.origin.y))
        }
    }
}
