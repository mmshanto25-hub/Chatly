# Chatly - Native Android Real-Time Chat Application

Chatly is a production-ready, modern native Android real-time messaging application built with **Jetpack Compose**, **Material 3**, **Kotlin Coroutines & Flow**, **Firebase Authentication**, and **Cloud Firestore**.

---

## Features

- **Secure Authentication**: Email & Password sign-up and login with Firebase Auth and session management.
- **Real-Time Messaging**: Instant 1-to-1 messaging powered by Cloud Firestore real-time snapshot listeners.
- **User Discovery & Contacts**: Search registered users by name or email and start direct conversations.
- **Modern UI & Design**: Built with Jetpack Compose and Material 3, supporting dark and light themes, responsive layouts, and smooth animations.
- **Robust State Handling**: Loading, empty, and error states across all screens.
- **Security**: Firestore security rules ensuring only conversation participants can access private chat history.

---

## Firebase Setup Instructions

To connect Chatly to your own Firebase backend, follow these steps:

1. **Create a Firebase Project**:
   - Go to the [Firebase Console](https://console.firebase.google.com/).
   - Click **Add project** and follow the setup wizard.

2. **Register Android App**:
   - In your Firebase project overview, click the **Android** icon to add an app.
   - Enter package name: `com.example.chat`.
   - Register the app and download your `google-services.json` file.

3. **Add google-services.json**:
   - Place the downloaded `google-services.json` file directly into the `app/` directory of your Android project (`Chat/app/google-services.json`).

4. **Enable Firebase Authentication**:
   - In the Firebase Console, navigate to **Authentication** -> **Sign-in method**.
   - Enable **Email/Password**.

5. **Enable Cloud Firestore**:
   - Navigate to **Firestore Database** and click **Create database**.
   - Start in **Test mode** (or production mode with the provided rules below).

6. **Deploy Firestore Security Rules**:
   - Deploy the provided `firestore.rules` file in your Firebase Console under Firestore -> Rules.

---

## Building and Running

1. Open the project in **Android Studio** (Koala or newer).
2. Sync project with Gradle files.
3. Connect an Android device or start an emulator (API 28+).
4. Click **Run** (`Shift + F10`) to build and launch Chatly.
