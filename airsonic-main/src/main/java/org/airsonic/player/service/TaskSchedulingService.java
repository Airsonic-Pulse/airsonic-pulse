package org.airsonic.player.service;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Service
public class TaskSchedulingService implements ScheduledTaskHolder {
    private static final Logger LOG = LoggerFactory.getLogger(TaskSchedulingService.class);

    private final ScheduledTaskRegistrar registrar;

    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<ScheduledTask, TaskMetaData> taskMetadata = new ConcurrentHashMap<>();


    public TaskSchedulingService(ThreadPoolTaskSchedulerBuilder builder) throws IOException {
        ThreadPoolTaskScheduler taskScheduler = builder.build();
        taskScheduler.setDaemon(true);
        taskScheduler.afterPropertiesSet();
        this.registrar = new ScheduledTaskRegistrar();
        this.registrar.setScheduler(taskScheduler);
    }

    public void scheduleTask(String name, Function<ScheduledTaskRegistrar, ScheduledTask> scheduledTask) {
        scheduleTask(name, scheduledTask, true);
    }

    public void scheduleTask(String name, Function<ScheduledTaskRegistrar, ScheduledTask> scheduledTask, boolean cancelIfExists) {
        if (name == null) {
            return;
        }
        tasks.compute(name, (k, v) -> {
            if (cancelIfExists && v != null) {
                v.cancel();
            }
            ScheduledTask task = scheduledTask.apply(registrar);
            taskMetadata.put(task, new TaskMetaData(k, Instant.now()));
            LOG.info("Task {} scheduled", k);
            return task;
        });
    }

    public void scheduleFixedDelayTask(String name, Runnable task, Instant firstTime, Duration period, boolean cancelIfExists) {
        scheduleTask(name,
            r -> r.scheduleFixedDelayTask(new FixedDelayTask(task, period, Duration.ofMillis(ChronoUnit.MILLIS.between(Instant.now(), firstTime)))),
            cancelIfExists);
    }

    public void scheduleAtFixedRate(String name, Runnable task, Instant firstTime, Duration period, boolean cancelIfExists) {
        scheduleTask(name,
            r -> r.scheduleFixedRateTask(new FixedRateTask(task, period, Duration.ofMillis(ChronoUnit.MILLIS.between(Instant.now(), firstTime)))),
            cancelIfExists);
    }

    public void scheduleOnce(String name, Runnable task, Instant firstTime, boolean cancelIfExists) {
        scheduleTask(name,
            r -> r.scheduleTriggerTask(new TriggerTask(task, new RunOnceTrigger(Duration.ofMillis(ChronoUnit.MILLIS.between(Instant.now(), firstTime))))),
            cancelIfExists);
    }

    public ScheduledTask getScheduledTask(String name) {
        return tasks.get(name);
    }

    public void unscheduleTask(String name) {
        ScheduledTask task = tasks.remove(name);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public Set<ScheduledTask> getScheduledTasks() {
        return new HashSet<>(tasks.values());
    }

    public static class RunOnceTrigger extends PeriodicTrigger {
        public RunOnceTrigger(Duration initialDelay) {
            super(Duration.ZERO);
            setInitialDelay(initialDelay);
        }

        @Override
        public Instant nextExecution(TriggerContext triggerContext) {
            if (triggerContext.lastCompletion() == null) { // hasn't executed yet
                return super.nextExecution(triggerContext);
            }
            return null;
        }
    }

    @Component
    @Endpoint(id = "customscheduledtasks")
    public static class CustomScheduledTasksEndpoint {
        @Autowired
        private TaskSchedulingService taskService;

        @ReadOperation
        public WebEndpointResponse<Map<String, Map<String, Object>>> info() {
            Map<String, Map<String, Object>> map = taskService.tasks.entrySet().stream().map(e -> {
                Map<String, Object> metadata = Optional.ofNullable(taskService.taskMetadata.get(e.getValue()))
                        .map(TaskMetaData::toMap).orElse(Collections.emptyMap());
                metadata.put("scheduledTask", e.getValue());
                metadata.put("scheduledBy", e.getValue().getTask().getRunnable().getClass().getName());
                metadata.put("runMetadata",
                        getRunMetadata((Instant) metadata.get("created"), e.getValue(), Instant.now()));
                return Pair.of(e.getKey(), metadata);
            }).collect(toMap(p -> p.getKey(), p -> p.getValue()));
            return new WebEndpointResponse<>(map);
        }

        private RunMetadata getRunMetadata(Instant created, ScheduledTask scheduledTask, Instant now) {
            if (scheduledTask.getTask() instanceof TriggerTask task) {
                if (task.getTrigger() instanceof RunOnceTrigger trigger) {
                    Instant firstRun = created.plusMillis(trigger.getInitialDelayDuration().toMillis());
                    if (firstRun.isAfter(now)) {
                        return new RunMetadata(firstRun, null, firstRun, RunMetadata.Type.RUN_ONCE);
                    } else {
                        return new RunMetadata(firstRun, firstRun, null, RunMetadata.Type.RUN_ONCE);
                    }
                }
            } else if (scheduledTask.getTask() instanceof IntervalTask task) {
                Instant firstRun = created.plusMillis(task.getInitialDelayDuration().toMillis());
                long millis = ChronoUnit.MILLIS.between(firstRun, now);
                if (millis < 0) {
                    // firstRun will happen in the future
                    return new RunMetadata(firstRun, null, firstRun,
                            task instanceof FixedRateTask ? RunMetadata.Type.FIXED_RATE : RunMetadata.Type.FIXED_DELAY);
                } else {
                    // firstRun happened in the past
                    long runs = millis / task.getIntervalDuration().toMillis();
                    if (task instanceof FixedRateTask) {
                        Instant lastRun = firstRun.plusMillis(task.getIntervalDuration().toMillis() * runs);
                        Instant nextRun = lastRun.plusMillis(task.getIntervalDuration().toMillis());
                        return new RunMetadata(firstRun, lastRun, nextRun, RunMetadata.Type.FIXED_RATE);
                    } else if (task instanceof FixedDelayTask) {
                        if (runs < 1) {
                            // cannot calculate the next run because don't know how long the previous task lasted
                            return new RunMetadata(firstRun, firstRun, Instant.MIN, RunMetadata.Type.FIXED_DELAY);
                        } else {
                            // cannot calculate the last or next runs because don't know how long previous tasks lasted
                            return new RunMetadata(firstRun, Instant.MIN, Instant.MIN, RunMetadata.Type.FIXED_DELAY);
                        }
                    }
                }
            }

            return null;
        }

        public static class RunMetadata {
            private final Instant firstRun;
            private final Instant lastRun;
            private final Instant nextRun;
            private final Type type;

            public RunMetadata(Instant firstRun, Instant lastRun, Instant nextRun, Type type) {
                super();
                this.firstRun = firstRun;
                this.lastRun = lastRun;
                this.nextRun = nextRun;
                this.type = type;
            }

            public Instant getFirstRun() {
                return firstRun;
            }

            public Instant getLastRun() {
                return lastRun;
            }

            public Instant getNextRun() {
                return nextRun;
            }

            public Type getType() {
                return type;
            }

            public static enum Type {
                FIXED_RATE, FIXED_DELAY, RUN_ONCE
            }
        }
    }

    private static class TaskMetaData {
        private final String name;
        private final Instant created;

        public TaskMetaData(String name, Instant created) {
            super();
            this.name = name;
            this.created = created;
        }

        public Map<String, Object> toMap() {
            HashMap<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("created", created);
            return map;
        }
    }

}
