# Chiron

![Cover Image](imgs/Cover.png)

My Personal Workout Tracker.

## Features

- Pre-made exercises
- Create custom exercises
- Create workouts by adding exercises to them. Add sets, reps, and weight to each exercise in the workout.
- Functional Supersets
- Workout history with filterable tags.
  - Ordered by recency.
- Timer and stopwatch with presets.
- Duplicate workouts
- View 'previous' performance of an exercise
- Store personal records

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

To assemble a debug version of the app. This is not secure for distribution, but it's decent and fast.

Run

```bash
.\gradlew.bat assembleRelease
```

For an actual release build. Note that you'll need a proper `keystore.properties` file with the right credentials to sign the app, otherwise pre-existing versions of it won't update.

And look the file at `app\build\outputs\apk\debug\app-debug.apk`

For official builds, check the [releases](https://github.com/intelligent-username/Chiron/releases).

As of right now, there's only an Android version. Download the `apk` file. The security warnings are harmless (since downloading from GitHub is not the same as the official Play Store, these things will pop up).

## Modifying the App

To modify the app, you'll need to use Android Studio or XCode depending on if you want to twaek an Android or iOS version of the app. Clone this repository and make whatever changes you want.

## Upcoming Changes

These are the changes I want to make. Basically all features are done. The only thing that's left is the miniplayer feature, some refactoring, and creating 'release-ready' builds via kts properties. To make iOS release, this will be very inconvenient since it looks like codes expire after 7 days, so I might just abonden the iOS version completely.

Steps:

  1. Create the miniplayer. Get certified with Spotify's SDK.

  2. Modularize all files and functions to a decent extent, so they're easier to track. Possibly also convert icons to XML (tried this before) to increase loading effiency.

  3. Add the following modules:

      ```md
        - `shared/`
          - `src/commonMain/`   <- universal stuff (both logic and UI)
          - `src/androidMain/`  <- Android-specific code
          - `src/iosMain/`      <- iOS-specific code
        - `androidApp/`
        - `iosApp/`
      ```

      If this step is done, need to ensure compatibility with the miniplayer.

  4. Create a GitHub Action to automatically build and release both Android and iOS versions of the app on push.

<!--   

These are the files that need to be refactored:

  1798 ./app/src/main/java/com/chiron/app/ui/history/WorkoutEditor.kt
  422 ./app/src/main/java/com/chiron/app/data/ChironRepository.kt
  352 ./app/src/main/java/com/chiron/app/ui/timer/PresetsSheet.kt
  320 ./app/src/main/java/com/chiron/app/viewmodel/HistoryViewModel.kt
  307 ./app/src/main/java/com/chiron/app/ui/exercises/PrScreen.kt
  269 ./app/src/main/java/com/chiron/app/MainActivity.kt
  262 ./app/src/main/java/com/chiron/app/ui/history/HistoryScreen.kt
  255 ./app/src/main/java/com/chiron/app/ui/exercises/ExercisesScreen.kt
  248 ./app/src/main/java/com/chiron/app/ui/components/IconPicker.kt  

Command:

```bash
find . -type f -name "*.kt" -exec wc -l {} + | sort -nr | head -n 10
```
 -->

<!-- 
Some notes for future multi-platform support
Using Kotlin Multiplateform

- The 'back button' stuff (especially when searching in the 'Exercises' tab) should be checked
- Spotify SDK calling logic will diverge basically just in name
- Playback checker for miniplayer logic

 -->

## License

This project is licensed under the Apache 2.0 License.
