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

import static com.github.rinde.rinsim.core.model.time.RealtimeClockController.RtClockEventType.SWITCH_TO_REAL_TIME;
import static com.github.rinde.rinsim.core.model.time.RealtimeClockController.RtClockEventType.SWITCH_TO_SIM_TIME;
import static com.github.rinde.rinsim.core.model.time.RealtimeModel.SimpleState.INIT_RT;
import static com.github.rinde.rinsim.core.model.time.RealtimeModel.SimpleState.INIT_ST;
import static com.github.rinde.rinsim.core.model.time.RealtimeModel.SimpleState.STOPPED;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verifyNotNull;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.measure.Measure;
import javax.measure.unit.SI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.fsm.AbstractState;
import com.github.rinde.rinsim.fsm.StateMachine;
import com.github.rinde.rinsim.fsm.StateMachine.StateMachineEvent;
import com.github.rinde.rinsim.fsm.StateMachine.StateTransitionEvent;
import com.google.auto.value.AutoValue;
import com.google.common.math.DoubleMath;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import net.openhft.affinity.AffinityLock;

/**
 * @author Rinde van Lon
 *
 */
class RealtimeModel extends TimeModel implements RealtimeClockController {
  // number of ticks that will be checked for consistency
  static final int CONSISTENCY_CHECK_LENGTH = 50;
  // static final double MAX_MEAN_DEVIATION_PERC = .05;

  static final double MAX_AVG_DEV_FACTOR = .05;
  static final double MAX_DEVIATION_FACTOR = .1d;
  // static final double MAX_TICK_LENGTH_FACTOR = 1.1d;
  // static final double MIN_TICK_LENGTH_FACTOR = .9d;
  static final Logger LOGGER = LoggerFactory.getLogger(RealtimeModel.class);
  static final double MS_TO_NS = 1000000d;

  final StateMachine<Trigger, RealtimeModel> stateMachine;
  // final Map<TickListener, TickListenerTimingChecker> decoratorMap;

  @Nullable
  AffinityLock affinityLock;
  // final long maxTickDuration;
  // final long minTickDuration;
  final long maxTickDeviationNs;
  final Realtime realtimeState;

  RealtimeModel(RealtimeBuilder builder) {
    super(builder, RtClockEventType.values());
    LOGGER.trace("Constructor");
    // decoratorMap = new LinkedHashMap<>();

    final long tickNanos = Measure.valueOf(timeLapse.getTickLength(),
        timeLapse.getTimeUnit()).longValue(SI.NANO(SI.SECOND));

    // maxTickDuration = DoubleMath.roundToLong(
    // MAX_TICK_LENGTH_FACTOR * tickNanos, RoundingMode.UP);
    // minTickDuration = DoubleMath.roundToLong(MIN_TICK_LENGTH_FACTOR *
    // tickNanos,
    // RoundingMode.DOWN);
    maxTickDeviationNs =
        DoubleMath.roundToLong(MAX_DEVIATION_FACTOR * tickNanos,
            RoundingMode.UP);

    // final Range<Long> tickDuration =
    // Range.closed(minTickDuration, maxTickDuration);

    final long maxStdNs =
        DoubleMath.roundToLong(tickNanos * MAX_AVG_DEV_FACTOR, RoundingMode.UP);
    // final long maxMeanDeviationNs = DoubleMath.roundToLong(
    // tickNanos * MAX_MEAN_DEVIATION_PERC, RoundingMode.UP);

    realtimeState =
        new Realtime(tickNanos, maxStdNs, maxTickDeviationNs);
    final SimulatedTime st = new SimulatedTime();
    stateMachine = StateMachine
        .create(
            builder.getClockMode() == ClockMode.REAL_TIME ? INIT_RT : INIT_ST)
        .addTransition(INIT_RT, Trigger.SIMULATE, INIT_ST)
        .addTransition(INIT_RT, Trigger.START, realtimeState)
        .addTransition(INIT_ST, Trigger.REAL_TIME, INIT_RT)
        .addTransition(INIT_ST, Trigger.START, st)
        .addTransition(realtimeState, Trigger.SIMULATE, realtimeState)
        .addTransition(realtimeState, Trigger.REAL_TIME, realtimeState)
        .addTransition(realtimeState, Trigger.DO_SIMULATE, st)
        .addTransition(realtimeState, Trigger.STOP, STOPPED)
        .addTransition(st, Trigger.REAL_TIME, st)
        .addTransition(st, Trigger.SIMULATE, st)
        .addTransition(st, Trigger.DO_REAL_TIME, realtimeState)
        .addTransition(st, Trigger.STOP, STOPPED)
        .addTransition(STOPPED, Trigger.STOP, STOPPED)
        .build();

    final RealtimeModel ref = this;
    stateMachine.getEventAPI().addListener(new Listener() {
      @Override
      public void handleEvent(Event e) {
        @SuppressWarnings("unchecked")
        final StateTransitionEvent<Trigger, RealtimeModel> event =
            (StateTransitionEvent<Trigger, RealtimeModel>) e;

        LOGGER.trace("{} {}", timeLapse, event);
        if ((event.newState == realtimeState || event.newState == INIT_RT)
            && eventDispatcher.hasListenerFor(SWITCH_TO_REAL_TIME)) {
          eventDispatcher.dispatchEvent(new Event(SWITCH_TO_REAL_TIME, ref));
        } else if ((event.newState == st || event.newState == INIT_ST)
            && eventDispatcher.hasListenerFor(SWITCH_TO_SIM_TIME)) {
          eventDispatcher.dispatchEvent(new Event(SWITCH_TO_SIM_TIME, ref));
        }
      }
    }, StateMachineEvent.values());
  }

