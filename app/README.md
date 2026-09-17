# Smart Home Dashboard

A Kotlin Multiplatform smart-home dashboard for Android, iOS, and desktop.

## Running the apps

Run Gradle commands from this `app/` directory:

- Android: `./gradlew :androidApp:assembleDebug`
- Desktop: `./gradlew :shared:run`
- iOS: open [`iosApp/iosApp.xcodeproj`](./iosApp/iosApp.xcodeproj) in Xcode

## Desktop release packages

Publishing a GitHub Release builds and attaches two self-contained desktop installers. They bundle the required Java runtime, so Java does not need to be installed separately.

### Windows

Download `SmartHome-<version>-windows-x64.exe` from the GitHub Release and run it. The installer is currently unsigned, so Windows SmartScreen may show an **Unknown publisher** warning. If you trust the downloaded release, select **More info**, then **Run anyway**.

### Fedora KDE

Download `SmartHome-<version>-fedora-x86_64.rpm` from the GitHub Release. Open it with Discover, or install it from a terminal:

```bash
sudo dnf install ./SmartHome-<version>-fedora-x86_64.rpm
```

The installed application is available from the KDE application launcher.

## Tests

- Shared tests: `./gradlew :shared:allTests`
- Android host tests: `./gradlew :shared:testAndroidHostTest`
- iOS simulator tests: `./gradlew :shared:iosSimulatorArm64Test`

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
