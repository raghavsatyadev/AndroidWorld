package io.github.raghavsatyadev.androidworld.core.adapters.emulator

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor

/**
 * Attaches an `authorization: Bearer <token>` metadata header to all outgoing gRPC calls.
 */
class BearerTokenInterceptor(private val token: String) : ClientInterceptor {

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                headers.put(AUTHORIZATION_METADATA_KEY, "Bearer $token")
                super.start(responseListener, headers)
            }
        }
    }

    companion object {
        val AUTHORIZATION_METADATA_KEY: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
