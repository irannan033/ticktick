# Google Sign-In (Phase 3 — not implemented yet)

Needs credentials only you can create:
1. Create a Firebase project; enable Authentication > Google.
2. Register the Android app (`com.ppm.momentum`) with your debug/release SHA-1 fingerprints.
3. Download `google-services.json` into `android-app/app/` (it is git-ignored).
4. Note the Web client ID; the backend verifies Google ID tokens with it.
The login button on the welcome screen is disabled until this phase.
