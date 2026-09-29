package org.shilpo.laboon.auth

class OnboardingProgress(private val store: KeyValueStore) {

    val hasCompletedPermissions: Boolean
        get() = store.getBoolean(KEY_PERMISSIONS_COMPLETED)

    fun markPermissionsCompleted() {
        store.putBoolean(KEY_PERMISSIONS_COMPLETED, true)
    }

    fun reset() {
        store.remove(KEY_PERMISSIONS_COMPLETED)
    }

    private companion object {
        const val KEY_PERMISSIONS_COMPLETED = "permissions_completed"
    }
}
