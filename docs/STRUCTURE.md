# File Structure

This multi-module file structure is designed for modularity, testability, and parallel Gradle compilation. The diagram below details the responsibility of each module and key directory, specifically highlighting asset locations such as `icons/` and `audio/`.

```
Chiron/
├── app/                                  # Main Android application module (UI shell, navigation, DI)
│   └── src/main/
│       ├── java/                         # Application class, MainActivity, DI ServiceLocator, Settings
│       └── res/                          # App icons, launcher assets, and theme resources (NO raw assets)
│
├── core/                                 # Shared foundational modules
│   ├── common/                           # Pure Kotlin utilities (date math, 1RM estimation, tick scaling, unit conversions)
│   ├── database/                         # Room SQLite DB (v14), DAOs, repositories, migrations, export/import
│   ├── model/                            # Shared domain data models and Room entities (Exercise, WorkoutSession, SetEntry, BodyWeightEntry)
│   ├── spotify/                          # Spotify App Remote IPC integration, dominant color extraction, offline mini-player
│   └── ui/                               # Shared Compose theme, components (BottomNavBar, IconPicker), colors
│       └── src/main/assets/icons/        # 100+ bespoke exercise icons in .svg format (canonical asset source)
│
├── feature/                              # Feature-specific modules
│   ├── exercises/                        # Exercise library, custom exercise builder, PR calculation, 1RM estimation, exercise volume graph
│   ├── goals/                            # Goal tracking, target exercises, milestones, and progress metrics
│   ├── history/                          # Workout history logs, active workout logger & session editor, supersets, volume analytics
│   └── timer/                            # Workout interval timer, metronome, and bodyweight stats
│       └── src/main/
│           ├── assets/audio/             # Metronome tick sound files (Tick1.mp3, Tick2.mp3, Tick3.mp3)
│           └── res/raw/                  # Timer countdown audio effect (beep.mp3)
│
├── db/                                   # This folder here
├── docs/                                 # Project documentation and architectural guides
├── gradle/                               # Gradle wrapper binaries, version catalog (libs.versions.toml)
├── imgs/                                 # Documentation screenshots, promotional assets, and diagrams
└── notes/                                # Architectural plans, refactoring notes, and design docs
```
