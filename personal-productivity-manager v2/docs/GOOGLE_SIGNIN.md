# Google Sign-In setup (you must do this part; it needs your Google account)

1. Google Cloud Console > create/choose a project > APIs & Services > OAuth consent screen (External, add yourself as test user).
2. Credentials > Create OAuth client ID:
   - **Web application** -> copy its Client ID. This is `GOOGLE_WEB_CLIENT_ID` (server `.env`) and `googleWebClientId` (app build).
   - **Android** -> package `com.ppm.momentum` + the SHA-1 of the key that signs your APK
     (debug key: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`;
     for GitHub-built APKs the debug key differs per runner, so use a fixed keystore stored as a GitHub secret).
3. Build the app with the client id:
   `gradle assembleDebug -PgoogleWebClientId=XXXX.apps.googleusercontent.com -PdefaultServerUrl=https://your.domain`
   (or put those two lines in `android-app/gradle.properties`; in CI pass them from repository variables).
4. For the web client at `/app/`, add your server origin under the Web client's "Authorized JavaScript origins".

No Firebase project or `google-services.json` is needed: the app gets a Google ID token and the server verifies it.
