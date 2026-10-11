package dev.pulse.ui.registry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.pulse.core.prefs.UserRole
import dev.pulse.ui.components.DefaultRegistry

/** The current role, for the rare component that needs it. Prefer [RoleGate] / [RoleExperience]. */
val LocalUserRole = staticCompositionLocalOf<UserRole?> { null }

/**
 * Everything that may differ by role, in one place. Today both roles share one experience;
 * a merchant-specific UI later is a one-line change in [RoleExperience.forRole]: no if/else
 * spread across screens.
 */
data class RoleExperience(
    /** Components available to this role's generated surfaces. */
    val registry: ComponentRegistry,
) {
    companion object {
        private val Shared = RoleExperience(registry = DefaultRegistry)

        fun forRole(role: UserRole): RoleExperience = when (role) {
            UserRole.REGULAR -> Shared
            UserRole.MERCHANT -> Shared // future: RoleExperience(registry = MerchantRegistry)
        }
    }
}

/** Renders [content] with the experience for [role] and exposes the role to the tree. */
@Composable
fun RoleGate(role: UserRole, content: @Composable (RoleExperience) -> Unit) {
    CompositionLocalProvider(LocalUserRole provides role) {
        content(RoleExperience.forRole(role))
    }
}
