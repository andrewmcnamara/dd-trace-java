package datadog.trace.core;

import datadog.trace.api.config.TracerConfig;
import datadog.trace.util.AgentTaskScheduler;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A monitor thread scheduled on a regular basis. It triggers keep-alive spans to be sent for
 * long-running traces.
 */
public class TraceKeepAlive implements AgentTaskScheduler.Task<Void> {
  public TraceKeepAlive(long keepAlivePeriod) {
    if (keepAlivePeriod <= 0) {
      throw new IllegalArgumentException(
          TracerConfig.LONG_RUNNING_TRACE_FLUSH_INTERVAL + " property should be strictly positive");
    }
    this.keepAlivePeriod = keepAlivePeriod;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(TraceKeepAlive.class);

  private final Object dummy = new Object();
  private final ConcurrentMap<WeakReference<PendingTrace>, Object> pendingTraces =
      new ConcurrentHashMap<>();
  private final long keepAlivePeriod;

  private volatile AgentTaskScheduler.Scheduled<Void> scheduled;

  @Override
  public void run(Void ignored) {
    final long now = System.currentTimeMillis();
    final List<WeakReference<PendingTrace>> garbaged = new ArrayList<>();
    for (final WeakReference<PendingTrace> ref : pendingTraces.keySet()) {
      final PendingTrace pt = ref.get();
      if (pt != null) {
        pt.keepAliveUnfinished(now, keepAlivePeriod);
      } else {
        garbaged.add(ref);
      }
    }
    for (final WeakReference<PendingTrace> ref : garbaged) {
      pendingTraces.remove(ref);
    }
  }

  public void start() {
    synchronized (this) {
      if (scheduled == null) {
        LOGGER.debug(
            "Starting long running keepalive monitor. It will flush pending thread each {} millis",
            keepAlivePeriod);
      }
      scheduled =
          AgentTaskScheduler.INSTANCE.scheduleAtFixedRate(
              this, null, 0L, keepAlivePeriod, TimeUnit.MILLISECONDS);
    }
  }

  public void stop() {
    synchronized (this) {
      if (scheduled != null) {
        scheduled.cancel();
        scheduled = null;
      }
    }
    LOGGER.debug("Long running keepalive monitor stopped");
  }

  public void onPendingTraceBegins(final PendingTrace pendingTrace) {
    pendingTraces.put(new WeakReference<>(pendingTrace), dummy);
  }

  public void onPendingTraceEnds(final PendingTrace pendingTrace) {
    pendingTraces.remove(new WeakReference<>(pendingTrace));
  }
}
