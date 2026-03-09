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

And look the file at `app\build\outputs\apk\debug\app-debug.apk`

For official builds, check the [releases](https://github.com/intelligent-username/Chiron/releases).

As of right now, there's only an Android version. Download the `apk` file. The security warnings are harmless (since downloading from GitHub is not the same as the official Play Store, these things will pop up).

## Modifying the App

To modify the app, you'll need to use Android Studio or XCode depending on if you want to twaek an Android or iOS version of the app. Clone this repository and make whatever changes you want.

## Upcoming Changes

This app is completely done with every feature I intend to add. The only thing that's left to do is some restructuring so I can take advantage of Kotlin's Compose Multiplatform and make this available for iOS and more.

Steps:

  1. Modularize all files and functions to a decent extent, so they're easier to track. Possibly also convert icons to XML (tried this before) to increase loading effiency.

  2. Add the following modules:

      ```md
        - `shared/`
          - `src/commonMain/`   <- universal stuff (both logic and UI)
          - `src/androidMain/`  <- Android-specific code
          - `src/iosMain/`      <- iOS-specific code
        - `androidApp/`
        - `iosApp/`           <- iOS-specific code
      ```

  3. Create a GitHub Action to automatically build and release both Android and iOS versions of the app on push.

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

-->

<!-- 
Command

```bash
find . -type f -name "*.kt" -exec wc -l {} + | sort -nr | head -n 10```
 -->

## License

This project is licensed under the Apache 2.0 License.
