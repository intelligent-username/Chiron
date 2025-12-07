# Chiron — Full specification (minimal, local-first workout tracker)

**Intention.** Chiron is a small, simple, modern workout tracker for a single user on Android. It is offline-only, fast to use in-gym, and deliberately feature-minimal: record exercises, sets (weight + reps), notes, and lightweight tags (Day, Date, Location). The app must be one-screen-simple for logging and easy to edit afterwards. No cloud, no analytics, no templates, no exports. Build only what you explicitly specified.

Below is a complete, implementation-ready specification: features, data model, app architecture, tech stack, UX flows, DB schema, DAOs, ViewModels, timing and autosave rules, PR logic, unit behavior, image handling, testing notes, and finally a precise file tree ready for import into Android Studio.

---

# Core feature summary (explicit)

1. **Three tabs** (bottom navigation + swipe):

   * **History** — chronological stack of workouts, inline editor for “Today” or editing past workouts. Filter by Day tag. Workouts ordered by timestamp.
   * **Exercises** — global exercise list (name, description, image URI, archived flag). Search/autocomplete (Jaccard). Edit name (cascades), description, image.
   * **Timer** — stopwatch and countdown timer (two segmented sub-tabs). Minimal controls only.

2. **Workout identity** — Each workout is defined and strictly enforced by the triple: `day_tag` (user tag), `date_iso` (ISO date / timestamp), `location_tag` (string). The DB enforces uniqueness on the triple. Workouts have canonical `workout_id`.

3. **Records** — Each workout contains ordered `ExerciseEntry` rows, each with ordered `SetEntry` rows (weight and reps). Both set weight and reps are nullable (partial entry allowed). All items have an `archived` flag; nothing is permanently deleted.

4. **Units** — Weights stored in the DB always in **pounds**. A global `UserSettings.display_in_kg` boolean toggles display conversion to kg (front-end only). If display-in-kg is enabled, the UI converts and rounds to nearest 0.5 kg, otherwise shows lbs. (User setting stored in DataStore / small DB table.)

5. **Autofill / suggestions** — When adding an exercise in a workout, the app autofills the first set with the last used weight/reps for that exercise (from DB). Name autocomplete uses Jaccard similarity over tokenized exercise names, tie-broken by recency.

6. **PR detection** — PR defined as **highest weight recorded for that exact reps count** per exercise. PRs recomputed on demand from DB after edits; transient UI badge when a new PR is created.

7. **Supersets / dropsets** — Each `ExerciseEntry` has a `sequence_type` enum (`NONE`, `SUPERSET_START`, `SUPERSET_MIDDLE`, `SUPERSET_END`, `DROPSET`) and `group_id` (nullable). Supersets are expressed by multiple `ExerciseEntry` rows sharing a `group_id`.

8. **Images** — Exercise images live as files copied into app-managed image storage and referenced by URI string in the `Exercise` entity. The repository copies the user-selected image into the app images folder at upload time. Note that images for an exercise are optional and ones that don't have an image shouldn't have any placeholder.

9. **Editing behavior** — All fields editable at any time. Partial entries allowed (e.g., weight recorded first, reps added later). Edits propagate where specified: exercise name rename cascades to historical entries. Archiving hides items from autocomplete and normal lists but does not remove history references.

10. **No extra features.** No templates, no charts, no sync, no export (explicitly none). Keep scope tight.

---

# Tech stack (final, minimal)

* **Language:** Kotlin only.
* **UI:** Jetpack Compose. Compose is Android’s recommended UI toolkit and simplifies a single-activity architecture. ([Android Developers][1])
* **Persistence:** Room (SQLite). Use entity+DAO pattern and Kotlin coroutines / Flows. ([Android Developers][2])
* **Build:** Gradle with Kotlin DSL (`build.gradle.kts`). Prefer Kotlin DSL for new projects. ([Gradle Documentation][3])
* **Image loading:** Coil (Compose-compatible) for showing URIs.
* **Image storage:** copy user-provided images into app-managed directory (see image rules below). Use MediaStore/SAF for selection.
* **Preferences:** DataStore (or a simple single-row `UserSettings` table) to persist `display_in_kg`.
* **Min SDK / target:** Min SDK **23** (reason: AndroidX libs moved to require minSdk 23 after 2025). Target and compile SDK use latest installed in Android Studio. ([Android Developers][4])

---

# App architecture (minimal, single-activity)

