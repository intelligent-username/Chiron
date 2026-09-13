# Chiron

![Cover Image](imgs/Cover.png)

Chiron is a high-speed, customizable, and feature-rich workout tracker designed to eliminate the bloat, forced recommendations, rigid constraints, and invasive tracking found in modern fitness apps. Built with Jetpack Compose and an offline-first Room architecture, Chiron offers deep customization for traditional lifting, calisthenics, cadence training, and complex volume metrics.

Download the latest version from the [releases tab](https://github.com/intelligent-username/Chiron/releases).

---

## The Three Core Tabs

![Screenshots](imgs/demos.webp)

Chiron is organized into three primary tabs, each featuring dedicated sub-modes:

### 1. History Tab (Workout Tracking & Volume Analytics)

The central hub for logging workouts in real time, reviewing past sessions, and analyzing training volume:

*   **Session Logger & Editor:** Dynamically compose workouts containing individual exercises, sets, reps, weight, distance, and duration.
*   **Supersets & Dropsets:** Link consecutive exercises into functional supersets or dropsets with a connected visual UI.
*   **Performance Comparison:** View your "Previous" performance directly within each card to see exactly what numbers you need to match or beat.
*   **Workout Management:** Duplicate routines, reorder sets, or filter past sessions by exercise or date.
*   **Volume Analytics (Sub-View):** Tap the Volume icon in the top bar to toggle comprehensive training volume charts:
    *   *Adaptive Date Windows:* View volume across 7, 14, 30, or up to 90 days (or one-third of your total workout history), automatically capped to your first recorded workout.
    *   *Even-Rounded Y-Axis Ticks:* Dynamic Y-axis scale rounds cleanly to thousands or half-thousands for intuitive readability.
    *   *Interactive Navigation:* Tap any volume bar to jump directly into that day's workout log.

### 2. Exercises Tab (Exercise Directory, PRs & Goals)

A fast, searchable catalog for managing movements, checking personal records, and setting milestones:

*   **Categorical PR Tracking:** Automatic PR detection across 5 distinct exercise categories:
    *   *Weight & Reps:* Tracks peak weight per repetition count with integrated 1RM estimation formulas.
    *   *Time & Weight:* Longest hold duration achieved for a given load (e.g. weighted planks, deadhangs).
    *   *Distance & Weight (Rep-Based):* Tracks weight achieved for specific distance/repetition buckets.
    *   *Distance & Weight (Non-Rep):* Tracks longest distance achieved per weight (e.g. farmer carries).
    *   *Distance & Time:* Fastest duration achieved for a given distance.
*   **Interactive PR Details & Filter Pills:** High-fidelity PR view with horizontal sliding category pills (e.g. box jump heights).
*   **Exercise-Specific Volume History:** Each exercise detail screen features a dedicated volume graph spanning from the first logged session to the present day, calculating volume for both weight/rep and time-based movements.
*   **Custom Exercise Creator & 100+ SVG Icons:** Build custom exercises with full control over tracking dimensions (weight, reps, time, distance, bodyweight percentage). Choose from a built-in library of 100+ custom SVG icons.
*   **Goals Mode (Sub-View):** Tap the Goals icon to manage long-term strength and endurance targets, complete with target completion dates, milestones, and linked exercises.

### 3. Timer & Tools Tab (Intervals, Metronome & Bodyweight)

Pacing and biometric tracking utilities built directly into your workflow:

*   **Interval Timer & Stopwatch:** Multi-mode timers featuring customizable preset sheets, quick-increment shortcuts, and background countdown alerts.
*   **Metronome:** Interactive pendulum metronome for pacing cadence and tempo work, backed by multiple tick audio profiles.
*   **Bodyweight Stats (Sub-View):** Tap the header to open the Bodyweight tracking suite:
    *   *Weigh-In Logs:* Record bodyweight entries with timestamps, notes, and flexible units (kg/lbs).
    *   *Gap-Abridged Trend Graph:* Interactive trend chart that eliminates artificial zero-points, featuring nicely rounded scale ticks (nearest 0.25, 0.5, or 1.0 kg/lb).
    *   *Inline Editing & Deletion:* Edit past weigh-in values or delete records directly from the history list.
*   **Flexible Bodyweight Importer:** Import weight logs from plain text files with customizable delimiters (spaces, tabs, commas, pipes), date format detection, live row preview, and idempotent upserts.

---

## Special Features

*   **Offline Spotify Mini-Player:** A floating mini-player bar communicating locally with the on-device Spotify app via IPC. Includes gesture-driven expansion, animated wave seek bar, and **dynamic UI color shifting** that extracts dominant colors from the current album art to tint the entire app theme.
*   **Dynamic Bodyweight Volume Pipeline:** Exercises flagged with a bodyweight percentage (e.g., Pull-ups at 100%, Push-ups at 60%, Sit-ups at 50%, Dips at 100%) dynamically compute:
    $$\text{Effective Load} = (\text{Bodyweight}_{\text{date}} \times \%) + \text{Added Load}$$
    Past volume history recalculates reactively when weigh-ins are added or edited, treating the set weight field as added external load (e.g., weighted vest/belt).
*   **Volume Estimation Formulas:**
    *   *Reps + Weight:* $\text{Effective Weight} \times \text{Reps}$
    *   *Distance + Reps + Weight:* $\text{Effective Weight} \times \text{Reps} \times (\text{Distance} \times 2.0)$
    *   *Distance + Weight:* $\text{Effective Weight} \times \frac{\text{Distance}}{5.0}$
    *   *Time + Weight:* $\text{Effective Weight} \times \frac{\text{Duration}}{3.0}$
*   **Import / Export:** Full JSON backup and recovery tools to export or restore all workout history, exercise configurations, and weight logs offline.
*   **Unit Preferences:** Seamless live conversion between Metric (kg, meters) and Imperial (lbs, feet/inches).

---

## <img src="imgs/logo.png" width="16" height="16" style="border-radius: 50%"> Compilation & Build

### Prerequisites
*   [JDK 17+](https://www.oracle.com/java/technologies/downloads/)
*   Android SDK 35 (compileSdk 35, minSdk 26)

### Debug Build
To assemble an unsigned debug APK:

```bash
.\gradlew.bat assembleDebug
```

The resulting APK will be located at:
`app\build\outputs\apk\debug\Chiron-debug.apk`

### Release Build
To assemble an optimized, minified release APK:

```bash
.\gradlew.bat assembleRelease
```

> **Note:** A valid `keystore.properties` file must be present in the project root to sign the release build. Refer to `keystore.properties.example` for required properties (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

The resulting release APK will be located at:
`app\build\outputs\apk\release\Chiron-release.apk`

For official release binaries, visit the [GitHub Releases](https://github.com/intelligent-username/Chiron/releases).

---

## License

This project is licensed under the Apache 2.0 License.
