## Introduction and Intent

Chiron is a small, personal, offline-first Android workout tracking application. Its purpose is not to compete with commercial fitness apps, but to replace a notes app with something purpose-built, clean, and fast. The design goal is simplicity without ambiguity: every feature exists because it solves a real logging problem during training. There are no social features, no cloud sync, no templates, no analytics, and no defaults imposed by the app. Everything is user-defined.

Chiron is designed for a single user, on a single device. All data lives locally. Nothing is ever deleted; items can only be archived. The app is structured so that future expansion is possible, but the initial implementation must remain minimal, readable, and stable.

The core promise of Chiron is this: logging a workout should be faster than writing it in a notes app, and reviewing past workouts should be clearer than scrolling through text.

---

## Core Concepts and Terminology

Before specifying features and implementation, the core concepts must be clearly defined. These concepts are used consistently throughout the app and the database.

### Workout Session

A workout session represents a single training session performed at a specific time and place. A workout session is uniquely identified by three components:

1. **Day tag** – a user-defined label such as "Upper", "Legs", "Pull", etc.
2. **Date** – a calendar date, stored internally as a full UTC timestamp.
3. **Location tag** – a user-defined label such as "Home", "Gym A", "Gym B".

These three together define a workout. Two workouts may share the same day tag or location tag, but the full combination of (day, date, location) must be unique.

Each workout session contains a sequence of exercises performed during that session.

### Exercise

An exercise is a global, reusable entity such as "Bench Press", "Lat Pulldown", or "Barbell Curl". Exercises are defined once and reused across all workouts.

An exercise has:

* A unique immutable ID
* A user-editable name (must be unique among non-archived exercises)
* An optional image
* A global description explaining how to perform the exercise

Renaming an exercise updates all historical references. Exercises are never deleted, only archived.

### Exercise Entry

An exercise entry represents the use of a specific exercise within a specific workout session. The same exercise may appear multiple times within the same workout session, in different positions.

An exercise entry has:

* A reference to a workout session
* A reference to a global exercise
* An order index (position within the workout)
* A sequence type (normal, superset, dropset, etc.)
* An optional instance note describing how the exercise felt that day

### Set Entry

A set entry represents a single performed set of an exercise entry. Sets are ordered and may be partially filled.

A set entry has:

* A reference to an exercise entry
* A set index (order within the exercise)
* Weight (stored in pounds, nullable)
* Repetitions (integer, nullable)
* Timestamp
* Optional note

Partial sets are allowed. Weight and reps may be entered independently.

---

## Functional Requirements

### Navigation Structure

Chiron has exactly three primary tabs, displayed in a bottom navigation bar. The user can switch tabs either by tapping icons or by swiping horizontally.

Tabs:

1. Workout History
2. Exercises
3. Timer

There are no additional screens beyond these tabs and inline editors.

---

## Workout History Tab

The Workout History tab is the main screen of the app.

### Layout

* Top area: filter row displaying available day tags
* Main area: chronological list of workout cards
* Floating action button: "New Workout"

Workout cards are ordered by date, most recent first.

### Workout Card

Each workout card displays:

* Date (formatted in local time)
* Day tag
* Location tag
* Small preview of exercises performed

Tapping a card opens the workout in editable mode inline.

### Creating a New Workout

When the user taps "New Workout":

* A new workout session is created with the current timestamp
* The user selects or creates:

  * Day tag
  * Location tag
* The workout opens immediately in editable mode

The user is not required to complete the workout immediately. Changes are autosaved.

### Editing a Workout

Inside a workout:

* The header contains editable fields:

  * Day tag
  * Date (calendar picker)
  * Location tag

* Below the header is a vertical list of exercise entries.

### Exercise Entry UI

Each exercise entry appears as a rectangular block containing:

* Exercise name (tap to rename or change exercise)
* Optional exercise image thumbnail
* Sequence indicator (normal, superset, dropset)
* Instance note field (optional)

