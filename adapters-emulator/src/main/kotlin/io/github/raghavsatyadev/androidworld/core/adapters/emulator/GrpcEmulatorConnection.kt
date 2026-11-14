package io.github.raghavsatyadev.androidworld.core.adapters.emulator

import com.android.emulator.control.EmulatorControllerGrpc
import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.MouseEvent
import com.android.emulator.control.ParameterValue
import com.android.emulator.control.PhysicalModelValue
import com.android.emulator.control.Posture
import com.android.emulator.control.Touch
import com.android.emulator.control.TouchEvent
import com.google.protobuf.Empty
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorConnection
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorEndpoint
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorFrame
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorHardware
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorHardwareParser
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorInput
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.FoldPosture
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.ScreenRotation
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * gRPC-backed implementation of [EmulatorConnection] communicating with the emulator's
 * `EmulatorController` service.
 */
class GrpcEmulatorConnection(
    endpoint: EmulatorEndpoint,
    private val channel: ManagedChannel = createChannel(endpoint),
) : EmulatorConnection {

    private val asyncStub = EmulatorControllerGrpc.newStub(channel)
    private val blockingStub = EmulatorControllerGrpc.newBlockingStub(channel)

    override fun frames(maxWidth: Int, maxHeight: Int): Flow<EmulatorFrame> = callbackFlow {
        val format = ImageFormat.newBuilder()
            .setFormat(ImageFormat.ImgFormat.RGBA8888)
            .setWidth(maxWidth)
            .setHeight(maxHeight)
            .setDisplay(0)
            .build()

        var callObserver: ClientCallStreamObserver<ImageFormat>? = null

        val observer = object : ClientResponseObserver<ImageFormat, Image> {
            override fun beforeStart(requestStream: ClientCallStreamObserver<ImageFormat>) {
                callObserver = requestStream
            }

            override fun onNext(image: Image) {
                val fmt = image.format
                val w = if (fmt.width != 0) fmt.width else image.width
                val h = if (fmt.height != 0) fmt.height else image.height
                val bytes = image.image.toByteArray()
                if (w > 0 && h > 0 && bytes.size >= w * h * 4) {
                    val rotation = ScreenRotation.ofIndex(fmt.rotation.rotationValue)
                    val folded = if (fmt.hasFoldedDisplay()) fmt.foldedDisplay else null
                    trySend(
                        EmulatorFrame(
                            w, h, bytes, image.seq, rotation,
                            foldedWidth = folded?.width ?: 0,
                            foldedHeight = folded?.height ?: 0,
                        ),
                    )
                }
            }

            override fun onError(t: Throwable) {
                close(t)
            }

            override fun onCompleted() {
                close()
            }
        }

        asyncStub.streamScreenshot(format, observer)

        awaitClose {
            callObserver?.cancel("closed", null)
        }
    }.buffer(Channel.CONFLATED)

    override suspend fun send(input: EmulatorInput) {
        withContext(Dispatchers.IO) {
            when (input) {
                is EmulatorInput.Touch -> {
                    val touch = Touch.newBuilder()
                        .setX(input.x)
                        .setY(input.y)
                        .setIdentifier(input.id)
                        .setPressure(if (input.pressed) 1 else 0)
                        .build()
                    val event = TouchEvent.newBuilder()
                        .addTouches(touch)
                        .build()
                    blockingStub.sendTouch(event)
                }
                is EmulatorInput.Mouse -> {
                    val event = MouseEvent.newBuilder()
                        .setX(input.x)
                        .setY(input.y)
                        .setButtons(input.buttons)
                        .setDisplay(0)
                        .build()
                    blockingStub.sendMouse(event)
                }
                is EmulatorInput.Key -> {
                    val event = KeyboardEvent.newBuilder()
                        .setEventType(KeyboardEvent.KeyEventType.keypress)
                        .setKey(input.key)
                        .build()
                    blockingStub.sendKey(event)
                }
                is EmulatorInput.Text -> {
                    val event = KeyboardEvent.newBuilder()
                        .setText(input.text)
                        .build()
                    blockingStub.sendKey(event)
                }
            }
        }
    }

    override suspend fun rotate(rotation: ScreenRotation) {
        withContext(Dispatchers.IO) {
            val param = ParameterValue.newBuilder()
                .addAllData(listOf(0f, 0f, rotation.degrees.toFloat()))
                .build()
            val value = PhysicalModelValue.newBuilder()
                .setTarget(PhysicalModelValue.PhysicalType.ROTATION)
                .setValue(param)
                .build()
            blockingStub.setPhysicalModel(value)
        }
    }

    override suspend fun setPosture(posture: FoldPosture) {
        withContext(Dispatchers.IO) {
            val p = Posture.newBuilder()
                .setValue(Posture.PostureValue.forNumber(posture.protoValue))
                .build()
            blockingStub.setPosture(p)
        }
    }

    override suspend fun hardware(): EmulatorHardware {
        return withContext(Dispatchers.IO) {
            val status = blockingStub.getStatus(Empty.getDefaultInstance())
            val hwEntries = status.hardwareConfig.entryList.associate { it.key to it.value }
            val platformMap = status.platformConfigMap
            val map = hwEntries + platformMap.filterKeys { it !in hwEntries }
            EmulatorHardwareParser.parse(map)
        }
    }

    override fun close() {
        channel.shutdownNow()
    }

    companion object {
        private fun createChannel(endpoint: EmulatorEndpoint): ManagedChannel {
            val builder = NettyChannelBuilder.forAddress(endpoint.host, endpoint.port)
                .usePlaintext()
                .maxInboundMessageSize(64 * 1024 * 1024)

            endpoint.token?.let { builder.intercept(BearerTokenInterceptor(it)) }

            return builder.build()
        }
    }
}
