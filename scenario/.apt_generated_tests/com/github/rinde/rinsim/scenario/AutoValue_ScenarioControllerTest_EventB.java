

package com.github.rinde.rinsim.scenario;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_ScenarioControllerTest_EventB extends ScenarioControllerTest.EventB {

  private final long time;

  AutoValue_ScenarioControllerTest_EventB(
      long time) {
    this.time = time;
  }

  @Override
  public long getTime() {
    return time;
  }

  @Override
  public String toString() {
    return "EventB{"
         + "time=" + time
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ScenarioControllerTest.EventB) {
      ScenarioControllerTest.EventB that = (ScenarioControllerTest.EventB) o;
      return (this.time == that.getTime());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= (int) ((time >>> 32) ^ time);
    return h$;
  }

}
