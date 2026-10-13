package dev.pulse.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.pulse.core.prefs.UserProfile
import dev.pulse.core.prefs.UserRole
import dev.pulse.ui.Screen
import dev.pulse.ui.components.ActionRow
import dev.pulse.ui.components.AvatarBadge
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass

/**
 * The hamburger menu. Kept short; merchants get their two publishing entries, users don't.
 * Sign Out sits last, apart from navigation.
 */
@Composable
fun MenuDrawer(
    open: Boolean,
    profile: UserProfile?,
    photo: ByteArray?,
    role: UserRole?,
    onSelect: (Screen) -> Unit,
    onRecentlyLaunched: () -> Unit,
    onSearchAround: () -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .testTag("menu-scrim"),
        )
    }
    AnimatedVisibility(open, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(0.84f)
                .background(Pulse.colors.backgroundTop, RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {} // swallow taps
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("menu-drawer"),
        ) {
            Row(
                Modifier.fillMaxWidth().glass().clickable { onSelect(Screen.Profile) }.padding(16.dp).testTag("menu-profile"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarBadge(profile?.avatarId, photo, 52, name = profile?.name)
                Column(Modifier.padding(start = 14.dp)) {
                    BasicText(profile?.name ?: "Your profile", style = PulseType.cardTitle)
                    BasicText(if (role == UserRole.MERCHANT) "Merchant · View profile" else "User · View profile", style = PulseType.label)
                }
            }
            Column(Modifier.padding(top = 16.dp)) {
                ActionRow(PulseIcons.Search, "Search Around", "menu-search") { onSearchAround() }
                ActionRow(PulseIcons.Rocket, "Search Recently Launched", "menu-recent") { onRecentlyLaunched() }
                ActionRow(PulseIcons.Clock, "Search History", "menu-history") { onSelect(Screen.History) }
                ActionRow(PulseIcons.Bookmark, "Saved Items", "menu-saved") { onSelect(Screen.Saved) }
                if (role == UserRole.MERCHANT) {
                    ActionRow(PulseIcons.Megaphone, "Publish an offering", "menu-publish") { onSelect(Screen.Publish) }
                    ActionRow(PulseIcons.Ticket, "My posts", "menu-posts") { onSelect(Screen.MyPosts) }
                }
                ActionRow(PulseIcons.Settings, "Settings", "menu-settings") { onSelect(Screen.Settings) }
                ActionRow(PulseIcons.Help, "Help and Support", "menu-help") { onSelect(Screen.Help) }
                ActionRow(PulseIcons.Info, "About", "menu-about") { onSelect(Screen.About) }
                ActionRow(PulseIcons.Shield, "Privacy Policy and Terms", "menu-legal") { onSelect(Screen.Legal) }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().glass().padding(horizontal = 12.dp)) {
                ActionRow(PulseIcons.Logout, "Sign Out", "menu-signout") { onSignOut() }
            }
        }
    }
}
