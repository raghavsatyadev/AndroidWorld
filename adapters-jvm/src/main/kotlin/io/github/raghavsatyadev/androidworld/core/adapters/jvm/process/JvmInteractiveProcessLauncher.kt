package io.github.raghavsatyadev.androidworld.core.adapters.jvm.process

import io.github.raghavsatyadev.androidworld.core.domain.process.InteractiveProcess
import io.github.raghavsatyadev.androidworld.core.domain.process.InteractiveProcessLauncher
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessCommand
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Runs a [ProcessCommand] with stdin open and stderr merged into stdout, through [ProcessBuilder]. */
class JvmInteractiveProcessLauncher : InteractiveProcessLauncher {

    override fun launch(command: ProcessCommand): InteractiveProcess {
        val builder = ProcessBuilder(listOf(command.executable) + command.arguments).redirectErrorStream(true)
        command.workingDirectory?.let { builder.directory(File(it)) }
        if (command.environment.isNotEmpty()) builder.environment().putAll(command.environment)
        return JvmInteractiveProcess(builder.start())
    }
}

private class JvmInteractiveProcess(private val process: Process) : InteractiveProcess {
    private val collected = AtomicBoolean(false)
    private val stdin = process.outputStream

    override val output: Flow<String> = flow {
        check(collected.compareAndSet(false, true)) { "The output of a process can be collected once" }
        process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) emit(reader.readLine() ?: break)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun write(text: String) = withContext(Dispatchers.IO) {
        stdin.write(text.toByteArray(Charsets.UTF_8))
        stdin.flush()
    }

    override fun close() {
        runCatching { stdin.close() }
        val children = process.descendants().toList()
        process.destroy()
        if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        children.filter { it.isAlive }.forEach { it.destroyForcibly() }
    }
}
