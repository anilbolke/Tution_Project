package com.tution.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.tution.util.ReminderConfig;

/**
 * The daily reminder run, plus the shared background executor.
 *
 * The app had no scheduler of any kind before this — every notification fired
 * inline during an HTTP request, which is also why viewing a receipt could block
 * the response for up to 30 seconds waiting on the WhatsApp gateway.
 * {@link #submit} exists so that work can move off the request thread.
 *
 * The nightly job always refreshes overdue instalment flags. It only SENDS when
 * {@link ReminderConfig#AUTO_SEND} is true, which stays false until the three
 * templates are approved by Meta.
 */
@WebListener
public class ReminderScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(ReminderScheduler.class.getName());

    /** Shared pool: one daily job plus the odd fire-and-forget message send. */
    private static volatile ScheduledExecutorService pool;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        pool = Executors.newScheduledThreadPool(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                // Daemon threads so a stuck send can never hold up Tomcat's shutdown.
                Thread t = new Thread(r, "tution-reminder");
                t.setDaemon(true);
                return t;
            }
        });

        long delay = secondsUntilNextRun();
        pool.scheduleAtFixedRate(this::runDaily, delay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);

        LOG.info("Reminder scheduler started; first run in " + (delay / 60) + " min, "
               + "auto-send=" + ReminderConfig.AUTO_SEND);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        LOG.info("Reminder scheduler stopped.");
    }

    /**
     * Runs work on the background pool. Falls back to running inline if the
     * pool is gone (shutdown in progress), so a caller never loses the work.
     */
    public static void submit(Runnable task) {
        ScheduledExecutorService p = pool;
        if (p == null || p.isShutdown()) {
            task.run();
            return;
        }
        try {
            p.submit(task);
        } catch (RuntimeException e) {
            task.run();
        }
    }

    /** The nightly job. Never lets an exception escape and kill the schedule. */
    private void runDaily() {
        try {
            ReminderService svc = new ReminderService();

            int flipped = svc.refreshOverdue();
            if (flipped > 0) {
                LOG.info("Marked " + flipped + " instalment(s) overdue.");
            }

            if (!ReminderConfig.AUTO_SEND) {
                LOG.info("Auto-send is off (templates pending approval) — "
                       + "reminder queue left for manual sending on /reminders.");
                return;
            }
            if (ReminderConfig.DRY_RUN) {
                LOG.info("DRY_RUN is on — the daily run will report but send nothing.");
            }
            ReminderService.RunResult res = svc.sendAll(null);
            LOG.info("Daily reminder run: " + res);

        } catch (Exception e) {
            // A failure today must not stop tomorrow's run.
            LOG.log(Level.WARNING, "Daily reminder run failed", e);
        }
    }

    /** Seconds until the next RUN_HOUR:RUN_MINUTE, tomorrow if today's has passed. */
    private static long secondsUntilNextRun() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(ReminderConfig.RUN_HOUR)
                                .withMinute(ReminderConfig.RUN_MINUTE)
                                .withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Math.max(60, Duration.between(now, next).getSeconds());
    }
}
