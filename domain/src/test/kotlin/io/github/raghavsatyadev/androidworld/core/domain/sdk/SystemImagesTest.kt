package io.github.raghavsatyadev.androidworld.core.domain.sdk

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SystemImagesTest {

    @Test
    fun `merge of the first fixture gives 5 images with 37_2 ps16k marked installed and appearing once`() {
        val listing = SdkListParser.parse(SdkListParserTest.fullListing)
        val images = SystemImages.merge(listing)
        images.size shouldBe 5

        val ps16kList = images.filter { it.path == "system-images/android-37.2/google_apis_playstore_ps16k/x86_64" }
        ps16kList.size shouldBe 1
        ps16kList.single().installed shouldBe true
    }

    @Test
    fun `merge order is CANARY, 37_2, 36_1, 34 ext10, 10`() {
        val listing = SdkListParser.parse(SdkListParserTest.fullListing)
        val images = SystemImages.merge(listing)
        images.map { it.apiLevel } shouldBe listOf("CANARY", "37.2", "36.1", "34", "10")
        images.map { it.apiLabel } shouldBe listOf(
            "API CANARY",
            "API 37.2",
            "API 36.1",
            "API 34 ext 10",
            "API 10",
        )
    }

    @Test
    fun `from parses api 34, extension 10, tag google_apis_playstore, abi x86_64`() {
        val pkg = SdkPackage(
            path = "system-images/android-34-ext10/google_apis_playstore/x86_64",
            version = "2.0.0 (+1)",
            description = "Google Play Intel x86_64 Atom System Image",
        )
        val img = requireNotNull(SystemImages.from(pkg, installed = false))
        img.apiLevel shouldBe "34"
        img.extension shouldBe 10
        img.tag shouldBe "google_apis_playstore"
        img.abi shouldBe "x86_64"
    }

    @Test
    fun `packageId replaces slashes with semicolons`() {
        val pkg = SdkPackage(
            path = "system-images/android-34-ext10/google_apis_playstore/x86_64",
            version = "2.0.0 (+1)",
            description = "Google Play Intel x86_64 Atom System Image",
        )
        val img = requireNotNull(SystemImages.from(pkg, installed = false))
        img.packageId shouldBe "system-images;android-34-ext10;google_apis_playstore;x86_64"
    }

    @Test
    fun `apiLabel formats api and extension`() {
        val pkgWithExt = SdkPackage(
            path = "system-images/android-34-ext10/google_apis_playstore/x86_64",
            version = "2.0.0",
            description = "Test",
        )
        val imgWithExt = requireNotNull(SystemImages.from(pkgWithExt, installed = false))
        imgWithExt.apiLabel shouldBe "API 34 ext 10"

        val pkgNoExt = SdkPackage(
            path = "system-images/android-Baklava/google_apis/x86_64",
            version = "1.0.0",
            description = "Test",
        )
        val imgNoExt = requireNotNull(SystemImages.from(pkgNoExt, installed = false))
        imgNoExt.apiLabel shouldBe "API Baklava"
    }

    @Test
    fun `tagLabel humanises known and custom tags and handles ps16k suffix`() {
        val pkgPs16k = SdkPackage(
            path = "system-images/android-37.2/google_apis_playstore_ps16k/x86_64",
            version = "7.0.0",
            description = "Test",
        )
        val imgPs16k = requireNotNull(SystemImages.from(pkgPs16k, installed = false))
        imgPs16k.tagLabel shouldBe "Google Play (16 KB pages)"

        val pkgWear = SdkPackage(
            path = "system-images/android-36.1/android-wear-signed/arm64-v8a",
            version = "1.0.0",
            description = "Test",
        )
        val imgWear = requireNotNull(SystemImages.from(pkgWear, installed = false))
        imgWear.tagLabel shouldBe "Android Wear Signed"
    }

    @Test
    fun `from returns null for platforms android-36`() {
        val platformPkg = SdkPackage(
            path = "platforms/android-36",
            version = "2.0.0",
            description = "Android SDK Platform 36",
        )
        SystemImages.from(platformPkg, installed = false) shouldBe null
    }

    @Test
    fun `hostAbis maps architectures correctly`() {
        SystemImages.hostAbis("amd64") shouldBe setOf("x86_64", "x86")
        SystemImages.hostAbis("x86_64") shouldBe setOf("x86_64", "x86")
        SystemImages.hostAbis("aarch64") shouldBe setOf("arm64-v8a")
        SystemImages.hostAbis("arm64") shouldBe setOf("arm64-v8a")
        SystemImages.hostAbis("unknown") shouldBe emptySet()
    }

    @Test
    fun `apiOrder compares numeric parts and codenames correctly`() {
        (SystemImages.apiOrder.compare("37.2", "37") > 0) shouldBe true
        (SystemImages.apiOrder.compare("37", "36.1") > 0) shouldBe true
        (SystemImages.apiOrder.compare("36.1", "9") > 0) shouldBe true
        (SystemImages.apiOrder.compare("CANARY", "37.2") > 0) shouldBe true
        (SystemImages.apiOrder.compare("Baklava", "CANARY") < 0) shouldBe true
    }
}
