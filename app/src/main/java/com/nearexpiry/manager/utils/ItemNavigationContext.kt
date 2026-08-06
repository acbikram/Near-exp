package com.nearexpiry.manager.utils

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the ordered list of item ids the user was browsing in History right
 * before opening an item's Detail screen — whatever that list looked like at
 * that moment (current filter/sort/search applied). The Detail screen uses
 * this to support swipe left/right to the next/previous item, in that exact
 * same order, without needing to serialize the whole list through
 * navigation arguments.
 *
 * This is a simple in-memory snapshot, not reactive: if items change while
 * browsing (e.g. one gets deleted elsewhere), the swipe order doesn't update
 * until History is revisited. That matches how swipe-through works in most
 * list→detail UIs (e.g. photo galleries) and keeps this simple and reliable.
 */
@Singleton
class ItemNavigationContext @Inject constructor() {
    @Volatile
    var orderedIds: List<Long> = emptyList()
        private set

    fun set(ids: List<Long>) {
        orderedIds = ids
    }

    /** The id after [currentId] in the held order, or null at the end / if not found. */
    fun nextOf(currentId: Long): Long? {
        val idx = orderedIds.indexOf(currentId)
        if (idx == -1 || idx >= orderedIds.lastIndex) return null
        return orderedIds[idx + 1]
    }

    /** The id before [currentId] in the held order, or null at the start / if not found. */
    fun previousOf(currentId: Long): Long? {
        val idx = orderedIds.indexOf(currentId)
        if (idx <= 0) return null
        return orderedIds[idx - 1]
    }
}
