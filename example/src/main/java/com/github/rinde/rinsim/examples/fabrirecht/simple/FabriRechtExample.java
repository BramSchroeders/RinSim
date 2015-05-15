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
package com.github.rinde.rinsim.examples.fabrirecht.simple;

import java.io.File;
import java.io.IOException;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModels;
import com.github.rinde.rinsim.core.model.road.RoadUser;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.core.pdptw.DefaultVehicle;
import com.github.rinde.rinsim.core.pdptw.VehicleDTO;
import com.github.rinde.rinsim.pdptw.common.StatsTracker;
import com.github.rinde.rinsim.scenario.AddVehicleEvent;
import com.github.rinde.rinsim.scenario.ScenarioController;
import com.github.rinde.rinsim.scenario.TimedEventHandler;
import com.github.rinde.rinsim.scenario.fabrirecht.FabriRechtParser;
import com.github.rinde.rinsim.scenario.fabrirecht.FabriRechtScenario;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.PDPModelRenderer;
import com.github.rinde.rinsim.ui.renderers.PlaneRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

/**
 * Simplest example showing how the Fabri {@literal &} Recht problem can be
 * configured using a custom vehicle.
 *
 * @author Rinde van Lon
 */
public final class FabriRechtExample {

  private FabriRechtExample() {}

  public static void main2(String[] args) throws IOException {
    // we load a problem instance from disk, we instantiate it with 8
    // trucks, each with a capacity of 20 units
    final FabriRechtScenario scenario = FabriRechtParser
      .fromJson(Files.toString(
        new File("../scenario-util/files/test/fabri-recht/lc101.scenario"),
        Charsets.UTF_8), 8, 20);

    // we plug our custom vehicle in by specifying a creator
    final ImmutableMap<Class<?>, TimedEventHandler<?>> m = ImmutableMap
      .<Class<?>, TimedEventHandler<?>> of(
        AddVehicleEvent.class, new TimedEventHandler<AddVehicleEvent>() {
          @Override
          public void handleTimedEvent(AddVehicleEvent event, SimulatorAPI sim) {
            sim.register(new Truck(event.vehicleDTO));
          }
        });

    // instantiate the simulator using the scenario
    final Simulator sim = Simulator.builder()
      .addModel(ScenarioController.builder(scenario)
        .withEventHandlers(m)
      )
      .addModel(StatsTracker.builder())
      .addModel(View.builder().withAutoPlay()
        .with(PlaneRoadModelRenderer.builder())
        .with(PDPModelRenderer.builder())
        .with(RoadUserRenderer.builder())
      )
      .build();

    // start the simulation
    sim.start();

    // simulation is done, lets print the statistics!
    System.out.println(sim.getModelProvider().getModel(StatsTracker.class)
      .getStatistics());
  }
}

/**
 * This truck implementation only picks parcels up, it does not deliver them.
 *
 * @author Rinde van Lon
 */
class Truck extends DefaultVehicle {

  public Truck(VehicleDTO pDto) {
    super(pDto);
  }

  @Override
  protected void tickImpl(TimeLapse time) {
    final RoadModel rm = roadModel.get();
    final PDPModel pm = pdpModel.get();
    // we always go to the closest available parcel
    final DefaultParcel closest = (DefaultParcel) RoadModels
      .findClosestObject(rm.getPosition(this), rm, new Predicate<RoadUser>() {
        @Override
        public boolean apply(RoadUser input) {
          return input instanceof DefaultParcel
            && pm.getParcelState((DefaultParcel) input) == ParcelState.AVAILABLE;
        }
      });

    if (closest != null) {
      rm.moveTo(this, closest, time);
      if (rm.equalPosition(closest, this)
        && pm
          .getTimeWindowPolicy()
          .canPickup(closest.getPickupTimeWindow(), time.getTime(),
            closest.getPickupDuration())) {
        pm.pickup(this, closest, time);
      }
    }
  }
}
