package com.nearexpiry.manager.domain.model

/**
 * How to resolve a duplicate (same POS code + expiry date + unit) when
 * adding/merging items — used by scan, manual entry, and copy/move
 * between projects.
 *
 *  • ADD     — sum the incoming quantity onto the existing one.
 *  • REPLACE — overwrite the existing quantity with the incoming one.
 */
enum class MergeMode { ADD, REPLACE }
