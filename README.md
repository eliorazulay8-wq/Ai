# CodeMaster AI — Android app

A native Android app (Kotlin + WebView) with a phone-friendly coding assistant UI and a GitHub Actions workflow that builds a debug APK in the cloud.

## Build and download the APK (no PC required)
1. Open this repository on your phone and select **Actions**.
2. If prompted, enable GitHub Actions.
3. Select **Build CodeMaster AI APK** and tap **Run workflow** (or push a commit to `main` to trigger it).
4. Wait for the workflow to finish successfully.
5. Open the completed run and download the `CodeMaster-AI-debug-apk` artifact.
6. Extract the downloaded ZIP and install `app-debug.apk`. Android may ask you to allow installs from the app used to download it.

## Connect the AI
On first launch, tap Setup and enter your own OpenAI API key. Select a model and connect. API usage is billed separately; a ChatGPT subscription does not include API credits.

**Security:** the key is held only in the page's memory and is cleared when the app restarts. Direct client-side API calls are less secure than a private backend. Do not share your key or a preconfigured APK. This app sends prompts and conversation history to the OpenAI API.

## Project
- Android: Kotlin + Android WebView
- UI: bundled HTML/CSS/JavaScript
- Package: `com.codemaster.ai`
- Minimum Android: API 23 (Android 6.0)
- Build output: debug APK for personal testing, not a Play Store release

The app generates code as text; it does not compile or execute arbitrary code on the phone.
