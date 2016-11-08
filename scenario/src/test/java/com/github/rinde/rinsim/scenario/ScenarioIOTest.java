/*
 * Copyright (C) 2011-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
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
package com.github.rinde.rinsim.scenario;

import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders.StaticGraphRMB;
import com.github.rinde.rinsim.core.model.time.RealtimeClockController.ClockMode;
import com.github.rinde.rinsim.core.model.time.TimeModel;
import com.github.rinde.rinsim.geom.LengthData;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.geom.TableGraph;
import com.github.rinde.rinsim.testutil.TestUtil;

/**
 *
 * @author Rinde van Lon
 */
public class ScenarioIOTest {

  /**
   * Tests unreachable code.
   */
  @BeforeClass
  public static void setUpClass() {
    TestUtil.testPrivateConstructor(ScenarioIO.class);
    TestUtil.testEnum(ScenarioIO.ClassIO.class);
    TestUtil.testEnum(ScenarioIO.EnumIO.class);
    TestUtil.testEnum(ScenarioIO.ImmutableListIO.class);
    TestUtil.testEnum(ScenarioIO.ImmutableSetIO.class);
    TestUtil.testEnum(ScenarioIO.MeasureIO.class);
    TestUtil.testEnum(ScenarioIO.ModelBuilderIO.class);
    TestUtil.testEnum(ScenarioIO.ParcelIO.class);
    TestUtil.testEnum(ScenarioIO.PredicateIO.class);
    TestUtil.testEnum(ScenarioIO.ProblemClassIO.class);
    TestUtil.testEnum(ScenarioIO.ScenarioObjIO.class);
    TestUtil.testEnum(ScenarioIO.StopConditionIO.class);
    TestUtil.testEnum(ScenarioIO.TimeWindowHierarchyIO.class);
    TestUtil.testEnum(ScenarioIO.UnitIO.class);
    TestUtil.testEnum(ScenarioIO.VehicleIO.class);
  }

  /**
   * Tests {@link ScenarioIO#readerAdapter(com.google.common.base.Function)}.
   * @throws IOException When IO fails.
   */
  @Test
  public void testReaderAdapter() throws IOException {
    final Scenario s = Scenario.builder()
      .addModel(TimeModel.builder().withTickLength(7L))
      .build();

    final Path tmpDir = Files.createTempDirectory("rinsim-scenario-io-test");
    final Path file = Paths.get(tmpDir.toString(), "test.scen");
    ScenarioIO.write(s, file);

    final Scenario out = ScenarioIO.reader().apply(file);
    final Scenario convertedOut =
      verifyNotNull(ScenarioIO.readerAdapter(ScenarioConverters.toRealtime())
        .apply(file));

    assertThat(s).isEqualTo(out);
    assertThat(s).isNotEqualTo(convertedOut);
    assertThat(convertedOut.getModelBuilders())
      .contains(TimeModel.builder()
        .withRealTime()
        .withStartInClockMode(ClockMode.SIMULATED)
        .withTickLength(7L));

    Files.delete(file);
    Files.delete(tmpDir);
  }

  static class TestObject {
    TableGraph<LengthData> g;
  }

  @Test
  public void testIO() {
    final TableGraph<LengthData> g = new TableGraph<>();

    g.addConnection(new Point(0, 0), new Point(1, 0));
    g.addConnection(new Point(1, 1), new Point(1, 0));

    final TestObject to = new TestObject();
    to.g = g;

    final String ser = ScenarioIO.GSON.toJson(to, TestObject.class);
    System.out.println(ser);

    final TestObject to2 = ScenarioIO.GSON.fromJson(ser, TestObject.class);

    assertThat(to.g).isEqualTo(to2.g);

    System.out.println();

    System.out.println();

    final Scenario s = Scenario.builder()
      .addModel(TimeModel.builder().withTickLength(7L))
      .addModel(RoadModelBuilders.staticGraph(g))
      .build();

    final String serialized = ScenarioIO.write(s);
    System.out.println(serialized);
    final Scenario deserialized = ScenarioIO.read(serialized);

    System.out.println(
      ((StaticGraphRMB) deserialized.getModelBuilders().asList().get(1))
        .getGraph());

    assertThat(s).isEqualTo(deserialized);
  }
}