Below the exercise header are the sets for that exercise.

### Sets UI

Sets are displayed as small pills or rows showing:

* Weight
* Reps

Each value is individually editable. The user may:

* Enter weight first, reps later
* Enter reps first, weight later

Adding a set:

* Adds a new set entry
* Autofills weight and reps based on the most recent historical set for that exercise

### Ordering

* Exercise entries are ordered by their slot index (ascending)
* Sets are ordered by set index (ascending)

Reordering exercises updates slot indices atomically.

### Supersets and Dropsets

Each exercise entry has a `sequence_type` field:

* NONE
* SUPERSET
* DROPSET

Superset exercises share a group ID and are displayed grouped together.

### Filtering

The filter row shows all active day tags. Tapping a tag filters workouts by that day.

Archived tags are hidden from selection but visible in historical records.

---

## Exercises Tab

The Exercises tab manages the global exercise list.

### Layout

* Search bar at top
* Alphabetical list of exercises

### Exercise List Item

Each item displays:

* Exercise name
* Optional image

Tapping opens exercise details.

### Exercise Details

Shows:

* Exercise image
* Exercise name (editable)
* Global description (optional)
* Full history summary (read-only)

### Adding an Exercise

Exercises may be added:

* From the Exercises tab
* Inline while logging a workout

Exercise names must be unique among non-archived exercises.

### Images

When an image is added:

* The file is copied into the app’s internal `imgs/exercises/` directory
* The URI is stored in the database

---

## Timer Tab

The Timer tab contains two sub-tabs:

1. Stopwatch
2. Countdown Timer

### Stopwatch

* Start / Stop
* Lap recording

### Countdown Timer

* User-defined duration
* Start / Pause / Reset

No workout logic is tied to the timer. It is a standalone utility.

---

## Data Storage and Persistence

Chiron uses a local SQLite database via Room.

### Tables

#### WorkoutSession

* id (PK)
* day_tag (TEXT)
* date_utc (INTEGER)
* location_tag (TEXT)
* archived (BOOLEAN)

Unique constraint on (day_tag, date_utc, location_tag)

#### Exercise

* id (PK)
* name (TEXT, unique)
* image_uri (TEXT)
* description (TEXT)
* archived (BOOLEAN)

#### ExerciseEntry

* id (PK)
* workout_id (FK)
* exercise_id (FK)
* slot_index (INTEGER)
* sequence_type (INTEGER)
* group_id (INTEGER)
* instance_note (TEXT)

#### SetEntry

* id (PK)
* exercise_entry_id (FK)
* set_index (INTEGER)
* weight_lbs (REAL, nullable)
* reps (INTEGER, nullable)
* timestamp_utc (INTEGER)

#### UserSettings

* id (single row)
* display_in_kg (BOOLEAN)

---

## Units and PR Logic

* All weights are stored in pounds
* Display in kilograms is optional and purely visual
* Conversion: lbs / 2.2, rounded to nearest 0.5 kg

PR logic:

* For a given exercise and rep count
* The highest recorded weight is the PR
* Recomputed dynamically from historical data

---

## Architecture and Tech Stack

* Language: Kotlin
* UI: Jetpack Compose
* State: ViewModel + StateFlow
* Persistence: Room (SQLite)
* Image loading: Coil
* Preferences: DataStore
* Date handling: java.time

Single-Activity architecture.

---

## File Structure

```
Chiron/
├── imgs/
│   └── exercises/
├── db/
│   ├── schema_v1.sql
│   └── migrations/
├── src/
│   ├── MainActivity.kt
│   ├── data/
│   │   ├── ChironDatabase.kt
│   │   ├── dao/
│   │   ├── entities/
│   │   └── ChironRepository.kt
│   ├── ui/
│   │   ├── history/
│   │   ├── exercises/
│   │   └── timer/
│   ├── viewmodel/
│   ├── util/
│   └── prefs/
└── build.gradle
```
  