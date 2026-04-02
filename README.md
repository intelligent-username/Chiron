# Chiron

![Cover Image](imgs/Cover.png)

Chiron is a high-speed, customizable, and feature-rich workout tracker designed to get rid of all the bloat, recommendations, lack of customization, and/or tracking that's found in virutally every single other app. It's designed to my taste, so it has every faeture that I will need. Download the version for your device [here](https://github.com/intelligent-username/Chiron/releases).

## Features

- Pre-made exercises
- Create custom exercises
- Workouts composed of exercise, each exercise is composed of sets: (weight, reps) tuples.
- Functional Supersets
- Create and Save custom reusable names and locations.
  - Ordered by recency.
  - Filter by names
  - Filter by locations
- Timer and stopwatch with presets.
- Duplicate workouts
- View 'previous' performance of an exercise to see what numbers you have to beat.
- Automatically tracks personal records
- Import and Export data
- Spotify integration with equally clean UI :D

## Compilation

First, make sure you have [JDK](https://www.oracle.com/java/technologies/downloads/) and [Gradle](https://docs.gradle.org/current/userguide/installation.html) installed.

Run

```bash
.\gradlew.bat assembleDebug
```

To assemble a debug version of the app. This isn't the best for serious production & distribution, but it's decent and fast.

All the releases so far have been debug versions.

Run

```bash
.\gradlew.bat assembleRelease
```

For assembling an actual release build. (Note that you'll need a proper `keystore.properties` file with the right credentials to sign the app, otherwise pre-existing versions of it won't update.)

And look the file at `app\build\outputs\apk\debug\app-debug.apk`

For official builds, check the [releases](https://github.com/intelligent-username/Chiron/releases).

To develop on your own, I recommend [Android Studio](https://developer.android.com/studio).

Download your respective installer to download the app. Then, you can run it like any other app. For Spotify integration, you'll need to log in with your Spotify account on the Spotify app first (this is a feature required by Spotify itself). 

## Modifying the App

To modify the app, you'll need to use Android Studio or XCode depending on if you want to twaek an Android or iOS version of the app. Clone this repository and make whatever changes you want.

## Upcoming Changes🚧🗓️🚧‼️

Basically all features are done. I might make some UI/efficiency tweaks, but otherwise, the only thing left is to make an iOS version. The problem here is, for official released, the Android version is signed only once and pretty easily, whereas the iOS version needs to be re-signed every 7 days, which is why iOS support will likely be delayed for a long time. 

Steps:

  1. Add the following modules to create cross-platform compatibility:

      ```md
        - `shared/`
          - `src/commonMain/`   <- universal stuff (both logic and UI)
          - `src/androidMain/`  <- Android-specific code
          - `src/iosMain/`      <- iOS-specific code
        - `androidApp/`
        - `iosApp/`
      ```

      Once this step is done, need to ensure support for the miniplayer.

  2. Create a GitHub Action to automatically build and release both Android and iOS versions of the app on push.

<!--   

These are the files that I might want to refactor:

   436 ./app/src/main/java/com/chiron/app/data/transfer/DataTransferRepository.kt
   388 ./app/src/main/java/com/chiron/app/ui/history/ExerciseEntryCard.kt
   345 ./app/src/main/java/com/chiron/app/data/ChironRepository.kt
   324 ./app/src/main/java/com/chiron/app/ui/history/WorkoutEditor.kt
   285 ./app/src/main/java/com/chiron/app/ui/history/WorkoutEditorHeader.kt
   282 ./app/src/main/java/com/chiron/app/ui/history/SupersetCard.kt
   265 ./app/src/main/java/com/chiron/app/ui/settings/SettingsScreen.kt
   259 ./app/src/main/java/com/chiron/app/spotify/MiniPlayerBar.kt
   239 ./app/src/main/java/com/chiron/app/ui/timer/TimerScreen.kt

Command:

```bash
find . -type f -name "*.kt" -exec wc -l {} + | sort -nr | head -n 10
```

9153 Lines total btw

 -->

<!-- 
Some notes for future multi-platform support
Using Kotlin Multiplateform

- The 'back button' stuff (especially when searching in the 'Exercises' tab) should be checked
- Spotify SDK calling logic will diverge basically just in name
- Playback checker for miniplayer logic

 -->

 <!-- 
 Misc To-Do
 
 Minor behavioral conflict when previewing last performance of the last exercise in a workuot (so the bottom one). Sometimes stuck and need to re-press to unpress, sometimes just works. IDK why
 
 Better UI?

 Loading Screen?

 Add a demo recording.
 
  -->

## License <img src="imgs/image.png" width="32" height="32">

This project is licensed under the Apache 2.0 License.
