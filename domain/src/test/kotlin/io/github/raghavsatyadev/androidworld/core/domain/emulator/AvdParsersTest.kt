package io.github.raghavsatyadev.androidworld.core.domain.emulator

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AvdParsersTest {

    private val esc = "\u001B"

    // Captured from `android emulator list --long` (CLI 1.0), escape codes and CRLF included.
    private val listing = listOf(
        "$esc[1mAVD ID$esc[21m$esc[0m                   $esc[1mAVD Name$esc[21m$esc[0m                      " +
            "$esc[1mAPI Level$esc[21m$esc[0m      $esc[1mStatus$esc[21m$esc[0m         $esc[1mSerial$esc[21m$esc[0m",
        "Phone_API37              Phone_API37                   android-37.2   $esc[32mOnline$esc[39m$esc[0m         emulator-5554",
        "TV_API34                 TV_API34                      android-34     $esc[31mOffline$esc[39m$esc[0m",
    ).joinToString("\r\n")

    @Test
    fun `a coloured listing parses into running and stopped AVDs`() {
        AvdListParser.parse(listing) shouldBe listOf(
            Avd("Phone_API37", "Phone_API37", "android-37.2", AvdStatus.Online, "emulator-5554"),
            Avd("TV_API34", "TV_API34", "android-34", AvdStatus.Offline),
        )
    }

    @Test
    fun `a name with spaces stays whole`() {
        val text = "AVD ID      AVD Name         API Level  Status   Serial\nPixel_9     My Pixel 9 Pro   android-35 Online   emulator-5556\n"
        AvdListParser.parse(text).single().name shouldBe "My Pixel 9 Pro"
    }

    @Test
    fun `output without the table header yields nothing`() {
        AvdListParser.parse("No AVDs found.").shouldBeEmpty()
    }

    @Test
    fun `profiles are one per line`() {
        AvdListParser.parseProfiles("\nlarge_desktop\r\nmedium_phone\nsome warning text\n") shouldBe listOf("large_desktop", "medium_phone")
    }

    @Test
    fun `config tags pick the form factor`() {
        AvdConfigParser.parse("tag.id=google-tv\nhw.device.name=tv_1080p") shouldBe AvdConfig(FormFactor.Tv, "tv_1080p")
        AvdConfigParser.parse("tag.id=android-wear\nhw.device.name=wearos_large_round").formFactor shouldBe FormFactor.Wear
        AvdConfigParser.parse("tag.id=google_apis_playstore\nhw.device.name=pixel_8") shouldBe AvdConfig(FormFactor.Phone, "pixel_8")
        AvdConfigParser.parse("tag.id=google_apis\nhw.device.name=pixel_tablet").formFactor shouldBe FormFactor.Tablet
        AvdConfigParser.parse("").formFactor shouldBe FormFactor.Phone
    }
}
