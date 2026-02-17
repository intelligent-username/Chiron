# Chiron

![Cover Image](imgs/Cover.png)

My Personal Workout Tracker. This app is made for Android.

## File Structure

```md
Chiron/
├── imgs/         # Images used in the README, documentation, etc.
├── src/          # The actual source code for the app
├── docs/         # Documentation files (coming soon!?)
├── tests/        # Unit and integration (will add once project is done)
├── .gitignore    # Specifies files to ignore in git
├── LICENSE       # APACHE 2.0
└── README.md     # This file
```

## Compilation

Run

```bash
.\gradlew.bat assembleDebug
```

And look the file at `app\build\outputs\apk\debug\app-debug.apk`

For official builds, check the [releases](https://github.com/intelligent-username/Chiron/releases).

As of right now, there's only an Android version. Download the `apk` file. The security warnings are harmless (since downloading from GitHub is not the same as the official Play Store, these things will pop up).