  @Override
  void cleanUpAfterException() {
    LOGGER.trace("cleanUpAfterException");
    shutdownExecutor();
    super.cleanUpAfterException();
  }

  void shutdownExecutor() {
    LOGGER.trace("shutdownExecutor");
    final ListeningScheduledExecutorService ex = realtimeState.executor;
    if (ex != null) {
      ex.shutdown();

      // in case the future could not be cancelled before, do it now
      final ListenableScheduledFuture<?> fut = realtimeState.schedulerFuture;
      if (fut != null && !fut.isDone()) {
        realtimeState.cancelTask();
      }

      try {
        ex.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
      } catch (final InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }
    verifyNotNull(affinityLock).release();
  }

  boolean isExecutorAlive() {
    return realtimeState.executor != null
        && !realtimeState.executor.isTerminated();
  }

  @Override
  void doStart() {
    checkState(stateMachine.isSupported(Trigger.START),
        "%s can be started only once",
        getClass().getSimpleName());
    affinityLock = AffinityLock.acquireLock();
    stateMachine.handle(Trigger.START, this);
  }

  @Override
  public void stop() {
    LOGGER.trace("stop");
    checkState(stateMachine.isSupported(Trigger.STOP),
        "Can not stop time in current state: %s",
        stateMachine.getCurrentState().name());
    final boolean rt = stateMachine.stateIs(realtimeState);
    stateMachine.handle(Trigger.STOP, this);
    if (!rt) {
      shutdownExecutor();
    }
  }

  @SuppressWarnings("deprecation")
  @Deprecated
  @Override
  public void tick() {
    throw new UnsupportedOperationException(
        "Calling tick directly is not supported in "
            + RealtimeModel.class.getSimpleName());
  }

  @Override
  public boolean isTicking() {
    return !stateMachine.stateIsOneOf(SimpleState.values());
  }

  @Override
  public void switchToRealTime() {
    checkState(stateMachine.isSupported(Trigger.REAL_TIME),
        "Can not switch to real time mode because clock is already stopped.");
    stateMachine.handle(Trigger.REAL_TIME, this);
  }

  @Override
  public void switchToSimulatedTime() {
    checkState(stateMachine.isSupported(Trigger.SIMULATE),
        "Can not switch to simulated time mode because clock is already "
            + "stopped.");
    stateMachine.handle(Trigger.SIMULATE, this);
  }

  @Override
  public ClockMode getClockMode() {
    return ((ClockState) stateMachine.getCurrentState()).getClockMode();
  }

  // @Override
  // public boolean register(TickListener tickListener) {
  // checkArgument(!decoratorMap.containsKey(tickListener),
  // "A TickListener can not be registered more than once: %s.",
  // tickListener);
  // final TickListenerTimingChecker decorated = new TickListenerTimingChecker(
  // tickListener, maxTickDuration);
  // decoratorMap.put(tickListener, decorated);
  // return super.register(decorated);
  // }

  // @Override
  // public boolean unregister(TickListener tickListener) {
  // return super.unregister(decoratorMap.remove(tickListener));
  // }

  @Override
  @Nonnull
  public <U> U get(Class<U> clazz) {
    if (clazz == RealtimeClockController.class) {
      return clazz.cast(this);
    }
    return super.get(clazz);
  }

  // String printTLDump() {
  // long sumDuration = 0;
  // final StringBuilder sb = new StringBuilder();
  // for (final TickListenerTimingChecker tl : decoratorMap.values()) {
  // sb.append(tl.delegate)
  // .append(" took ")
  // .append(tl.tickDuration)
  // .append(" ns")
  // .append(System.lineSeparator());
  // sumDuration += tl.tickDuration;
  // }
  // sb.append("sum duration ")
  // .append(sumDuration)
  // .append(" nano seconds")
  // .append(System.lineSeparator());
  // return sb.toString();
  // }

  enum Trigger {
    START, STOP, SIMULATE, DO_SIMULATE, REAL_TIME, DO_REAL_TIME;
  }

  interface ClockState
      extends com.github.rinde.rinsim.fsm.State<Trigger, RealtimeModel> {

    /**
     * @return The {@link RealtimeClockController.ClockMode} the clock is in.
     */
    ClockMode getClockMode();
  }

  abstract static class AbstractClockState
      extends AbstractState<Trigger, RealtimeModel>
      implements ClockState {}

  static class SimulatedTime extends AbstractClockState {
    boolean isTicking;
    @Nullable
    Trigger nextTrigger;

    SimulatedTime() {}

    @Override
    @Nullable
    public Trigger handle(@Nullable Trigger trigger, RealtimeModel context) {
      if (trigger == Trigger.REAL_TIME) {
        isTicking = false;
        nextTrigger = Trigger.DO_REAL_TIME;
        return null;
      } else if (trigger == Trigger.SIMULATE) {
        if (nextTrigger == Trigger.DO_REAL_TIME) {
          return null;
        }
        isTicking = true;
        nextTrigger = null;
        return null;
      }
      isTicking = true;
      try {
        while (isTicking) {
          context.tickImpl();
        }
      } catch (final RuntimeException e) {
        LOGGER.error(e.getMessage(), e);
        context.cleanUpAfterException();
        throw e;
      }
      final Trigger t = nextTrigger;
      nextTrigger = null;
      return t;
    }

    @Override
    public void onExit(Trigger event, RealtimeModel context) {
      isTicking = false;
    }

    @Override
    public ClockMode getClockMode() {
      return ClockMode.SIMULATED;
    }

    @Override
    public String toString() {
      return "SimulatedTime";
    }
  }

  static class Realtime extends AbstractClockState {
    static final long THREAD_SLEEP_MS = 50L;
    // waiting 50ms seems to improve reliability of interarrival times
    static final long STARTUP_DELAY = 50000000L;

    final long tickNanos;
    final double maxAvgDeviationNs;
    // final double maxMeanDeviationNs;
    // final Range<Long> allowedTickDuration;
    final long maxTickDeviationNs;
    final List<Throwable> exceptions;
    @Nullable
    Trigger nextTrigger;
    @Nullable
    ListeningScheduledExecutorService executor;

    @Nullable
    ListenableScheduledFuture<?> schedulerFuture;

    AtomicBoolean taskIsRunning;
    AtomicBoolean isShuttingDown;

    // keeps time for last real-time request while in RT mode
    long lastRtRequest;

    TimeRunner timeRunner;

    Realtime(long tickNs, long maxAvgTickDevNs, long maxTickDevNs) {
      tickNanos = tickNs;
      maxAvgDeviationNs = maxAvgTickDevNs;
      // maxMeanDeviationNs = maxMeanDevNs;
      maxTickDeviationNs = maxTickDevNs;
      // allowedTickDuration = tickDur;
      taskIsRunning = new AtomicBoolean();
      isShuttingDown = new AtomicBoolean();
      exceptions = new ArrayList<>();

    }

    @Override
    public void onEntry(Trigger event, RealtimeModel context) {
      if (executor == null) {
        LOGGER.trace("starting executor..");
        executor = MoreExecutors.listeningDecorator(
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
              @Override
              public Thread newThread(@Nullable Runnable r) {
                return new Thread(r,
                    Thread.currentThread().getName() + "-RealtimeModel");
              }
            }));
      }
    }