* **MainActivity** (single activity) with Compose and **BottomNavigation** + `Pager` for swipe. Use `ViewModel`s scoped to each tab.
* Layers:

  * **UI** (Compose composables) — simple and thin.
  * **ViewModel** (StateFlow + coroutine scope) — UI state, simple input validation, and small caching.
  * **Repository** — Room access, small business logic (PR recompute, suggestions), image copy helper.
  * **DB (Room)** — Entities & DAOs. Use transactions for multi-table operations.
* No DI framework; use a small ServiceLocator or manual injection. Keep singletons minimal.

---

# Data model and schema (Room entities)

All timestamp fields stored as epoch millis UTC (Long). Use ascending `slot_index` semantics (1 = first). `archived` is integer flag 0/1.

```sql
-- WorkoutSession (canonical workout_id)
WorkoutSession:
  id: Long PRIMARY KEY AUTOINCREMENT
  day_tag: String NOT NULL        -- user tag (e.g., "Pull Day 1")
  date_iso: String NOT NULL       -- ISO date string (YYYY-MM-DD)
  date_utc: Long NOT NULL         -- epoch millis for full timestamp (for ordering)
  location_tag: String NOT NULL
  notes: String? NULL
  archived: Int DEFAULT 0
  UNIQUE(day_tag, date_iso, location_tag)

-- Exercise (global identity)
Exercise:
  id: Long PRIMARY KEY AUTOINCREMENT
  name: String NOT NULL UNIQUE    -- non-archived duplicates forbidden
  image_uri: String? NULL         -- internal app file URI
  description: String? NULL       -- exercise-global note / description
  archived: Int DEFAULT 0

-- ExerciseEntry (a single exercise row inside a workout)
ExerciseEntry:
  id: Long PRIMARY KEY AUTOINCREMENT
  workout_id: Long NOT NULL REFERENCES WorkoutSession(id)
  exercise_id: Long NOT NULL REFERENCES Exercise(id)
  slot_index: Int NOT NULL        -- 1-based ordering of exercise in workout, ascending
  group_id: Long? NULL            -- shared group id for supersets
  sequence_type: String NOT NULL  -- ENUM: NONE, SUPERSET_START, SUPERSET_MIDDLE, SUPERSET_END, DROPSET
  notes: String? NULL             -- instance note
  archived: Int DEFAULT 0
  UNIQUE(workout_id, slot_index)

-- SetEntry (a performed set)
SetEntry:
  id: Long PRIMARY KEY AUTOINCREMENT
  exercise_entry_id: Long NOT NULL REFERENCES ExerciseEntry(id)
  set_index: Int NOT NULL         -- 1-based ordering within exercise, ascending
  weight_lbs: Double? NULL        -- always stored in lbs, nullable if partial
  reps: Int? NULL                 -- nullable if partial
  is_failed: Int DEFAULT 0
  tempo: String? NULL
  notes: String? NULL
  timestamp_utc: Long NOT NULL
  UNIQUE(exercise_entry_id, set_index)
```

Indices: index on `Exercise.name`, `WorkoutSession.date_utc`, `SetEntry.exercise_entry_id`.

---

# DAO surface (representative method signatures)

Use suspend functions and Flows where helpful.

**ExerciseDao**

* `suspend fun insertExercise(ex: Exercise): Long`
* `suspend fun updateExercise(ex: Exercise)`
* `fun getExercisesFlow(): Flow<List<Exercise>>`
* `fun searchByJaccard(input: String, limit: Int): List<Exercise>` (compute in repo using simple in-memory Jaccard, not SQL)

**WorkoutSessionDao**

* `suspend fun insertWorkout(session: WorkoutSession): Long`
* `suspend fun updateWorkout(session: WorkoutSession)`
* `fun getWorkoutsFlow(showArchived: Boolean = false): Flow<List<WorkoutSession>>`
* `suspend fun duplicateWorkout(workoutId: Long): Long` — NOT supported (user said NO duplication). Implement but keep disabled.

**ExerciseEntryDao**

* `suspend fun insertEntry(entry: ExerciseEntry): Long`
* `suspend fun updateEntry(entry: ExerciseEntry)`
* `fun getEntriesForWorkout(workoutId: Long): Flow<List<ExerciseEntry>>`

**SetEntryDao**

* `suspend fun insertSet(set: SetEntry): Long`
* `suspend fun updateSet(set: SetEntry)`
* `fun getSetsForEntry(entryId: Long): Flow<List<SetEntry>>`
* `suspend fun getLastSetForExercise(exerciseId: Long): SetEntry?` (query via JOIN/ORDER BY timestamp)

