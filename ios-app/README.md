# TryZon AI - Native iOS Application (SwiftUI)

This directory contains the 100% native iOS application for **TryZon AI**, built using **SwiftUI**, **StoreKit 2**, and `UNUserNotificationCenter`.

It operates as a completely decoupled codebase from `android-app/`, sharing the exact same backend API endpoints at `https://tryzonai.com/api`.

---

## 📱 App Architecture

```
ios-app/
└── TryZonAI/
    ├── App/
    │   └── TryZonAIApp.swift            # SwiftUI Main WindowGroup & Lifecycle
    ├── Services/
    │   ├── APIClient.swift             # URLSession Multipart API & Task Polling
    │   ├── StoreKitManager.swift       # Apple StoreKit 2 Subscriptions & Credits
    │   └── NotificationManager.swift   # Engagement Notifications & Shopping Link Detection
    ├── Models/
    │   └── Models.swift                # User, TryOnTask, and Pricing Codable Models
    ├── Views/
    │   ├── Home/
    │   │   └── HomeScreen.swift        # Daily credits counter & Random Trial 🎲
    │   ├── TryOn/
    │   │   ├── TryOnUploadView.swift   # Garment & Person Photo Picker
    │   │   └── TryOnResultView.swift   # High-Res Result Display
    │   ├── Premium/
    │   │   └── PremiumView.swift       # Apple Review Guideline 3.1.1 Compliant Paywall
    │   └── Wardrobe/
    │       └── WardrobeView.swift      # Saved Outfits History
    └── Resources/
        └── Info.plist                  # Camera & Photo Privacy Permissions
```

---

## 🚀 How to Build & Deploy to App Store

1. **Open Xcode on macOS**:
   - Open Xcode -> Select **"Create a new Xcode project"** -> Choose **iOS App (SwiftUI)**.
   - Set Product Name: `TryZon AI`
   - Set Bundle Identifier: `com.jagtarapvtltd.tryzonai`
   - Add the source files from `ios-app/TryZonAI/` to the target.

2. **App Store Connect Setup**:
   - Log in to [appstoreconnect.apple.com](https://appstoreconnect.apple.com).
   - Under In-App Purchases / Subscriptions, add the 8 product IDs matching the backend:
     - `credits_pocket` (₹29 / $0.49 - Consumable)
     - `credits_starter` (₹99 / $1.29 - Consumable)
     - `credits_value` (₹299 / $3.99 - Consumable)
     - `credits_business` (₹799 / $9.99 - Consumable)
     - `credits_enterprise` (₹2,499 / $29.99 - Consumable)
     - `sub_weekly_pro` (₹99/wk - Auto-Renewable Subscription)
     - `sub_monthly_pro` (₹299/mo - Auto-Renewable Subscription)
     - `sub_yearly_legend` (₹1,499/yr - Auto-Renewable Subscription)

3. **Archive & TestFlight**:
   - In Xcode: Menu `Product > Archive`.
   - Click **"Distribute App"** -> Upload to **TestFlight**.
   - Test on iOS device, then submit for **App Store Review**!
