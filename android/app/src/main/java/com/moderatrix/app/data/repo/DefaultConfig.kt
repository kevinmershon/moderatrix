package com.moderatrix.app.data.repo

import com.moderatrix.app.data.db.ActivityDefEntity
import com.moderatrix.app.data.db.CategoryEntity

object DefaultConfig {
    val categories = listOf(
        CategoryEntity("existential", "Existential"),
        CategoryEntity("nurturing", "Nurturing"),
        CategoryEntity("neuroplasticity", "Neuroplasticity"),
        CategoryEntity("creative", "Creative"),
        CategoryEntity("contradictory_factors", "Contradictory Factors")
    ).mapIndexed { index, category -> category.copy(sortOrder = index) }

    val activities = listOf(
        ActivityDefEntity("productive_work", "existential", "Productive Work", 5, false),
        ActivityDefEntity("cook_dinner", "existential", "Cook Dinner", 3, false, availableMorning = false),
        ActivityDefEntity("work_out", "existential", "Work out", 7, false),
        ActivityDefEntity("sunlight", "existential", "Sunlight / Outside time", 7, false, availableNight = false),
        ActivityDefEntity("evening_walk", "existential", "Evening Walk", 3, false, availableMorning = false, availableNoon = false),
        ActivityDefEntity("brush_cat", "nurturing", "Brush Cat", 3, false),
        ActivityDefEntity("be_social", "nurturing", "Be Social / Go Out", 3, false),
        ActivityDefEntity("household_chores", "nurturing", "Household chores", 3, false),
        ActivityDefEntity("wordle", "neuroplasticity", "Wordle", 5, false),
        ActivityDefEntity("duolingo", "neuroplasticity", "Duolingo", 7, false),
        ActivityDefEntity("read", "neuroplasticity", "Read", 3, false),
        ActivityDefEntity("piano", "neuroplasticity", "Piano", 3, false),
        ActivityDefEntity("hobby_coding", "creative", "Hobby Coding", 3, false),
        ActivityDefEntity("music_composition", "creative", "Music Composition", 2, false),
        ActivityDefEntity("learning_blender", "creative", "Learning Blender", 3, false),

        // Contradictory factors: negative influences tracked morning/noon/night like activities,
        // but with no weekly target (0 = not applicable — the goal is minimizing, not hitting a count).
        ActivityDefEntity("reprimanded_at_work", "contradictory_factors", "Reprimanded at Work", 0, false),
        ActivityDefEntity("relationship_conflict", "contradictory_factors", "Relationship Conflict", 0, false),
        ActivityDefEntity("poor_quality_sleep", "contradictory_factors", "Poor quality sleep", 0, false, availableNoon = false, availableNight = false),
        ActivityDefEntity("refined_sugars", "contradictory_factors", "Refined sugars", 0, false),
        ActivityDefEntity("processed_foods", "contradictory_factors", "Processed foods", 0, false),
        ActivityDefEntity("alcohol_kombucha_soda", "contradictory_factors", "Alcohol / Kombucha / Soda", 0, false),
        ActivityDefEntity("excess_snacking", "contradictory_factors", "Excess Snacking", 0, false),
        ActivityDefEntity("high_leisure_screen_time", "contradictory_factors", "High Leisure Screen Time", 0, false)
    ).let { list ->
        // sortOrder is relative within each category (recomputed per-category so drag reordering
        // in the config screen only needs to touch the affected category's activities).
        list.groupBy { it.categoryId }
            .flatMap { (_, group) -> group.mapIndexed { index, activity -> activity.copy(sortOrder = index) } }
    }
}
