package io.github.dyzydery.bahasakotak.io.pack

/** What the database currently holds that is relevant to importing one pack. */
data class InstalledContent(
    val installedVersion: Int?,
    /** Active (not retired) topic, lesson and item IDs currently attributed to this pack. */
    val packTopicIds: Set<String>,
    val packLessonIds: Set<String>,
    val packItemIds: Set<String>,
    /** Every item ID in the database, from any pack. */
    val allItemIds: Set<String>,
)

sealed interface ImportPlan {
    data class Apply(
        val pack: PackDto,
        val isUpgrade: Boolean,
        val newItemIds: Set<String>,
        val updatedItemIds: Set<String>,
        val retiredItemIds: Set<String>,
        val retiredLessonIds: Set<String>,
        val retiredTopicIds: Set<String>,
    ) : ImportPlan

    data class AlreadyInstalled(val version: Int) : ImportPlan
    data class Downgrade(val installed: Int, val offered: Int) : ImportPlan
}

/**
 * Decides what an import does without touching the database, so idempotency rules are unit-testable.
 * Content is upserted by ID; anything this pack used to contain but no longer does is retired, never deleted,
 * so learning progress and the answer log survive.
 */
object ImportPlanner {
    fun plan(pack: PackDto, installed: InstalledContent): ImportPlan {
        val current = installed.installedVersion
        if (current != null) {
            if (pack.packVersion == current) return ImportPlan.AlreadyInstalled(current)
            if (pack.packVersion < current) return ImportPlan.Downgrade(current, pack.packVersion)
        }
        val itemIds = pack.items.map { it.id }.toSet()
        return ImportPlan.Apply(
            pack = pack,
            isUpgrade = current != null,
            newItemIds = itemIds - installed.allItemIds,
            updatedItemIds = itemIds intersect installed.allItemIds,
            retiredItemIds = installed.packItemIds - itemIds,
            retiredLessonIds = installed.packLessonIds - pack.lessons.map { it.id }.toSet(),
            retiredTopicIds = installed.packTopicIds - pack.topics.map { it.id }.toSet(),
        )
    }
}
