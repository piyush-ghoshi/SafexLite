#!/bin/bash

# Campus Panic Button - Firebase Functions Deployment Script

echo "🚀 Deploying Campus Panic Button Firebase Functions..."

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo "❌ Firebase CLI is not installed. Please install it first:"
    echo "npm install -g firebase-tools"
    exit 1
fi

# Check if logged in to Firebase
if ! firebase projects:list &> /dev/null; then
    echo "❌ Not logged in to Firebase. Please login first:"
    echo "firebase login"
    exit 1
fi

# Navigate to functions directory
cd functions

# Install dependencies
echo "📦 Installing dependencies..."
npm install

# Build TypeScript
echo "🔨 Building TypeScript..."
npm run build

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please fix TypeScript errors."
    exit 1
fi

# Go back to root directory
cd ..

# Deploy functions
echo "🚀 Deploying functions to Firebase..."
firebase deploy --only functions

if [ $? -eq 0 ]; then
    echo "✅ Functions deployed successfully!"
    echo ""
    echo "📋 Next steps:"
    echo "1. Verify functions are running in Firebase Console"
    echo "2. Test alert creation to trigger notifications"
    echo "3. Monitor function logs: firebase functions:log"
else
    echo "❌ Deployment failed. Check the error messages above."
    exit 1
fi