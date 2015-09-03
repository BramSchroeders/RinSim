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

import static com.github.rinde.rinsim.central.GlobalStateObjectBuilder.globalBuilder;
import static com.github.rinde.rinsim.central.GlobalStateObjectBuilder.vehicleBuilder;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class ScheduleUtilTest {

  @SuppressWarnings("null")
  Parcel p1, p2, p3;

  @Before
  public void setUp() {
    p1 = new ParcelDecorator(Parcel.builder(new Point(0, 0), new Point(1, 1))
        .serviceDuration(100)
        .build(), "p1");
    p2 = new ParcelDecorator(Parcel.builder(new Point(1, 1), new Point(1, 2))
        .serviceDuration(100)
        .build(), "p2");
    p3 = new ParcelDecorator(Parcel.builder(new Point(1, 3), new Point(1, 2))
        .serviceDuration(100)
        .build(), "p3");
  }

  static class ParcelDecorator extends Parcel {

    private final String name;

    public ParcelDecorator(Parcel p, String parcelName) {
      super(p.getDto());
      name = parcelName;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * Tests that parcel that is being picked up is added to the route if it is
   * missing.
   */
  @Test
  public void testFixSchedule1() {
    final GlobalStateObject state = globalBuilder()
        .addAvailableParcel(p1)
        .addVehicle(vehicleBuilder()
            .setDestination(p1)
            .setRemainingServiceTime(10L)
            .build())
        .build();

    // parcel is being picked up, but appears only n times in route
    assertThat(fix(schedule(route()), state)).containsExactly(route(p1, p1));
    assertThat(fix(schedule(route(p1)), state)).containsExactly(route(p1, p1));
    assertThat(fix(schedule(route(p1, p1)), state))
        .containsExactly(route(p1, p1));
  }

  /**
   * Situation with two available parcels, one is being picked up.
   */
  @Test
  public void testFixSchedule2() {
    final GlobalStateObject state = globalBuilder()
        .addAvailableParcels(p1, p2)
        .addVehicle(vehicleBuilder()
            .setDestination(p1)
            .setRemainingServiceTime(10L)
            .build())
        .addVehicle(vehicleBuilder().build())
        .build();

    assertThat(fix(schedule(route(), route()), state))
        .isEqualTo(schedule(route(p1, p1), route()));

    assertThat(fix(schedule(route(), route(p1)), state))
        .isEqualTo(schedule(route(p1, p1), route()));

    assertThat(fix(schedule(route(), route(p1, p1)), state))
        .isEqualTo(schedule(route(p1, p1), route()));

    assertThat(fix(schedule(route(), route(p1, p2, p1)), state))
        .isEqualTo(schedule(route(p1, p1), route(p2)));

    assertThat(fix(schedule(route(), route(p1, p2, p3, p1)), state))
        .isEqualTo(schedule(route(p1, p1), route(p2)));

    assertThat(fix(schedule(route(p2), route(p1, p3, p1)), state))
        .isEqualTo(schedule(route(p1, p2, p1), route()));
  }

  /**
   * Situation with two available parcels, one is being picked up.
   */
  @Test
  public void testFixSchedule3() {
    final GlobalStateObject state = globalBuilder()
        .addAvailableParcels(p1)
        .addVehicle(vehicleBuilder()
            .addToContents(p2)
            .setRemainingServiceTime(10L)
            .build())
        .addVehicle(vehicleBuilder().build())
        .build();

    assertThat(fix(schedule(route(), route()), state))
        .isEqualTo(schedule(route(p2), route()));

    assertThat(fix(schedule(route(p2, p2), route(p1, p3, p1)), state))
        .isEqualTo(schedule(route(p2), route(p1, p1)));

    assertThat(fix(schedule(route(p1, p2, p3, p1, p2), route()), state))
        .isEqualTo(schedule(route(p1, p1, p2), route()));
  }

  static List<List<Parcel>> fix(ImmutableList<ImmutableList<Parcel>> schedule,
      GlobalStateObject state) {
    return ScheduleUtil.fixSchedule(schedule, state);
  }

  static ImmutableList<Parcel> route(Parcel... ps) {
    return ImmutableList.copyOf(asList(ps));
  }

  @SafeVarargs
  static ImmutableList<ImmutableList<Parcel>> schedule(
      ImmutableList<Parcel>... rs) {
    return ImmutableList.copyOf(rs);
  }
}