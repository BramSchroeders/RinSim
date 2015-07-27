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

import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

import javax.annotation.Nullable;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * TODO doc.
 * @author Rinde van Lon
 */
public class SolverToRealtimeAdapter implements RealtimeSolver {
  Optional<Scheduler> scheduler;
  Optional<ListenableFuture<ImmutableList<ImmutableList<Parcel>>>> currentFuture;
  private final Solver solver;

  public SolverToRealtimeAdapter(Solver s) {
    solver = s;
    currentFuture = Optional.absent();
    scheduler = Optional.absent();
  }

  @Override
  public void init(Scheduler s) {
    scheduler = Optional.of(s);
  }

  @Override
  public void receiveSnapshot(GlobalStateObject snapshot) {
    checkState(scheduler.isPresent(), "Not yet initialized.");
    if (currentFuture.isPresent() && !currentFuture.get().isDone()) {
      currentFuture.get().cancel(true);
    }
    currentFuture = Optional.of(
      scheduler.get().getSharedExecutor().submit(
        new SolverComputer(solver, snapshot)));
    Futures.addCallback(currentFuture.get(),
      new FutureCallback<ImmutableList<ImmutableList<Parcel>>>() {
        @Override
        public void onSuccess(
            @Nullable ImmutableList<ImmutableList<Parcel>> result) {
          if (result == null) {
            throw new IllegalArgumentException(
                "Solver.solve(..) must return a non-null result.");
          }
          scheduler.get().updateSchedule(result);
          scheduler.get().doneForNow();
        }

        @Override
        public void onFailure(Throwable t) {
          if (t instanceof CancellationException) {
            return;
          }
          throw new IllegalStateException(t);
        }
      });
  }

  static class SolverComputer
      implements Callable<ImmutableList<ImmutableList<Parcel>>> {
    final Solver solver;
    final GlobalStateObject snapshot;

    SolverComputer(Solver sol, GlobalStateObject snap) {
      solver = sol;
      snapshot = snap;
    }

    @Override
    public ImmutableList<ImmutableList<Parcel>> call() throws Exception {
      return solver.solve(snapshot);
    }
  }
}
