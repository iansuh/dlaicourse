package app.numbaseball.server

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.FileAppender
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun main() {
    val config = Config.fromEnv()
    config.logFile?.let { addFileLog(it.absolutePath) }
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

/** LOG_FILE: also write the (privacy-safe) log lines to a file. */
private fun addFileLog(path: String) {
    val ctx = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
    val encoder = PatternLayoutEncoder().apply {
        context = ctx
        pattern = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSX} %-5level %logger{20} - %msg%n"
        start()
    }
    val appender = FileAppender<ILoggingEvent>().apply {
        context = ctx
        file = path
        this.encoder = encoder
        start()
    }
    ctx.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender)
}
