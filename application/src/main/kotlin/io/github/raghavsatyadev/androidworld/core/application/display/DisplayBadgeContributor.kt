package io.github.raghavsatyadev.androidworld.core.application.display

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.devicecontext.BadgeContributor
import io.github.raghavsatyadev.androidworld.core.domain.devicecontext.OverrideSummaryContributor
import io.github.raghavsatyadev.androidworld.core.domain.nav.NavigationBadge
import io.github.raghavsatyadev.androidworld.core.domain.nav.ViewId

/**
 * The Device rail entry's display-override [BadgeContributor] (`design/README.md` §2: amber when
 * font scale != 1 or density != 100%; those controls live in the Device view). Rather than tracking font-scale/density
 * values a second time, this reuses [fontScaleOverrides]/[densityOverrides] — the same task 026/027
 * [OverrideSummaryContributor]s the status bar's override chip already reads — as the single source
 * of "is this serial currently overridden," so the badge and the override chip can never disagree
 * about what counts as an override.
 */
class DisplayBadgeContributor(
    private val fontScaleOverrides: OverrideSummaryContributor,
    private val densityOverrides: OverrideSummaryContributor,
) : BadgeContributor {

    override val viewId: ViewId = ViewId.Device

    override fun badgeFor(serial: DeviceSerial?): NavigationBadge =
        if (fontScaleOverrides.overridesFor(serial).isNotEmpty() || densityOverrides.overridesFor(serial).isNotEmpty()) {
            NavigationBadge.Attention
        } else {
            NavigationBadge.None
        }
}
