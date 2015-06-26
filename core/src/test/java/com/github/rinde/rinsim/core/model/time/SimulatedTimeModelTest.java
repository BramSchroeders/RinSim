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

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.util.Collection;

import javax.measure.unit.NonSI;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.github.rinde.rinsim.core.model.time.Clock.ClockEventType;
import com.github.rinde.rinsim.core.model.time.TimeModel.Builder;
import com.github.rinde.rinsim.event.ListenerEventHistory;

/**
 * @author Rinde van Lon
 *
 */
public class SimulatedTimeModelTest extends TimeModelTest<SimulatedTimeModel> {

  /**
   * @param sup
   */
  public SimulatedTimeModelTest(Builder sup) {
    super(sup);
  }

  /**
   * @return The models to test.
   */
  @Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {
        { TimeModel.builder() },
        { TimeModel.builder().withTickLength(333L).withTimeUnit(NonSI.HOUR) }
    });
  }

  /**
   * Test starting and stopping time.
   */
  @Test
  public void testStartStop() {
    final LimitingTickListener ltl = new LimitingTickListener(getModel(), 3);
    final ListenerEventHistory leh = new ListenerEventHistory();

    getModel().getEventAPI().addListener(leh, ClockEventType.values());
    getModel().register(ltl);
    getModel().start();
    assertEquals(3 * getModel().getTickLength(), getModel().getCurrentTime());

    getModel().start();

    assertEquals(6 * getModel().getTickLength(), getModel().getCurrentTime());
    assertThat(leh.getEventTypeHistory()).isEqualTo(
      asList(
        ClockEventType.STARTED,
        ClockEventType.STOPPED,
        ClockEventType.STARTED,
        ClockEventType.STOPPED)
      );
  }
}
