package com.novel.scheduler

import com.novel.infrastructure.event.DailyStoryCountResetJob
import io.ktor.server.application.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

class DailyResetScheduler(private val application: Application) {
    private val logger = LoggerFactory.getLogger(DailyResetScheduler::class.java)
    private val resetJob: DailyStoryCountResetJob by application.inject()
    
    private var schedulerJob: Job? = null
    
    fun start() {
        logger.info("Starting daily reset scheduler")
        
        schedulerJob = application.launch {
            while (isActive) {
                try {
                    val now = Clock.System.now()
                    val nextMidnight = now.plus(1, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
                    val delayMillis = now.until(nextMidnight, DateTimeUnit.MILLISECOND)
                    
                    logger.info("Next daily reset scheduled at: $nextMidnight (in ${delayMillis}ms)")
                    
                    delay(delayMillis.milliseconds)
                    
                    logger.info("Executing daily reset job")
                    resetJob.resetDailyCounts()
                    
                    // Wait a bit to avoid running multiple times
                    delay(60000) // 1 minute
                } catch (e: Exception) {
                    logger.error("Error in daily reset scheduler", e)
                    // Wait before retrying
                    delay(300000) // 5 minutes
                }
            }
        }
    }
    
    fun stop() {
        logger.info("Stopping daily reset scheduler")
        schedulerJob?.cancel()
        schedulerJob = null
    }
}

fun Application.configureDailyResetScheduler() {
    val scheduler = DailyResetScheduler(this)
    scheduler.start()
    
    // Stop scheduler when application shuts down
    monitor.subscribe(ApplicationStopping) {
        scheduler.stop()
    }
    
    log.info("Daily reset scheduler configured")
}
