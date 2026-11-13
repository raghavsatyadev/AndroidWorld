package io.github.raghavsatyadev.androidworld.core.domain.wifi

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlin.random.Random
import org.junit.jupiter.api.Test

class QrPairingTest {

    @Test
    fun `payload formats credentials into wifi mecard string`() {
        val credentials = QrPairingCredentials(serviceName = "studio-abc", password = "123456")
        QrPairing.payload(credentials) shouldBe "WIFI:T:ADB;S:studio-abc;P:123456;;"
    }

    @Test
    fun `escape escapes mecard special characters`() {
        QrPairing.escape("a;b,c:d\\e\"f") shouldBe "a\\;b\\,c\\:d\\\\e\\\"f"
    }

    @Test
    fun `generate produces valid service name and pairing code`() {
        val credentials = QrPairing.generate(Random(42))

        val serviceNamePattern = Regex("^studio-[a-z0-9]{10}$")
        val digitPattern = Regex("^\\d{6}$")

        serviceNamePattern.matches(credentials.serviceName) shouldBe true
        digitPattern.matches(credentials.password) shouldBe true

        val parseResult = PairingCode.parse(credentials.password)
        (parseResult is PairingCodeResult.Valid) shouldBe true

        credentials.toString() shouldNotContain credentials.password
        credentials.toString() shouldBe "QrPairingCredentials(serviceName=${credentials.serviceName}, password=******)"
    }
}
