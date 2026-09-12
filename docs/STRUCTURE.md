# File Structure

This file structure was made to ensure parallel (multi-core) compilation works well. The diagram below helps me locate where each file is in case I forget after a while, in particular the `icons/` folder, which is updated frequently

```
Chiron/
├── app/                                  # Main Android application module
│   └── src/main/
│       ├── assets/
│       │   ├── audio/                    # Sound effects for workout timer and metronome
│       │   └── icons/                    # Exercise icons in .svg format
│       ├── java/                         # App entry, DI wiring, background services (timer/metronome), and root UI
│       └── res/                          # Android resources (launcher icons, drawables, strings, themes)
│
├── core/                                 # Shared foundational modules
│   ├── common/                           # Utilities (date math, 1RM estimation, unit conversions)
│   ├── database/                         # Room SQLite database, DAOs, repositories, and export/import
│   ├── model/                            # Shared domain data models and Room entities
│   └── ui/                               # Design system components, colors, and Compose theme
│
├── feature/                              # Feature-specific modules
│   ├── exercises/                        # Exercise library, search, detail screens, and tracking setup
│   ├── history/                          # Workout history logs, calendars, and volume analytics
│   ├── timer/                            # Workout interval timer, metronome, and bodyweight stats
│   └── workouts/                         # Active workout logger and session execution
│
├── db/                                   # Standalone SQLite schemas and SQL migration references (not comprehensive)
├── docs/                                 # This folder here :)
├── gradle/                               # Gradle wrapper binaries and configuration
└── imgs/                                 # Documentation screenshots
```