All multi-row operations (reordering slot_index, deleting and reindexing set_index) performed inside `@Transaction` methods.

---

# Business rules & behavior details

* **Autosave & debounce:** Debounce lightweight writes to DB at **750 ms** per field change; always force-sync on navigation away or tapping “Finish”. Use coroutine `debounce` or manual coroutine delay with cancellation.
* **Partial sets:** Allow `weight_lbs` or `reps` to be `null`. PR logic ignores sets missing either weight or reps.
* **Ordering:** Always store ascending slot_index and set_index; when an entry or set is removed, reindex the remaining items in a single transaction.
* **Rename cascade:** When user renames an Exercise, update the `Exercise.name` field only. Exercise entries reference `exercise_id` so history stays consistent automatically. Do not create new rows—rename updates the single global Exercise record.
* **Archive semantics:** Archiving sets `archived=1`. Archived items are excluded from default DAO queries (unless `showArchived=true`) and from autocomplete search.
* **Image handling:** On image upload, copy selected file into app-managed storage path `filesDir/images/exercises/` with filename `<exerciseId>_<systemMillis>.<ext>`. Store content URI string in DB (`image_uri`). Repo includes `imgs/` for static assets and placeholders only. When the user archives an exercise, image file remains until user explicitly deletes via "delete image" action.
* **PR detection:** For each exercise and rep count `r`, compute `max_weight_r` = max(weight_lbs WHERE reps == r). New PR if current set weight > max_weight_r. PR badges created transiently; PR values are not stored separately (computed on demand).
* **Rep/weight suggestion:** Maintain `exercise_max_1rm` as `max(weight * (1 + reps/30))` computed on-demand. For a provided weight `w`, estimate reps via inverted Epley and clamp to 1..30. If no history, default suggestions are empty (let user enter manually).
* **Units:** All weights stored as lbs; front-end converts to kg if user preference set. Use conversion factor 1 kg = 2.2046226218 lbs. Round display kg to nearest 0.5 kg.
* **Validation:** Reps must be 1..100 if non-null; weight 0.0..2000.0 if non-null. UI prevents invalid input.

---

# UI & navigation details

* **Bottom navigation** (centered at bottom): three icons with labels: History, Exercises, Timer. Tabs are swipeable (Pager). Navigation state held in `MainViewModel`.
* **History screen**:

  * Top: filter chip row with dynamic list of `day_tag` values (derived from DB distinct `day_tag`).
  * Main: grid/stack of workout cards arranged left→right, top→bottom by recency. FAB labeled “New workout” opens inline `WorkoutEditor` for today (pre-filled `date_iso`).
  * WorkoutEditor (inline):

    * Header: editable `day_tag`, `date_iso` picker, `location_tag`.
    * Vertical list of `ExerciseEntry` rectangles with drag handle (reorder), name (autocomplete), thumbnail image, and a horizontal row of set “pills”. Each pill shows `weight × reps` or “—” for missing values. Tap pill opens numeric edit for weight/reps; “+ Add Set” button at end. Save auto on change.
* **Exercises screen**:

  * Search bar with live Jaccard suggestions.
  * Rows: name, thumbnail, last-used quick stats (last weight × reps). Tap to full-detail screen with description and full history.
  * Add new exercise: name + select image + description.
* **Timer screen**:

  * Two segmented sub-tabs: Timer (countdown presets) and Stopwatch (lap times).
  * Minimal controls: start/pause/reset. No automation.

---

# Image & repo folder clarification

* **Repo `imgs/`**: static assets only (app logo, SVG icons, placeholder exercise images). These are committed in source control and used at build time.
* **Runtime user images**: copy into app files directory at runtime (`context.filesDir/images/exercises/`). Store the internal content URI in `Exercise.image_uri`. The repo `imgs/` is *not* used to store user uploads at runtime. This preserves correct behavior on devices and prevents accidental loss when the project root is separate from runtime storage.

---

# Testing & QA

* Unit tests:

  * Jaccard similarity (tokenization, scoring).
  * 1RM estimator and rep suggestion functions.
  * Unit conversion (lbs↔kg rounding).
  * DB reindexing after reorder/delete.
* Instrumented tests:

  * Add-workout flow: add exercise, add set weight then reps, verify persisted values.
  * Edit-workout flow: rename exercise, verify cascade.
  * Archive flow: archive exercise and ensure it hides from autocomplete.

