/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.rinsim.core.model.time;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verifyNotNull;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Queues;
import com.google.common.primitives.Doubles;

/**
 *
 * @author Rinde van Lon
 */
public final class GCLogMonitor {
  static final long S_TO_NS = 1000000000L;
  static final long HISTORY_LENGTH = 30 * S_TO_NS;
  static final long LOG_PARSER_DELAY = 200L;
  static final int QUEUE_EXPECTED_SIZE = 50;
  static final String FILTER =
      "Total time for which application threads were stopped";
  static final String PRINT_ARG = "-XX:+PrintGCApplicationStoppedTime";
  static final String LOG_ARG = "-Xloggc:";

  @Nullable
  private static volatile GCLogMonitor instance;

  final long startTimeMillis;
  final Deque<PauseTime> pauseTimes;
  final Tailer tailer;

  GCLogMonitor(@Nullable String path) {
    final String logPath;
    if (path == null) {
      final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
      final List<String> arguments = runtimeMxBean.getInputArguments();

      String providedLogFile = null;
      boolean foundPrintArg = false;
      for (final String arg : arguments) {
        if (arg.startsWith(LOG_ARG)) {
          providedLogFile = arg.substring(LOG_ARG.length());
        } else if (arg.equals(PRINT_ARG)) {
          foundPrintArg = true;
        }
      }
      checkArgument(providedLogFile != null && foundPrintArg,
          "Expected VM arguments: '%s<somefile> %s' but found '%s'.", LOG_ARG,
          PRINT_ARG, arguments);

      logPath = providedLogFile;
    } else {
      logPath = path;
    }
    tailer = new Tailer(new File(logPath), new LogListener(), LOG_PARSER_DELAY);
    final Thread thread = new Thread(tailer, "gclog-tailer");
    thread.setDaemon(true);
    thread.start();

    pauseTimes = Queues.synchronizedDeque(new LinkedList<PauseTime>());
    startTimeMillis = ManagementFactory.getRuntimeMXBean().getStartTime();
  }

  private void close() {
    tailer.stop();
    try {
      Thread.sleep(LOG_PARSER_DELAY);
    } catch (final InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Checks whether a log entry with the specified time (obtained from
   * {@link System#currentTimeMillis()}) or a later time exists in the log.
   * @param timeMillis The time in milliseconds.
   * @return <code>true</code> if there is a log entry later than or equal to
   *         the specified time, <code>false</code> otherwise.
   */
  public boolean hasSurpassed(long timeMillis) {
    return !pauseTimes.isEmpty()
        && pauseTimes.peekLast().getTime()
            - (timeMillis - startTimeMillis) >= 0;
  }

  /**
   * Computes the pause duration (aka stop-the-world time) in the specified
   * closed interval <code>[ts1,ts2]</code>. These pause times can be caused by
   * the GC, JIT compiler, etc.
   * @param ts1 The start of the interval (inclusive) must be a time obtained
   *          via {@link System#currentTimeMillis()} since the startup of the
   *          VM.
   * @param ts2 The end of the interval (inclusive) must be &gt;
   *          <code>ts1</code>.
   * @return The accumulated duration of pause time (in ns) in the specified
   *         interval.
   * @throws IllegalArgumentException If an illegal interval is specified.
   */
  public long getPauseTimeInInterval(long ts1, long ts2) {
    // convert system times to vm lifetime
    final long vmt1 = ts1 - startTimeMillis;
    final long vmt2 = ts2 - startTimeMillis;
    checkArgument(vmt1 >= 0,
        "ts1 must indicate a system time (in ns) after the start of the VM, "
            + "VM was started at %sns, ts1 is %sns.",
        startTimeMillis, ts1);
    checkArgument(vmt2 > vmt1, "ts2 must indicate a system time (in ns) after "
        + "ts1, ts1 %sns, ts2 %sns.", ts1, ts2);
    long duration = 0;
    // iterator starts with oldest times
    synchronized (pauseTimes) {
      for (final PauseTime pt : pauseTimes) {
        // if vmt2 < pt time -> we are outside the interval, we can stop the
        // loop
        if (vmt2 - pt.getTime() < 0) {
          break;
        }
        // if vmt1 <= pt time -> we are inside the interval, add the times to
        // the duration
        if (vmt1 - pt.getTime() <= 0) {
          duration += pt.getDuration();
        }
      }
    }
    return duration;
  }

  @SuppressWarnings("null")
  public static GCLogMonitor getInstance() {
    if (instance == null) {
      synchronized (GCLogMonitor.class) {
        if (instance == null) {
          return instance = new GCLogMonitor(null);
        }
      }
    }
    return instance;
  }

  @VisibleForTesting
  static void createInstance(@Nullable String path) {
    synchronized (GCLogMonitor.class) {
      if (instance != null) {
        instance.close();
      }
      instance = new GCLogMonitor(path);
    }
  }

  class LogListener extends TailerListenerAdapter {

    LogListener() {}

    @Override
    public void handle(@Nullable String line) {
      if (line != null && line.contains(FILTER)) {
        final String[] parts = line.split(": ");

        final Double t = Doubles.tryParse(parts[0]);
        if (t == null) {
          return;
        }
        final long time = (long) (S_TO_NS * t);
        final Double d =
            Doubles.tryParse(parts[2].substring(0, parts[2].length() - 8));
        if (d == null) {
          return;
        }
        final long duration = (long) (S_TO_NS * d);
        if (!pauseTimes.isEmpty()) {
          checkState(pauseTimes.peekLast().getTime() <= time,
              "Time inconsistency detected in the gc log.");
        }
        // add new info at the back
        pauseTimes.add(PauseTime.create(time, duration));

        // remove old info at the front
        while (time - pauseTimes.peekFirst().getTime() > HISTORY_LENGTH) {
          pauseTimes.pollFirst();
        }
      }
    }

    @Override
    public void handle(@SuppressWarnings("null") Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      throw new IllegalStateException(e);
    }
  }

  @AutoValue
  abstract static class PauseTime implements Comparable<PauseTime> {
    abstract long getTime();

    abstract long getDuration();

    @Override
    public int compareTo(@Nullable PauseTime o) {
      final PauseTime other = verifyNotNull(o);
      return ComparisonChain.start().compare(getTime(), other.getTime())
          .compare(getDuration(), other.getDuration())
          .result();
    }

    static PauseTime create(long time, long duration) {
      return new AutoValue_GCLogMonitor_PauseTime(time, duration);
    }
  }
}