    @Override
    @Nullable
    public Trigger handle(@Nullable Trigger event,
        final RealtimeModel context) {
      LOGGER.trace("Realtime.handle {}", event);
      if (event == Trigger.SIMULATE) {
        // RT takes precedence over ST, if a request for RT has been made during
        // the same tick, all ST requests are ignored.
        if (context.getCurrentTime() > lastRtRequest) {
          LOGGER.trace("set trigger");
          nextTrigger = Trigger.DO_SIMULATE;
        }
        return null;
      } else if (event == Trigger.REAL_TIME) {
        lastRtRequest = context.getCurrentTime();
        nextTrigger = null;
        return null;
      }

      taskIsRunning.set(true);
      timeRunner = new TimeRunner(context);
      schedulerFuture =
          verifyNotNull(executor).scheduleAtFixedRate(timeRunner, 0,
              tickNanos,
              TimeUnit.NANOSECONDS);
      final ListenableScheduledFuture<?> future = schedulerFuture;
      Futures.addCallback(future, new FutureCallback<Object>() {
        @Override
        public void onFailure(Throwable t) {
          if (!(t instanceof CancellationException)) {
            exceptions.add(t);
          } else {
            LOGGER.trace("{} cancel execution", context.timeLapse);
          }
          taskIsRunning.set(false);
        }

        @Override
        public void onSuccess(@Nullable Object result) {}
      });
      awaitTermination(context);
      LOGGER.trace("end of realtime, next trigger {}", nextTrigger);
      final Trigger t = nextTrigger;
      nextTrigger = null;
      return t;
    }

