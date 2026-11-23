# CodeMaster AI Offline for Android

CodeMaster AI is an Android coding assistant that runs a local GGUF language model through llama.cpp. After the model is downloaded once, inference happens on the phone; chat prompts are not sent to an AI API.

## Build the APK without a PC
1. Open the Actions tab in this repository.
2. Open the latest **Build CodeMaster AI Offline APK** run.
3. When it finishes successfully, download the **CodeMaster-AI-Offline-APK** artifact.
4. Extract the ZIP and install app-debug.apk on an Android phone.

## First launch
- Connect to Wi-Fi and tap **Download offline AI model (2.5 GB)**.
- Keep CodeMaster AI open until the download finishes.
- The app loads the Qwen3-4B Q4_K_M model. Allow several minutes for first load.
- Once loaded, the model runs locally and can answer without an internet connection.

Model: [Qwen3-4B-GGUF Q4_K_M](https://huggingface.co/Qwen/Qwen3-4B-GGUF), Apache-2.0. Model file is approximately 2.5 GB. It is downloaded separately rather than bundled inside the APK so the APK remains a manageable size.

Runtime: [llama.cpp](https://github.com/ggml-org/llama.cpp). The Android build targets arm64-v8a devices.

## Important device notes
- Keep at least 5 GB of free storage before downloading the model.
- The model uses several GB of RAM while running. Close other apps first. Performance and whether it fits depend on the phone, Android version, available memory, and thermal state.
- This is a local language model, not a hosted ChatGPT-class service. It can make mistakes; review generated code before running it.
- Internet is needed for the initial model download only. No API key or paid AI subscription is required.
