# WoW Forever Addon Manager (Android)

Quick Android app to search, install, and manage CurseForge addons for **WoW Forever** (game version `1.60.1`, gameId=1).

## Features
- Enter your CurseForge API key
- Select the AddOns folder (`_forever_/Interface/AddOns`) via Storage Access Framework (persisted)
- Search addons filtered to 1.60.1
- View files and one-click install (downloads + extracts zip into the folder)
- List installed addon folders
- Local management (no full account login/sync — CurseForge user auth is proprietary; this uses the public API key)

## Getting an API Key
1. Go to [https://console.curseforge.com/](https://console.curseforge.com/)
2. Sign in / create account
3. Create an API key
4. Paste it into the app

## Build Instructions (One-click in Android Studio)
1. Install [Android Studio](https://developer.android.com/studio)
2. Open this folder (`WoWForeverAddonManager`) in Android Studio
3. Let it sync Gradle (it will download the wrapper if needed)
4. Connect a phone or start an emulator
5. Click the Run button (green triangle) or **Build > Build Bundle(s)/APK(s) > Build APK(s)**
6. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`

### Command line (after wrapper is generated)
```bash
./gradlew assembleDebug
```

## Folder Auto-detect
The app uses the standard Storage Access Framework so it works on modern Android without special permissions.  
On first launch, tap **Select AddOns Folder** and navigate to:

**Default for jaredgei/wow-forever-android:**  
`/storage/emulated/0/WoW Forever/_forever_/Interface/AddOns`

(Create the Interface/AddOns folders if they don't exist yet.)

The permission is persisted so you only pick it once.

## Limitations / Notes
- Extraction is basic (handles typical addon zip structure). Complex multi-folder zips may need manual cleanup.
- Update checking is listed but not fully automated in this quick version — you can reinstall the latest file.
- Full CurseForge account login + library sync is not implemented (requires reverse-engineering their private auth). API key + local is the practical approach.
- Tested conceptually against CurseForge REST API v1. gameVersion=`1.60.1`.

## Requirements
- Android 8.0+ (API 26)
- Internet for API/downloads
- CurseForge API key

Enjoy your addons on the phone!
