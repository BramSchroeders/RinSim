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
package com.github.rinde.rinsim.ui;

import static com.google.common.base.Verify.verifyNotNull;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.eclipse.swt.widgets.Display;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.TickListener;
import com.github.rinde.rinsim.core.TimeLapse;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.road.PlaneRoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.testutil.GuiTests;
import com.github.rinde.rinsim.ui.renderers.PlaneRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;
import com.github.rinde.rinsim.ui.renderers.TestRenderer;
import com.github.rinde.rinsim.ui.renderers.UiSchema;

/**
 * Test for {@link View}.
 * @author Rinde van Lon
 */
@Category(GuiTests.class)
public class ViewTest {

  /**
   * Simple GUI test.
   */
  @Test
  public void testRenderer() {
    final Simulator sim = Simulator.builder()
        .addModel(PlaneRoadModel.builder().build())
        .build();

    View.create(sim)
        .setTitleAppendix("ViewTest")
        .enableAutoClose()
        .enableAutoPlay()
        .stopSimulatorAtTime(10000)
        .with(PlaneRoadModelRenderer.create())
        .with(new TestRenderer())
        .show();
  }

  /**
   * Closes the window while the simulator is running. This emulates what
   * happens when a user closes the window.
   */
  @Test
  public void closeWindowWhilePlaying() {
    final Simulator sim = Simulator.builder()
        .addModel(PlaneRoadModel.builder().build())
        .addModel(new DefaultPDPModel())
        .build();

    sim.addTickListener(new TickListener() {
      @Override
      public void tick(TimeLapse timeLapse) {
        if (timeLapse.getTime() >= 15 * 1000) {
          final Display disp = UITestTools.findDisplay();
          verifyNotNull(disp).syncExec(
              new Runnable() {
                @Override
                public void run() {
                  verifyNotNull(disp).getActiveShell().close();
                }
              });
        }
      }

      @Override
      public void afterTick(TimeLapse timeLapse) {}
    });

    sim.register(new TestDepot());
    final UiSchema uis = new UiSchema();
    uis.add(TestDepot.class, "/graphics/perspective/tall-building-64.png");
    View.create(sim)
        .setTitleAppendix("ViewTest")
        .with(PlaneRoadModelRenderer.create())
        .with(RoadUserRenderer.builder())
        .enableAutoPlay()
        .show();
  }

  /**
   * Tests a view with a simulator that is not configured.
   */
  @Test(expected = IllegalStateException.class)
  public void failNotConfiguredSim() {
    @SuppressWarnings("deprecation")
    final Simulator sim = new Simulator(new MersenneTwister(123),
        Measure.valueOf(1000L, SI.MILLI(SI.SECOND)));
    View.create(sim).show();
  }

  static class TestDepot extends Depot {
    public TestDepot() {
      setStartPosition(new Point(1, 1));
    }

    @Override
    public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}
  }
}
