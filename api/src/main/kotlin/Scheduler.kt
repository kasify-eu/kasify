package eu.kasify

import eu.kasify.jobs.CalendarImportJob
import eu.kasify.jobs.NotificationDispatchJob
import eu.kasify.jobs.SesHospedajesPollJob
import eu.kasify.jobs.SesHospedajesSubmissionJob
import org.quartz.JobBuilder
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory
import java.util.Date

object SchedulerManager {
    lateinit var scheduler: Scheduler
        private set

    val isInitialized: Boolean
        get() = ::scheduler.isInitialized

    fun initialize(scheduler: Scheduler) {
        this.scheduler = scheduler
    }
}

fun configureScheduler() {
    val scheduler = StdSchedulerFactory.getDefaultScheduler()
    SchedulerManager.initialize(scheduler)
    val job = JobBuilder.newJob(CalendarImportJob::class.java).build()
    val trigger =
        TriggerBuilder
            .newTrigger()
            .startAt(Date(System.currentTimeMillis() + 5 * 60 * 1000))
            .withSchedule(
                SimpleScheduleBuilder
                    .simpleSchedule()
                    .withIntervalInMinutes(15)
                    .repeatForever(),
            ).build()
    scheduler.scheduleJob(job, trigger)

    // SES jobs run hourly in production. In development they run every 5 minutes with a shorter
    // initial delay, so submit → poll → retry round-trips against the local mock server don't
    // require waiting out production cadences.
    val isDevelopment = System.getenv("ENVIRONMENT") != "production"
    val sesIntervalMinutes = if (isDevelopment) 5 else 60
    val sesStartDelayMs = (if (isDevelopment) 1 else 5) * 60 * 1000L

    val sesJob =
        JobBuilder
            .newJob(SesHospedajesSubmissionJob::class.java)
            .withIdentity("sesSubmissionJob", "ses")
            .build()

    val sesTrigger =
        TriggerBuilder
            .newTrigger()
            .withIdentity("sesSubmissionTrigger", "ses")
            .startAt(Date(System.currentTimeMillis() + sesStartDelayMs))
            .withSchedule(
                SimpleScheduleBuilder
                    .simpleSchedule()
                    .withIntervalInMinutes(sesIntervalMinutes)
                    .repeatForever(),
            ).build()

    scheduler.scheduleJob(sesJob, sesTrigger)

    val sesPollJob =
        JobBuilder
            .newJob(SesHospedajesPollJob::class.java)
            .withIdentity("sesPollJob", "ses")
            .build()
    val sesPollTrigger =
        TriggerBuilder
            .newTrigger()
            .withIdentity("sesPollTrigger", "ses")
            .startAt(Date(System.currentTimeMillis() + sesStartDelayMs))
            .withSchedule(
                SimpleScheduleBuilder
                    .simpleSchedule()
                    .withIntervalInMinutes(sesIntervalMinutes)
                    .repeatForever(),
            ).build()

    scheduler.scheduleJob(sesPollJob, sesPollTrigger)

    // Notifications outbox: drains pending notifications every minute. User-facing, so latency is
    // kept low; per-row exponential backoff (next_attempt_at) handles transient delivery failures.
    val notificationJob =
        JobBuilder
            .newJob(NotificationDispatchJob::class.java)
            .withIdentity("notificationDispatchJob", "notifications")
            .build()
    val notificationTrigger =
        TriggerBuilder
            .newTrigger()
            .withIdentity("notificationDispatchTrigger", "notifications")
            .startAt(Date(System.currentTimeMillis() + 30 * 1000))
            .withSchedule(
                SimpleScheduleBuilder
                    .simpleSchedule()
                    .withIntervalInMinutes(1)
                    .repeatForever(),
            ).build()
    scheduler.scheduleJob(notificationJob, notificationTrigger)

    scheduler.start()
}
