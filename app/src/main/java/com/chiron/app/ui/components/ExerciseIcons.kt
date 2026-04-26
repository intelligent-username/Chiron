package com.chiron.app.ui.components

// Available exercise icons — name matches the icon ID stored in DB, fileName matches assets/icons/
data class ExerciseIcon(val name: String, val fileName: String)

val AVAILABLE_ICONS = listOf(
    ExerciseIcon("dumbell",                "dumbell.svg"),
    ExerciseIcon("benchpress",             "benchpress.svg"),
    ExerciseIcon("chest-press",            "chest-press.svg"),
    ExerciseIcon("incline-bench",          "incline-bench.svg"),
    ExerciseIcon("incline-press-machine",  "incline-press-machine.svg"),
    ExerciseIcon("incline-dumbbell-press", "incline-db-press.svg"),
    ExerciseIcon("overhead-press",         "overhead-press.svg"),
    ExerciseIcon("overhead-press-machine", "overhead-press-machine.svg"),

    ExerciseIcon("incline-curl",           "incline-curl.svg"),
    ExerciseIcon("curl",                   "curl.svg"),
    ExerciseIcon("hammer-curl",            "hammer-curl.svg"),
    ExerciseIcon("preacher-curl",          "preacher-curl.svg"),
    ExerciseIcon("overhand-cable-curl",    "overhand-cable-curl.svg"),
    ExerciseIcon("finger-curl",            "finger-curl.svg"),

    ExerciseIcon("deadlift",               "deadlift.svg"),
    ExerciseIcon("zercher-deadlift",       "zercher-deadlift.svg"),
    ExerciseIcon("farmers-carry",          "farmers-carry.svg"),

    ExerciseIcon("jump",                   "jump.svg"),
    ExerciseIcon("leg-curl",               "leg-curl.svg"),
    ExerciseIcon("leg-extension",          "leg-extension.svg"),
    ExerciseIcon("leg-raise",              "leg-raise.svg"),
    ExerciseIcon("squat",                  "squat.svg"),
    ExerciseIcon("bulgarian-split-squat",  "BSS.svg"),
    ExerciseIcon("leg-press",              "leg-press.svg"),
    ExerciseIcon("calf-machine",           "calf-machine.svg"),
    ExerciseIcon("sl-calf-raise",          "sl-calf-raise.svg"),
    ExerciseIcon("hack-squat-machine",     "hack-squat-machine.svg"),
    ExerciseIcon("lunge",                  "lunge.svg"),
    ExerciseIcon("hip-thrust",             "hip-thrust.svg"),

    ExerciseIcon("machine",                "machine.svg"),
    ExerciseIcon("pulldown",               "pulldown.svg"),
    ExerciseIcon("pushdown",               "pushdown.svg"),
    ExerciseIcon("overhead-extension",     "overhead-extension.svg"),

    ExerciseIcon("45-plate",               "plate-ta.svg"),
    ExerciseIcon("25-plate",               "plate-tb.svg"),
    ExerciseIcon("20-plate",               "plate-tc.svg"),
    ExerciseIcon("10-plate",               "plate-td.svg"),

    ExerciseIcon("pull-up",                "pull-up.svg"),
    ExerciseIcon("neutral-pullup",         "neutral-pull.svg"),
    ExerciseIcon("ring-pullup",            "ring-pullup.svg"),
    ExerciseIcon("push-up",                "push-up.svg"),
    ExerciseIcon("dip",                    "dip.svg"),
    ExerciseIcon("ring-dip",               "ring-dip.svg"),
    ExerciseIcon("rings",                  "rings.svg"),

    ExerciseIcon("treadmill",              "treadmill.svg"),
    ExerciseIcon("stationary-bike",        "stationary-bike.svg"),
    ExerciseIcon("heart-rate",             "heart-rate.svg"),

    ExerciseIcon("lateral-raise",          "lateral-raise.svg"),
    ExerciseIcon("barbell",                "barbell.svg"),
    ExerciseIcon("smith",                  "smith.svg"),
    ExerciseIcon("cables",                 "cables.svg"),
    ExerciseIcon("bands",                  "bands.svg"),

    ExerciseIcon("fly-machine",            "fly-machine.svg"),
    ExerciseIcon("peck-deck",              "deck.svg"),
    ExerciseIcon("cable-crossover",        "cable-crossover.svg"),

    ExerciseIcon("ab-twister",             "ab-twister.svg"),
    ExerciseIcon("landmine-rotation",      "landmine-rotation.svg"),
    ExerciseIcon("medicine-ball",          "medicine-ball.svg"),

    ExerciseIcon("machine-row",            "machine-row.svg"),
    ExerciseIcon("single-arm-row",         "single-arm-row.svg"),
    ExerciseIcon("barbell-row",            "barbell-row.svg"),

    ExerciseIcon("itrot",                  "internal-rotation.svg"),
    ExerciseIcon("neck-curl",              "neck-curl.svg"),
    ExerciseIcon("lateral-neck",           "lateral-neck.svg"),

    ExerciseIcon("link",                   "link.svg"),
    ExerciseIcon("smiley",                 "smiley.svg"),
    ExerciseIcon("sit-up",                 "sit-up.svg"),
    ExerciseIcon("kettlebell",             "kettlebell.svg"),
)