    @Override
    public void onExit(Trigger event, RealtimeModel context) {
      LOGGER.trace("Realtime onExit {}", event);
      cancelTask();
      if (event == Trigger.STOP) {
        isShuttingDown.set(true);
      }
    }

    void cancelTask() {
      final ListenableScheduledFuture<?> f = verifyNotNull(schedulerFuture);
      if (!f.isDone()) {
        LOGGER.trace("initiate cancel RT clock");
        f.cancel(true);
      }
    }

    void awaitTermination(RealtimeModel context) {
      LOGGER.trace("awaiting RT clock termination..");
      try {
        while (taskIsRunning.get()) {
          Thread.sleep(THREAD_SLEEP_MS);
        }

      } catch (final InterruptedException e) {
        throw new IllegalStateException(e);
      }
      if (!exceptions.isEmpty()) {
        context.cleanUpAfterException();
        LOGGER.error(exceptions.get(0).getMessage(), exceptions.get(0));
        if (exceptions.get(0) instanceof RuntimeException) {
          throw (RuntimeException) exceptions.get(0);
        } else if (exceptions.get(0) instanceof Error) {
          throw (Error) exceptions.get(0);
        }
        throw new IllegalStateException(exceptions.get(0));
      }
      try {
        Thread.sleep(THREAD_SLEEP_MS);
      } catch (final InterruptedException e) {
        throw new IllegalStateException(e);
      }
      try {
        timeRunner.checkConsistency();
      } catch (final IllegalStateException e) {
        context.cleanUpAfterException();
        throw e;
      }
      LOGGER.trace("done waiting");
      if (isShuttingDown.get()) {
        context.shutdownExecutor();
      }
    }

