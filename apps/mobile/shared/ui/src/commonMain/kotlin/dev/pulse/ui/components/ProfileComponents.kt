package dev.pulse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pulse.core.prefs.Avatars
import dev.pulse.core.prefs.ProfileRules
import dev.pulse.core.prefs.UserProfile
import dev.pulse.dsl.Node
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.neumorphic
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `profile.editor`: name, avatar or photo, optional phone. `mode: onboarding` is the first-launch
 * account setup (with "Get started"); `mode: edit` is Profile in the menu. Only the name is required.
 */
object ProfileEditorComponent : PulseComponent {
    override val key = "profile.editor"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val onboarding = node.props["mode"]?.jsonPrimitive?.contentOrNull == "onboarding"
        ProfileEditor(scope, onboarding, modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(scope: RenderScope, onboarding: Boolean, modifier: Modifier) {
    val session = scope.session
    val existing = session.profile
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var avatar by rememberSaveable { mutableStateOf(existing?.avatarId ?: Avatars.ids.first()) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone.orEmpty()) }
    var photo by remember { mutableStateOf(session.profilePhoto()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var roleError by remember { mutableStateOf<String?>(null) }
    val coroutines = rememberCoroutineScope()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Section(if (onboarding) "Set up your profile" else "Your details") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBadge(avatar, photo, 72, name = name)
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (session.canPickPhoto) {
                        TextAction(if (photo == null) "Upload a photo" else "Change photo", {
                            coroutines.launch { session.pickPhoto()?.let { photo = it } }
                        }, "profile-photo")
                    }
                    if (photo != null) TextAction("Use an avatar instead", { photo = null }, "profile-photo-remove")
                }
            }
            if (photo == null) {
                BasicText("Or pick an avatar", style = PulseType.label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatars.ids.forEach { id ->
                        AvatarBadge(
                            id, null, 40,
                            Modifier
                                .neumorphic(CircleShape, pressed = id == avatar)
                                .clickable(role = Role.RadioButton) { avatar = id }
                                .testTag("avatar-$id")
                                .padding(if (id == avatar) 5.dp else 2.dp),
                        )
                    }
                }
            }
            FormField("Name *", name, { name = it; nameError = null }, "What should we call you?", "profile-name", error = nameError)
            FormField(
                "Mobile number (optional)", phone, { phone = it; phoneError = null }, "98765 43210", "profile-phone",
                keyboardType = KeyboardType.Phone,
                error = phoneError,
                helper = if (existing?.phone != null && !existing.phoneVerified) "Not verified yet. SMS verification arrives with online accounts; your number stays on this phone."
                else "Optional. Kept on this phone only.",
            )
        }
        if (onboarding) {
            RolePicker(current = session.role, onSelect = { session.setRole(it); roleError = null }, onboarding = true)
            roleError?.let { BasicText(it, style = PulseType.label.copy(color = ErrorColor)) }
        }
        PrimaryButton(if (onboarding) "Get started" else "Save changes", {
            nameError = ProfileRules.nameProblem(name)
            val normalized = ProfileRules.normalizePhone(phone)
            phoneError = normalized.exceptionOrNull()?.message
            roleError = if (onboarding && session.role == null) "Choose User or Merchant" else null
            if (nameError == null && phoneError == null && roleError == null) {
                val number = normalized.getOrNull()
                session.saveProfile(
                    UserProfile(
                        name = name.trim(),
                        avatarId = avatar,
                        hasPhoto = photo != null,
                        phone = number,
                        // A changed number is a new, unverified number.
                        phoneVerified = existing?.phoneVerified == true && existing.phone == number,
                    ),
                )
                session.setProfilePhoto(photo)
                if (!onboarding) session.toast("Profile saved")
            }
        }, "profile-save")
        if (onboarding) {
            BasicText(
                "Your profile stays on this phone. We only ask for location when you search, and you can change everything later in Settings.",
                style = PulseType.label,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** `intent.clarify`: one question with tappable answers, instead of guessing an ambiguous request. */
object ClarifyComponent : PulseComponent {
    override val key = "intent.clarify"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val question = node.props["question"]?.jsonPrimitive?.contentOrNull ?: return
        val options = remember(node.props) {
            (node.props["options"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { o ->
                val obj = o as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val utterance = obj["utterance"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                label to utterance
            }
        }
        Column(modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section("Quick question") {
                BasicText(question, style = PulseType.title)
                BasicText("So you get the right places, not ones that just share a word.", style = PulseType.body)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEachIndexed { i, (label, utterance) ->
                        PulseChip(label, "clarify-$i") { scope.submitIntent(utterance) }
                    }
                }
            }
        }
    }
}

