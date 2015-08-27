package com.github.rinde.rinsim.core.model.time;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.rinde.rinsim.core.model.time.GCLogMonitor.PauseTime;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;

public class GCLogMonitorTest {
  static Path tempDir;
  Path gclog;
  GCLogMonitor monitor;

  @BeforeClass
  public static void setUpClass() {
    try {
      tempDir = Files.createTempDirectory("gclogtest");
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @AfterClass
  public static void tearDownClass() {
    try {
      Files.delete(tempDir);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Before
  public void setUp() {
    try {
      gclog =
          Files.createFile(Paths.get(tempDir.toString(), "gclog.txt"));
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
    GCLogMonitor.createInstance(gclog.toString());

    monitor = GCLogMonitor.getInstance();
  }

  @After
  public void tearDown() {
    try {
      Files.delete(gclog);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  public void testFail() {
    boolean fail = false;
    try {
      GCLogMonitor.createInstance(null);
    } catch (final IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("Expected VM arguments: ");
      fail = true;
    }
    assertThat(fail).isTrue();
  }

  @Test
  public void test() {
    log(1, 8);
    log(2, 0.0001);
    try {
      Thread.sleep(250);
    } catch (final InterruptedException e) {
      throw new IllegalStateException(e);
    }
    GCLogMonitor.getInstance();
    synchronized (monitor.pauseTimes) {
      final Iterator<PauseTime> it = monitor.pauseTimes.iterator();
      assertThat(it.next())
          .isEqualTo(PauseTime.create(1000000000L, 8000000000L));
      assertThat(it.next())
          .isEqualTo(PauseTime.create(2000000000L, 100000L));
      assertThat(it.hasNext()).isFalse();
    }

    final long st = monitor.startTimeMillis;

    assertThat(monitor.hasSurpassed(st)).isTrue();
    assertThat(monitor.hasSurpassed(st + 1000000000)).isTrue();
    assertThat(monitor.hasSurpassed(st + 2000000000)).isTrue();
    assertThat(monitor.hasSurpassed(st + 2000000001)).isFalse();

    assertThat(monitor.getPauseTimeInInterval(st, st + 2000000000))
        .isEqualTo(8000100000L);
    assertThat(monitor.getPauseTimeInInterval(st, st + 1))
        .isEqualTo(0L);
    assertThat(monitor.getPauseTimeInInterval(st + s(1), st + s(2)))
        .isEqualTo(8000100000L);
    assertThat(monitor.getPauseTimeInInterval(st + s(1) + 1, st + s(2) - 1))
        .isEqualTo(0L);
    assertThat(monitor.getPauseTimeInInterval(st + s(1), st + s(2) - 1))
        .isEqualTo(8000000000L);
    assertThat(monitor.getPauseTimeInInterval(st + s(1) + 1, st + s(2)))
        .isEqualTo(100000L);
    assertThat(monitor.getPauseTimeInInterval(st + s(2) + 1, st + s(2) + 3))
        .isEqualTo(0L);

    boolean fail = false;
    try {
      monitor.getPauseTimeInInterval(st - 1, st);
    } catch (final IllegalArgumentException e) {
      fail = true;
      assertThat(e.getMessage()).contains(
          "ts1 must indicate a system time (in ns) after the start of the VM");
    }
    assertThat(fail).isTrue();

    fail = false;
    try {
      monitor.getPauseTimeInInterval(st + 100, st + 10);
    } catch (final IllegalArgumentException e) {
      fail = true;
      assertThat(e.getMessage())
          .contains("ts2 must indicate a system time (in ns) after ts1");
    }
    assertThat(fail).isTrue();
  }

  static long s(long ns) {
    return ns * 1000000000;
  }

  void log(double time, double duration) {
    final String line =
        time + ": " + GCLogMonitor.FILTER + ": " + duration + " seconds";
    try {
      Files.write(gclog, ImmutableSet.of(line), Charsets.UTF_8,
          StandardOpenOption.APPEND);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
