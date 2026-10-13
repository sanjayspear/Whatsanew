package dev.pulse.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pulse.core.data.MerchantDraft
import dev.pulse.core.data.MerchantPosts
import dev.pulse.core.data.OfferingType
import dev.pulse.core.prefs.UserRole
import dev.pulse.dsl.Node
import dev.pulse.ui.Screen
import dev.pulse.ui.design.Pulse
import dev.pulse.ui.design.PulseIcons
import dev.pulse.ui.design.PulseType
import dev.pulse.ui.design.glass
import dev.pulse.ui.registry.PulseComponent
import dev.pulse.ui.registry.RenderScope
import kotlinx.coroutines.launch

/**
 * `merchant.publish`: the form merchants use to announce a new event, release, shop or service.
 * Validation and moderation run before anything is published; problems are listed in plain words.
 */
object MerchantPublishComponent : PulseComponent {
    override val key = "merchant.publish"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val session = scope.session
        if (session.role != UserRole.MERCHANT) {
            Section(null, modifier) {
                BasicText("Publishing is for merchant accounts.", style = PulseType.title)
                BasicText("Switch your account type to Merchant in Settings to announce events, launches and offers.", style = PulseType.body)
                TextAction("Open Settings", { session.navigate(Screen.Settings) }, "publish-to-settings")
            }
            return
        }
        var title by rememberSaveable { mutableStateOf("") }
        var type by rememberSaveable { mutableStateOf<OfferingType?>(null) }
        var description by rememberSaveable { mutableStateOf("") }
        var area by rememberSaveable { mutableStateOf("") }
        var launch by rememberSaveable { mutableStateOf("") }
        var event by rememberSaveable { mutableStateOf("") }
        var link by rememberSaveable { mutableStateOf("") }
        var image by remember { mutableStateOf<ByteArray?>(null) }
        var problems by remember { mutableStateOf<List<String>>(emptyList()) }
        val coroutines = rememberCoroutineScope()

        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("What are you announcing?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OfferingType.entries.forEach { t -> PulseChip(t.label, "offering-${t.name.lowercase()}", selected = type == t) { type = t } }
                }
                FormField("Title *", title, { title = it }, "e.g. Grand opening: Juice Junction", "publish-title")
                FormField("Description *", description, { description = it }, "What is it, who is it for, any offer", "publish-description", singleLine = false)
            }
            Section("Where and when") {
                FormField("Area *", area, { area = it }, "e.g. Indiranagar", "publish-area", helper = "A Bengaluru neighbourhood.")
                TextAction("Use my current location", { area = MerchantPosts.CURRENT_LOCATION }, "publish-here")
                FormField("Launch date", launch, { launch = it }, "YYYY-MM-DD (empty = today)", "publish-launch", keyboardType = KeyboardType.Number)
                FormField(
                    if (type in setOf(OfferingType.EVENT, OfferingType.SHOW, OfferingType.CONCERT)) "Event date *" else "Event date",
                    event, { event = it }, "YYYY-MM-DD", "publish-event", keyboardType = KeyboardType.Number,
                )
            }
            Section("Booking and images") {
                FormField(
                    "Booking or website link", link, { link = it }, "https://…", "publish-link",
                    keyboardType = KeyboardType.Uri,
                    helper = "Adds Book Now to your listing. Secure (https) links only.",
                )
                val bitmap = rememberBitmap(image)
                if (bitmap != null) {
                    Image(bitmap, "Listing image", Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                }
                if (session.canPickPhoto) {
                    Row {
                        TextAction(if (image == null) "Add an image" else "Change image", { coroutines.launch { session.pickPhoto()?.let { image = it } } }, "publish-image")
                        if (image != null) TextAction("Remove image", { image = null }, "publish-image-remove")
                    }
                }
            }
            if (problems.isNotEmpty()) {
                Section("Please fix") {
                    problems.forEach { BasicText("•  $it", style = PulseType.body.copy(color = ErrorColor)) }
                }
            }
            PrimaryButton("Publish", {
                when (val r = session.publish(MerchantDraft(title, type, description, area, launch, event, link), image)) {
                    is MerchantPosts.Outcome.Rejected -> problems = r.problems
                    is MerchantPosts.Outcome.Published -> {
                        problems = emptyList()
                        title = ""; type = null; description = ""; area = ""; launch = ""; event = ""; link = ""; image = null
                        session.toast("Published: it now appears in Search Recently Launched")
                        session.navigate(Screen.MyPosts)
                    }
                }
            }, "publish-submit")
            BasicText(
                "Posts are checked for missing details, unsafe links, duplicates and misleading wording. For now they appear on this phone; publishing to everyone arrives with merchant verification.",
                style = PulseType.label,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** `merchant.posts`: the merchant's own published offerings, with delete. */
object MerchantPostsComponent : PulseComponent {
    override val key = "merchant.posts"

    @Composable
    override fun Render(node: Node, scope: RenderScope, modifier: Modifier) {
        val session = scope.session
        val posts = remember(scope.dataVersion) { session.merchantPosts() }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (posts.isEmpty()) {
                Section(null) {
                    BasicText("You haven't published anything yet.", style = PulseType.body)
                    TextAction("Publish an offering", { session.navigate(Screen.Publish) }, "posts-publish")
                }
            }
            posts.forEach { post ->
                Section(post.type.label) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(post.title, style = PulseType.cardTitle)
                            BasicText(
                                listOfNotNull(
                                    post.areaName,
                                    Format.launchLine(post.toListing(), scope.nowMs()),
                                    post.eventAtEpochMs?.let { "Event ${Format.date(it, scope.nowMs())}" },
                                ).joinToString("  ·  "),
                                style = PulseType.label,
                            )
                        }
                        NeuButton(PulseIcons.Trash, null, { session.removePost(post.id); session.toast("Post removed") }, size = 36, description = "Delete ${post.title}")
                    }
                    BasicText(post.description, style = PulseType.body, maxLines = 3)
                    BasicText("Live on this phone · pending verification for everyone", style = PulseType.label.copy(color = Pulse.colors.accent))
                }
            }
        }
    }
}