    @Override
    public ClockMode getClockMode() {
      return ClockMode.REAL_TIME;
    }

    @Override
    public String toString() {
      return "Realtime";
    }

    class TimeRunner implements Runnable {
      final Deque<MeasuredDeviation> measuredDeviations;
      final Deque<TimeStamp> timeStamps;
      final Deque<TimeStamp> timeStampsBuffer;
      final RealtimeModel context;

      GCLogMonitor logMonitor;
      long counter;
      TimeStamp firstTimeStamp;

      TimeRunner(RealtimeModel rm) {
        context = rm;
        measuredDeviations = new LinkedList<>();
        timeStamps = new LinkedList<>();
        timeStampsBuffer = new LinkedList<>();
        logMonitor = GCLogMonitor.getInstance();
      }

      @Override
      public void run() {
        final TimeStamp ts = TimeStamp.now(counter);
        if (counter == 0) {
          firstTimeStamp = ts;
        }
        timeStampsBuffer.add(ts);

        checkConsistency();
        context.tickImpl();

        LOGGER.trace("tick {} is done, nextTrigger: {} ", counter, nextTrigger);
        if (nextTrigger != null) {
          cancelTask();
        }
        counter++;
      }

      void checkConsistency() {
        // check if GCLogMonitor has a time AFTER the timestamp (in that
        // case we are sure that we have complete information)
        // unless forceCheck=true -> then we always check all time stamps
        while (!timeStampsBuffer.isEmpty()
            && logMonitor
                .hasSurpassed(timeStampsBuffer.peekFirst().getMillis())) {
          timeStamps.add(timeStampsBuffer.removeFirst());
        }

        while (timeStamps.size() > 1) {
          final TimeStamp ts1 = timeStamps.removeFirst();
          final long expectedTs1ns =
              firstTimeStamp.getNanos() + ts1.getTickCount() * tickNanos;
          final long expectedTs1ms = firstTimeStamp.getMillis()
              + ts1.getTickCount() * (tickNanos / 1000000);

          final TimeStamp ts2 = timeStamps.peekFirst();
          final long expectedTs2ns = expectedTs1ns + tickNanos;

          final long deviationNs = ts2.getNanos() - expectedTs2ns;

          final long interArrivalTimeNs = ts2.getNanos() - ts1.getNanos();
          checkState(interArrivalTimeNs > 0);

          // compute correction in interval of [ts1, ts2]
          final long correctionNs = GCLogMonitor.getInstance()
              .getPauseTimeInInterval(expectedTs1ms, ts2.getMillis());

          // if (interArrivalTimeNs >= allowedTickDuration.upperEndpoint()
          // .longValue()) {
          // the max is taken because the correction can be too big in certain
          // situations. Example: if GC took 1500ms, tick size is 1000ms then
          // the actual inter arrival time is probably in the range
          // 1500-1600ms. If you apply the correction directly, the inter
          // arrival time would be incorrectly low, therefore we make sure
          // that the corrected inter arrival time cannot be lower than the
          // tick length.

          // interArrivalTimeNs =
          // Math.max(interArrivalTimeNs - correctionNs, tickNanos);
          // }
          // if (!allowedTickDuration.contains(interArrivalTimeNs)) {

          long correctedDeviationNs = deviationNs;
          if (Math.abs(deviationNs) > maxTickDeviationNs) {
            correctedDeviationNs = Math.max(0, deviationNs - correctionNs);
          }

          if (Math.abs(correctedDeviationNs) > maxTickDeviationNs) {

            throw new IllegalStateException(
                // interArrivalTimeNs / 1000000d + " is invalid (allowed: "
                // + allowedTickDuration +

                "deviation is too much: " + deviationNs / MS_TO_NS
                    + " max allowed: " + maxTickDeviationNs / MS_TO_NS
                    + " correction: " + correctionNs / MS_TO_NS
                    + " interval " + logMonitor.toVMTime(expectedTs1ms)
                    + " " + logMonitor.toVMTime(ts2.getMillis())
                    + " expected event time: " + expectedTs2ns
                    + " actual event time: " + ts2.getNanos()
                    + System.lineSeparator()
                    + logMonitor.getPauseIntervals()
                    + System.lineSeparator()
                    + measuredDeviations);
          }
          measuredDeviations.addLast(
              MeasuredDeviation.create(correctedDeviationNs, correctionNs));
        }

        if (measuredDeviations.size() < CONSISTENCY_CHECK_LENGTH) {
          return;
        }

        // final SummaryStatistics ss = new SummaryStatistics();

        long sum = 0;
        for (final MeasuredDeviation md : measuredDeviations) {
          sum += md.getDeviationNs();
        }
        final double avgDeviation = sum / (double) measuredDeviations.size();
        checkState(Math.abs(avgDeviation) < maxAvgDeviationNs,
            "Avg deviation is above threshold of %sns: %sns.",
            maxAvgDeviationNs,
            avgDeviation, measuredDeviations);
            // checkState(
            // Math.abs(tickNanos - sum.getMean()) < maxMeanDeviationNs,
            // "Mean interval is above threshold of %sns: %s.",
            // maxMeanDeviationNs, sum.getMean());

        // remove old values to free memory
        while (measuredDeviations.size() > CONSISTENCY_CHECK_LENGTH) {
          measuredDeviations.removeFirst();
        }
      }
    }

