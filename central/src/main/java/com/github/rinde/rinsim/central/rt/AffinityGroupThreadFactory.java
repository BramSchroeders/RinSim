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
package com.github.rinde.rinsim.central.rt;

import static com.google.common.base.Verify.verifyNotNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import net.openhft.affinity.AffinityLock;
import net.openhft.affinity.AffinitySupport;

/**
 * A {@link ThreadFactory} that creates {@link Thread}s that all have an
 * affinity for the <i>same</i> CPU. When used in a thread pool, the entire
 * thread pool will be run on the same CPU.
 * @author Rinde van Lon
 */
public class AffinityGroupThreadFactory implements ThreadFactory {
  private final String threadNamePrefix;
  private final boolean createDaemonThreads;
  private final AtomicInteger numThreads;
  private final Object lock;
  @Nullable
  private AffinityLock lastAffinityLock;
  private int id;

  /**
   * Create a new instance where threads get the specified name prefix. All
   * created threads are daemon threads, see {@link Thread#setDaemon(boolean)}
   * for more information.
   * @param name The thread name prefix.
   */
  public AffinityGroupThreadFactory(String name) {
    this(name, true);
  }

  /**
   * Create a new instance where threads get the specified name prefix. The
   * daemon property is set using {@link Thread#setDaemon(boolean)}.
   * @param name The thread name prefix.
   * @param daemon Indicates whether all threads created by this factory are
   *          daemon threads.
   */
  public AffinityGroupThreadFactory(String name, boolean daemon) {
    numThreads = new AtomicInteger();
    threadNamePrefix = name;
    createDaemonThreads = daemon;
    lock = new Object();
  }

  @Override
  public synchronized Thread newThread(@Nullable final Runnable r) {
    final String threadName =
      id == 0 ? threadNamePrefix : threadNamePrefix + '-' + id;
    numThreads.incrementAndGet();
    final Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        synchronized (lock) {
          if (lastAffinityLock != null) {
            final AffinityLock al = lastAffinityLock;
            AffinitySupport.setAffinity(1 << al.cpuId());
          } else {
            lastAffinityLock = AffinityLock.acquireLock();
          }
        }
        try {
          verifyNotNull(r).run();
        } finally {
          synchronized (lock) {
            if (numThreads.decrementAndGet() == 0 && lastAffinityLock != null) {
              lastAffinityLock.release();
              lastAffinityLock = null;
            }
          }
        }
      }
    }, threadName);
    t.setDaemon(createDaemonThreads);
    return t;
  }
}
