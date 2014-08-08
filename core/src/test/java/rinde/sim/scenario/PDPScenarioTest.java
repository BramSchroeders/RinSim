package rinde.sim.scenario;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Length;
import javax.measure.quantity.Velocity;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.junit.Test;

import rinde.sim.core.Simulator;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.Model;
import rinde.sim.core.model.pdp.PDPScenarioEvent;
import rinde.sim.core.model.road.PlaneRoadModel;
import rinde.sim.scenario.Scenario.ProblemClass;
import rinde.sim.scenario.Scenario.SimpleProblemClass;
import rinde.sim.util.TimeWindow;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link Scenario} and its builder.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class PDPScenarioTest {

  /**
   * Test the default settings of a scenario.
   */
  @Test
  public void testDefaults() {
    final Scenario scenario = Scenario
        .builder(Scenario.DEFAULT_PROBLEM_CLASS)
        .addEventType(FakeEventType.A)
        .build();

    assertEquals(SI.KILOMETER, scenario.getDistanceUnit());
    assertTrue(scenario.getModelSuppliers().isEmpty());
    assertEquals(newHashSet(FakeEventType.A), scenario.getPossibleEventTypes());
    assertSame(Scenario.DEFAULT_PROBLEM_CLASS, scenario.getProblemClass());
    assertEquals("", scenario.getProblemInstanceId());
    assertEquals(NonSI.KILOMETERS_PER_HOUR, scenario.getSpeedUnit());
    assertEquals(Predicates.alwaysFalse(), scenario.getStopCondition());
    assertEquals(1000L, scenario.getTickSize());
    assertEquals(SI.MILLI(SI.SECOND), scenario.getTimeUnit());
    assertEquals(new TimeWindow(0, 8 * 60 * 60 * 1000),
        scenario.getTimeWindow());
  }

  /**
   * Test correct ordering of events.
   */
  @Test
  public void testAddEvents() {
    final TimedEvent ev0 = new TimedEvent(FakeEventType.A, 0);
    final TimedEvent ev1 = new TimedEvent(FakeEventType.B, 205);
    final TimedEvent ev2 = new TimedEvent(FakeEventType.B, 7);
    final TimedEvent ev3 = new TimedEvent(FakeEventType.B, 203);
    final Scenario scenario = Scenario
        .builder(Scenario.DEFAULT_PROBLEM_CLASS)
        .addEvent(ev0)
        .addEvent(ev1)
        .addEvents(asList(ev2, ev3))
        // redundant adding of event types, should not make any difference
        .addEventTypes(asList(FakeEventType.A, FakeEventType.B))
        .build();
    assertEquals(asList(ev0, ev2, ev3, ev1), scenario.asList());
    assertEquals(newHashSet(FakeEventType.A, FakeEventType.B),
        scenario.getPossibleEventTypes());
  }

  /**
   * Test all modifying methods.
   */
  @Test
  public void testCustomProperties() {
    final ProblemClass problemClass = new FakeProblemClass();
    final Scenario scenario = Scenario
        .builder(problemClass)
        .distanceUnit(SI.PICO(SI.METER))
        .speedUnit(NonSI.MACH)
        .timeUnit(NonSI.DAY_SIDEREAL)
        .instanceId("crazyfast")
        .scenarioLength(7L)
        .tickSize(1)
        .addEventType(PDPScenarioEvent.TIME_OUT)
        .stopCondition(Predicates.<Simulator> alwaysTrue())
        .addModel(PlaneRoadModel.supplier(new Point(6, 6), new Point(1034,
            32), SI.METER, Measure.valueOf(1d, SI.METERS_PER_SECOND)))
        .build();

    assertEquals(SI.PICO(SI.METER), scenario.getDistanceUnit());
    assertEquals(NonSI.MACH, scenario.getSpeedUnit());
    assertEquals(NonSI.DAY_SIDEREAL, scenario.getTimeUnit());
    assertEquals("crazyfast", scenario.getProblemInstanceId());
    assertEquals(new TimeWindow(0L, 7L), scenario.getTimeWindow());
    assertEquals(1L, scenario.getTickSize());
    assertEquals(newHashSet(PDPScenarioEvent.TIME_OUT),
        scenario.getPossibleEventTypes());
    assertEquals(Predicates.alwaysTrue(), scenario.getStopCondition());
    assertEquals(1, scenario.getModelSuppliers().size());
    assertTrue(scenario.getModelSuppliers().get(0).get() instanceof PlaneRoadModel);

    final Scenario.Builder builder = Scenario
        .builder(Scenario.DEFAULT_PROBLEM_CLASS)
        .copyProperties(scenario);

    final Scenario copiedScen = builder.build();
    assertEquals(scenario, copiedScen);

    final Scenario builderCopyScen = Scenario.builder(builder,
        Scenario.DEFAULT_PROBLEM_CLASS)
        .addEventType(PDPScenarioEvent.TIME_OUT)
        .build();

    assertNotEquals(copiedScen, builderCopyScen);
  }

  /**
   * Test no arg constructor.
   */
  @Test
  public void testNoArgConstructor() {
    final Scenario scenario = new EmptyScenario();
    assertTrue(scenario.asList().isEmpty());
  }

  /**
   * Test for correct behavior of
   * {@link Scenario.Builder#ensureFrequency(Predicate, int)}.
   */
  @Test
  public void testModifyEventsMethods() {
    final TimedEvent ev1 = new TimedEvent(PDPScenarioEvent.TIME_OUT, 10000L);
    final TimedEvent ev2 = new CustomEvent(FakeEventType.A, 7);
    final TimedEvent ev3 = new CustomEvent(FakeEventType.A, 3);
    final TimedEvent ev3b = new CustomEvent(FakeEventType.A, 3);
    final TimedEvent ev4 = new CustomEvent(FakeEventType.B, 3);
    final TimedEvent ev5 = new CustomEvent(FakeEventType.B, 367);
    final List<TimedEvent> events = asList(ev1, ev2, ev3, ev3b, ev4, ev5);

    final Scenario.Builder b = Scenario
        .builder(Scenario.DEFAULT_PROBLEM_CLASS)
        .addEvents(events)
        .clearEvents();
    assertTrue(b.eventList.isEmpty());
    assertTrue(b.eventTypeSet.isEmpty());
    b.addEvents(events)
        .ensureFrequency(Predicates.equalTo(ev3), 1);
    assertEquals(asList(ev1, ev2, ev4, ev5, ev3), b.eventList);

    // should add two instances of ev1
    b.ensureFrequency(Predicates.equalTo(ev1), 3);
    assertEquals(asList(ev1, ev2, ev4, ev5, ev3, ev1, ev1), b.eventList);

    // frequency already achieved, nothing should change
    b.ensureFrequency(Predicates.equalTo(ev1), 3);
    assertEquals(asList(ev1, ev2, ev4, ev5, ev3, ev1, ev1), b.eventList);

    // only custom event instances remain
    b.filterEvents(Predicates.instanceOf(CustomEvent.class));
    assertEquals(asList(ev2, ev4, ev5, ev3), b.eventList);
  }

  /**
   * Negative frequency is not allowed.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testEnsureFrequencyFailFrequency() {
    Scenario.builder(Scenario.DEFAULT_PROBLEM_CLASS)
        .ensureFrequency(Predicates.<TimedEvent> alwaysTrue(), -1);
  }

  /**
   * Empty events list is not allowed.
   */
  @Test(expected = IllegalStateException.class)
  public void testEnsureFrequencyFailEmptyEventsList() {
    Scenario.builder(Scenario.DEFAULT_PROBLEM_CLASS)
        .ensureFrequency(Predicates.<TimedEvent> alwaysTrue(), 1);
  }

  /**
   * Filter must match at least one event.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testEnsureFrequencyFailFilter1() {
    Scenario.builder(Scenario.DEFAULT_PROBLEM_CLASS)
        .addEvent(new CustomEvent(FakeEventType.A, 0))
        .ensureFrequency(Predicates.<TimedEvent> alwaysFalse(), 1);
  }

  /**
   * Filter matches must be equal.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testEnsureFrequencyFailFilter2() {
    Scenario
        .builder(Scenario.DEFAULT_PROBLEM_CLASS)
        .addEvent(new CustomEvent(FakeEventType.A, 0))
        .addEvent(new CustomEvent(FakeEventType.A, 1))
        .ensureFrequency(Predicates.instanceOf(CustomEvent.class), 1);
  }

  /**
   * Tests for {@link SimpleProblemClass}.
   */
  @Test
  public void testSimpleProblemClass() {
    final ProblemClass pc = new SimpleProblemClass("hello world");
    assertEquals("hello world", pc.getId());
    assertTrue(pc.toString().contains("hello world"));
  }

  static class CustomEvent extends TimedEvent {
    CustomEvent(Enum<?> type, long timestamp) {
      super(type, timestamp);
    }
  }

  static class EmptyScenario extends Scenario {

    @Override
    public ImmutableList<? extends Supplier<? extends Model<?>>> getModelSuppliers() {
      throw new UnsupportedOperationException();
    }

    @Override
    public TimeWindow getTimeWindow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getTickSize() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Predicate<Simulator> getStopCondition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Unit<Duration> getTimeUnit() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Unit<Velocity> getSpeedUnit() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Unit<Length> getDistanceUnit() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ProblemClass getProblemClass() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getProblemInstanceId() {
      throw new UnsupportedOperationException();
    }
  }

  static class FakeProblemClass implements ProblemClass {
    @Override
    public String getId() {
      return "fake";
    }
  }

  enum FakeEventType {
    A, B;
  }
}
