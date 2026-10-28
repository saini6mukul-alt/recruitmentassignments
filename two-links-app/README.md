# My Live TV (2-link app)

Ek simple Android app jisme do websites do tabs mein khulti hain:

| Tab | Link |
|---|---|
| **Live TV Hub** | https://allinonelivetv.pages.dev/ |
| **StreamFlex TV** | https://streamflexsmm.in/Game/ |

<img src="icon-512.png" width="96" alt="App icon">

## Android par install kaise karein

1. Is folder ki **`MyLiveTV.apk`** file phone mein download karein (GitHub par file kholkar "Download raw file" dabaayein).
2. File manager ya Downloads se `MyLiveTV.apk` par tap karein.
3. "Install unknown apps" ki permission maange to us app (Chrome / Files) ke liye **Allow** karein.
4. **Install** dabaayein. Play Protect warning aaye to "More details → Install anyway" chunein (app Play Store se nahi hai, isliye ye warning aati hai).

Requirement: Android 5.0 ya usse naya.

## App ke features

- Neeche 2 tabs: dono websites alag-alag chalti rehti hain, tab badalne par page reload nahi hota.
- Active tab par dobara tap = us website ka home page.
- ↻ button = current page reload.
- Video fullscreen karne par phone apne aap landscape mein ghoomega, screen on rahegi.
- Back button: pehle fullscreen band, phir pichla page, phir pehla tab, phir app band.
- Internet na ho to "Dobara try karein" wala page dikhta hai.
- WhatsApp / Telegram / Play Store jaise links unki apni app mein khulte hain.

## iPhone (iOS) par

`.apk` iPhone par install nahi ho sakti. Bina Apple Developer account ke iPhone par apni app install karna possible nahi hai.
Sabse aasaan tareeka:

1. iPhone par **Safari** mein pehla link kholein.
2. Neeche **Share** button (⬆️ wala dabba) → **Add to Home Screen** → **Add**.
3. Doosre link ke liye bhi yahi karein.

Home screen par dono ke icon ban jaayenge aur app ki tarah khulenge.

## Khud se APK dobara build karna (developers ke liye)

Gradle ki zaroorat nahi hai. JDK aur Android SDK (`platforms;android-34`, `build-tools;36.0.0`) chahiye:

```bash
cd android
ANDROID_HOME=/path/to/android-sdk ./build.sh
# Output: android/build/MyLiveTV.apk
```

Links ya tab ke naam badalne ke liye `android/src/com/mukul/mylivetv/MainActivity.java` mein `TITLES` aur `URLS` edit karein.
App ka naam `android/res/values/strings.xml` mein hai.

> Note: `build.sh` pehli baar `~/.android/mylivetv.keystore` banata hai. Kisi doosre computer par build ki gayi APK alag key se sign hogi, isliye update se pehle purani app uninstall karni padegi.
