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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.Executors;

import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.central.Solvers.SimulationConverter;
import com.github.rinde.rinsim.central.Solvers.SolveArgs;
import com.github.rinde.rinsim.central.Solvers.StateContext;
import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.Model.AbstractModel;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.time.RealTimeClockController;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * @author Rinde van Lon
 *
 */
public final class RtSolverModel extends AbstractModel<RtSolverUser> {
  final RealTimeClockController clock;
  final PDPRoadModel roadModel;
  final PDPModel pdpModel;
  final RtSimSolverBuilder builder;

  RtSolverModel(RealTimeClockController c, PDPRoadModel rm, PDPModel pm) {
    clock = c;
    roadModel = rm;
    pdpModel = pm;
    builder = new RtSimSolverBuilderImpl();
  }

  @Override
  public boolean register(RtSolverUser element) {
    element.setSolverProvider(builder);
    return true;
  }

  @Override
  public boolean unregister(RtSolverUser element) {
    return true;
  }

  @Override
  public <U> U get(Class<U> clazz) {
    checkArgument(clazz == RtSimSolverBuilder.class,
      "%s does not provide this type: %s.", getClass().getSimpleName(), clazz);
    return clazz.cast(builder);
  }

  class RtSimSolverBuilderImpl extends RtSimSolverBuilder {
    @Override
    public RtSimSolver build(RealtimeSolver solver) {
      return new RtSimSolverSchedulerImpl(clock, solver, roadModel,
        pdpModel).rtSimSolver;
    }
  }

  public static Builder builder() {
    return new AutoValue_RtSolverModel_Builder();
  }

  @AutoValue
  public abstract static class Builder
    extends AbstractModelBuilder<RtSolverModel, RtSolverUser> {

    Builder() {
      setDependencies(RealTimeClockController.class, PDPRoadModel.class,
        PDPModel.class);
      setProvidingTypes(RtSimSolverBuilder.class);
    }

    @Override
    public RtSolverModel build(DependencyProvider dependencyProvider) {
      RealTimeClockController c = dependencyProvider
        .get(RealTimeClockController.class);
      PDPRoadModel rm = dependencyProvider.get(PDPRoadModel.class);
      PDPModel pm = dependencyProvider.get(PDPModel.class);
      return new RtSolverModel(c, rm, pm);
    }
  }

  static class RtSimSolverSchedulerImpl {
    final SimulationConverter converter;
    final RealtimeSolver solver;
    final RealTimeClockController clock;
    Optional<ImmutableList<ImmutableList<Parcel>>> currentSchedule;
    boolean isUpdated;
    final ListeningExecutorService executor;
    final RtSimSolver rtSimSolver;
    final Scheduler scheduler;

    RtSimSolverSchedulerImpl(RealTimeClockController c, RealtimeSolver s,
      PDPRoadModel rm, PDPModel pm) {
      solver = s;
      clock = c;
      converter = Solvers.converterBuilder()
        .with(clock)
        .with(rm)
        .with(pm)
        .build();
      currentSchedule = Optional.absent();
      isUpdated = false;

      executor = MoreExecutors
        .listeningDecorator(Executors.newSingleThreadExecutor());

      rtSimSolver = new InternalRtSimSolver();
      scheduler = new InternalScheduler();
      solver.init(scheduler);
    }

    class InternalRtSimSolver extends RtSimSolver {
      @Override
      public void solve(SolveArgs args) {
        final StateContext sc = converter.convert(args);
        executor.submit(new Runnable() {
          @Override
          public void run() {
            solver.receiveSnapshot(sc.state);
          }
        });
      }

      @Override
      public boolean isScheduleUpdated() {
        return isUpdated;
      }

      @Override
      public ImmutableList<ImmutableList<Parcel>> getCurrentSchedule() {
        isUpdated = false;
        return currentSchedule.get();
      }
    }

    class InternalScheduler extends Scheduler {
      @Override
      public void updateSchedule(ImmutableList<ImmutableList<Parcel>> routes) {
        currentSchedule = Optional.of(routes);
        isUpdated = true;

      }

      @Override
      public ImmutableList<ImmutableList<Parcel>> getCurrentSchedule() {
        checkState(currentSchedule.isPresent(),
          "No schedule has been set, use updateSchedule(..).");
        return currentSchedule.get();
      }

      @Override
      public void doneForNow() {
        clock.switchToSimulatedTime();
      }
    }
  }

}
