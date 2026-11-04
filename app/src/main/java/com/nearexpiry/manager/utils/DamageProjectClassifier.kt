package com.nearexpiry.manager.utils

/** Projects whose names contain "damage" use the Damage + Expiry workflow. */
object DamageProjectClassifier {
    fun isDamageProject(projectName: String?): Boolean =
        projectName?.contains("damage", ignoreCase = true) == true
}
