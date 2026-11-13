package io.github.raghavsatyadev.androidworld.core.domain.wifi

enum class MdnsServiceKind {
    Pairing,
    Connect,
    Other,
}

data class MdnsService(
    val instanceName: String,
    val serviceType: String,
    val endpoint: WifiEndpoint,
) {
    val kind: MdnsServiceKind = when {
        serviceType.contains("_adb-tls-pairing") -> MdnsServiceKind.Pairing
        serviceType.contains("_adb-tls-connect") -> MdnsServiceKind.Connect
        else -> MdnsServiceKind.Other
    }
}

fun parseMdnsServices(output: String): List<MdnsService> {
    val lines = output.lines()
    val services = mutableListOf<MdnsService>()
    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("List of discovered mdns services")) {
            continue
        }
        val fields = if (line.contains('\t')) {
            line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            line.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        }
        if (fields.size != 3) {
            continue
        }
        val instanceName = fields[0]
        val serviceType = fields[1]
        val endpointResult = WifiEndpoint.parse(fields[2])
        if (endpointResult is WifiEndpointResult.Valid) {
            services.add(MdnsService(instanceName = instanceName, serviceType = serviceType, endpoint = endpointResult.endpoint))
        }
    }
    return services
}

fun List<MdnsService>.findPairing(serviceName: String): MdnsService? =
    firstOrNull { it.kind == MdnsServiceKind.Pairing && it.instanceName == serviceName }

fun List<MdnsService>.findConnect(host: String): MdnsService? =
    firstOrNull { it.kind == MdnsServiceKind.Connect && it.endpoint.host.value == host }
