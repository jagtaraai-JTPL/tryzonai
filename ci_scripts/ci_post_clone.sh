#!/bin/sh

# Apple Xcode Cloud Post-Clone Automation Script
echo "Starting Xcode Cloud Build & Packaging for TryZon AI..."

# Ensure XcodeGen generates project if needed
if command -v xcodegen >/dev/null 2>&1; then
    cd ios-app && xcodegen generate && cd ..
fi

echo "Xcode Cloud Pre-build Verification Complete!"