    @AutoValue
    abstract static class MeasuredDeviation {

      abstract long getDeviationNs();

      abstract long getCorrectionNs();

      static MeasuredDeviation create(long dev, long corr) {
        return new AutoValue_RealtimeModel_Realtime_MeasuredDeviation(dev,
            corr);
      }
    }

    @AutoValue
    abstract static class TimeStamp {

      abstract long getTickCount();

      abstract long getMillis();

      abstract long getNanos();

      static TimeStamp now(long tickCount) {
        return create(tickCount, System.currentTimeMillis(), System.nanoTime());
      }

      static TimeStamp create(long tickCount, long millis, long nanos) {
        return new AutoValue_RealtimeModel_Realtime_TimeStamp(tickCount, millis,
            nanos);
      }
    }
  }

  @SuppressWarnings("null")
  enum SimpleState implements ClockState {
    INIT_RT {
      @Override
      public ClockMode getClockMode() {
        return ClockMode.REAL_TIME;
      }
    },
    INIT_ST {
      @Override
      public ClockMode getClockMode() {
        return ClockMode.SIMULATED;
      }
    },
    STOPPED {
      @Override
      public ClockMode getClockMode() {
        return ClockMode.STOPPED;
      }
    };

    @Override
    @Nullable
    public Trigger handle(@Nullable Trigger event, RealtimeModel context) {
      return null;
    }

    @Override
    public void onEntry(Trigger event, RealtimeModel context) {}

    @Override
    public void onExit(Trigger event, RealtimeModel context) {}

  }

  static class TickListenerTimingChecker implements TickListener {
    final TickListener delegate;
    final long maxTickDuration;
    long tickDuration;

    TickListenerTimingChecker(TickListener tl, long mxTickDuration) {
      delegate = tl;
      maxTickDuration = mxTickDuration;
    }

    @Override
    public void afterTick(TimeLapse timeLapse) {
      final long start = System.nanoTime();
      delegate.afterTick(timeLapse);
      final long afterTickDuration = System.nanoTime() - start;

      checkState(
          tickDuration + afterTickDuration < maxTickDuration,
          "%s took too much time, max combined computation time is tick length "
              + "(%sms). Tick duration: %sns, after tick duration: %sns.",
          delegate, timeLapse.getTickLength(), tickDuration, afterTickDuration);
    }

    @Override
    public void tick(TimeLapse timeLapse) {
      final long start = System.nanoTime();
      delegate.tick(timeLapse);
      tickDuration = System.nanoTime() - start;
    }
  }
}
