package dev.pulse.core.prefs

import dev.pulse.core.db.PulseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserRole { REGULAR, MERCHANT }

/**
 * Who the user is. `null` means "not chosen yet", which shows the first-launch question exactly once.
 * The choice survives app kill/restart and can be changed from Settings.
 */
interface UserRoleRepository {
    val role: StateFlow<UserRole?>
    fun setRole(role: UserRole)
    /** Sign out: forget the choice so first-launch setup runs again. */
    fun clear()
}

/**
 * SQLDelight-backed, so one shared implementation persists identically on Android and iOS.
 * expect/actual storage isn't needed because SQLDelight already abstracts the platform driver.
 */
class SqlUserRoleRepository(private val db: PulseDatabase) : UserRoleRepository {
    private val _role = MutableStateFlow(read())
    override val role: StateFlow<UserRole?> = _role.asStateFlow()

    override fun setRole(role: UserRole) {
        db.pulseQueries.putValue(KEY, role.name)
        _role.value = role
    }

    override fun clear() {
        db.pulseQueries.deleteValue(KEY)
        _role.value = null
    }

    private fun read(): UserRole? = db.pulseQueries.getValue(KEY).executeAsOneOrNull()
        ?.let { stored -> UserRole.entries.firstOrNull { it.name == stored } }

    private companion object {
        const val KEY = "user.role"
    }
}

class InMemoryUserRoleRepository(initial: UserRole? = null) : UserRoleRepository {
    private val _role = MutableStateFlow(initial)
    override val role: StateFlow<UserRole?> = _role.asStateFlow()
    override fun setRole(role: UserRole) { _role.value = role }
    override fun clear() { _role.value = null }
}