---

# Build settings & versions (practical defaults)

* `compileSdk` and `targetSdk` set to latest installed in Android Studio.
* `minSdk = 23` (Android 6.0) to avoid AndroidX compatibility issues after 2025. ([Android Developers][4])
* Use `kotlin("jvm")` Gradle Kotlin DSL files (`build.gradle.kts`). Prefer Kotlin DSL for new projects. ([Gradle Documentation][3])

---

# Final file structure (ready for Android Studio)

This is a single-module Android project laid out so a new Android Studio project can be populated with these files. Place Kotlin source files under `src/main/java/com/chiron/app/` or the Kotlin equivalent and Compose resources under `src/main/res` where necessary. Take the current file structure and transform it as necessary to conform to the following structure:

```
Chiron/
├── imgs/
│   ├── Cover.png
│   ├── logo.png
│   ├── logo.svg
│   └── placeholder.svg
│
├── db/                                    # docs & schema (not runtime DB)
│   ├── schema_v1.sql
│   └── Spec.md                             # this file
│
├── build.gradle.kts                        # root Gradle (Kotlin DSL)
├── settings.gradle.kts
│
├── app/
│   ├── build.gradle.kts                    # app module build file
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/chiron/app/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── App.kt                   # Subclass & singletons/providers
│   │   │   │   ├── di/                      # simple ServiceLocator (manual DI)
│   │   │   │   ├── data/
│   │   │   │   │   ├── ChironDatabase.kt
│   │   │   │   │   ├── dao/
│   │   │   │   │   │   ├── ExerciseDao.kt
│   │   │   │   │   │   ├── WorkoutSessionDao.kt
│   │   │   │   │   │   ├── ExerciseEntryDao.kt
│   │   │   │   │   │   └── SetEntryDao.kt
│   │   │   │   │   ├── entities/
│   │   │   │   │   │   ├── Exercise.kt
│   │   │   │   │   │   ├── WorkoutSession.kt
│   │   │   │   │   │   ├── ExerciseEntry.kt
│   │   │   │   │   │   └── SetEntry.kt
│   │   │   │   │   └── ChironRepository.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/                # Compose theme, colors, typography
│   │   │   │   │   │   ├── Color.kt
│   │   │   │   │   │   ├── Type.kt
│   │   │   │   │   │   └── Theme.kt
│   │   │   │   │   ├── components/           # small reusable composables
│   │   │   │   │   │   ├── ExerciseRow.kt
│   │   │   │   │   │   ├── SetPill.kt
│   │   │   │   │   │   └── BottomNavBar.kt
│   │   │   │   │   ├── history/
│   │   │   │   │   │   ├── HistoryScreen.kt
│   │   │   │   │   │   └── WorkoutEditor.kt
│   │   │   │   │   ├── exercises/
│   │   │   │   │   │   ├── ExercisesScreen.kt
│   │   │   │   │   │   └── ExerciseDetailScreen.kt
│   │   │   │   │   └── timer/
│   │   │   │   │       ├── TimerScreen.kt
│   │   │   │   │       └── StopwatchScreen.kt
│   │   │   │   ├── viewmodel/
│   │   │   │   │   ├── HistoryViewModel.kt
│   │   │   │   │   ├── ExercisesViewModel.kt
│   │   │   │   │   └── TimerViewModel.kt
│   │   │   │   ├── util/
│   │   │   │   │   ├── UnitConversion.kt
│   │   │   │   │   ├── Jaccard.kt
│   │   │   │   │   └── OneRmEstimator.kt
│   │   │   │   └── prefs/
│   │   │   │       └── UserSettings.kt       # DataStore/simple Room table for settings
│   │   │   ├── res/
│   │   │   │   ├── values/                   # strings.xml, themes, dimens etc
│   │   │   │   └── drawable/                 # vector drawables and icons
│   │   │   └── assets/                       # optional assets
│   │   ├── test/
│   │   └── androidTest/
│   │       └── ...                          # instrumented tests
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
└── settings.gradle.kts
```

---

# Implementation notes / final cautions

* Use `Flow` from Room DAOs for reactive UI updates. Avoid heavy queries on the UI thread.
* Keep write operations single-threaded and use transactions for reorder / reindex logic.
* Keep the UI minimal and test in actual gym conditions for button placement and font sizes.
* Use Coil for image loading of URIs; do not store binary blobs inside the DB.
* Enforce unique exercise names at insert time (non-archived) to avoid ambiguity.
