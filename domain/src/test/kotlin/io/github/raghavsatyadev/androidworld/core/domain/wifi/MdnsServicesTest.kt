package io.github.raghavsatyadev.androidworld.core.domain.wifi

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MdnsServicesTest {

    @Test
    fun `header-only output produces empty list`() {
        val output = "List of discovered mdns services\n"
        parseMdnsServices(output) shouldBe emptyList()
    }

    @Test
    fun `parseMdnsServices parses services and filters malformed lines`() {
        val sample =
            "List of discovered mdns services\n" +
                "adb-R5CT1234-AbCdEf\t_adb-tls-connect._tcp\t192.168.1.20:37099\n" +
                "studio-abc123defg\t_adb-tls-pairing._tcp.\t192.168.1.20:41235\n" +
                "broken line\n"

        val services = parseMdnsServices(sample)
        services.size shouldBe 2

        val pairing = services.findPairing("studio-abc123defg")
        pairing?.endpoint?.render() shouldBe "192.168.1.20:41235"
        pairing?.kind shouldBe MdnsServiceKind.Pairing

        val connect = services.findConnect("192.168.1.20")
        connect?.endpoint?.port?.value shouldBe 37099
        connect?.kind shouldBe MdnsServiceKind.Connect

        services.findPairing("other") shouldBe null
    }
}
