/**
 * 
 */
package rinde.sim.pdptw.common;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;

import javax.measure.quantity.Duration;
import javax.measure.quantity.Length;
import javax.measure.quantity.Velocity;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.junit.Test;

import rinde.sim.core.Simulator;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.Model;
import rinde.sim.core.model.pdp.DefaultPDPModel;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.PDPScenarioEvent;
import rinde.sim.core.model.pdp.twpolicy.TardyAllowedPolicy;
import rinde.sim.core.model.road.PlaneRoadModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.SimulationInfo;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.StopCondition;
import rinde.sim.pdptw.scenario.PDPScenario;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.util.TimeWindow;

import com.google.common.collect.ImmutableList;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class TestDynamicPDPTWProblem {

  protected static final Creator<AddVehicleEvent> DUMMY_ADD_VEHICLE_EVENT_CREATOR = new Creator<AddVehicleEvent>() {
    @Override
    public boolean create(Simulator sim, AddVehicleEvent event) {
      return true;
    }
  };

  /**
   * Checks for the absence of a creator for AddVehicleEvent.
   */
  @Test(expected = IllegalStateException.class)
  public void noVehicleCreator() {
    final Set<TimedEvent> events = newHashSet(new TimedEvent(
        PDPScenarioEvent.ADD_DEPOT, 10));
    new DynamicPDPTWProblem(new DummyScenario(events), 123).simulate();
  }

  @Test
  public void testStopCondition() {
    final Set<TimedEvent> events = newHashSet(new TimedEvent(
        PDPScenarioEvent.ADD_DEPOT, 10));
    final DynamicPDPTWProblem prob = new DynamicPDPTWProblem(new DummyScenario(
        events), 123);
    prob.addCreator(AddVehicleEvent.class, DUMMY_ADD_VEHICLE_EVENT_CREATOR);

    prob.addStopCondition(new TimeStopCondition(4));
    final StatisticsDTO stats = prob.simulate();

    assertEquals(5, stats.simulationTime);
  }

  class TimeStopCondition extends StopCondition {
    protected final long time;

    public TimeStopCondition(long t) {
      time = t;
    }

    @Override
    public boolean apply(SimulationInfo context) {
      return context.stats.simulationTime == time;
    }
  }

  class DummyScenario extends PDPScenario {

    public DummyScenario(Set<TimedEvent> events) {
      super(events, new HashSet<Enum<?>>(
          java.util.Arrays.asList(PDPScenarioEvent.values())));
    }

    @Override
    public TimeWindow getTimeWindow() {
      return TimeWindow.ALWAYS;
    }

    @Override
    public long getTickSize() {
      return 1;
    }

    @Override
    public StopCondition getStopCondition() {
      return StopCondition.TIME_OUT_EVENT;
    }

    @Override
    public ImmutableList<Model<?>> createModels() {
      return ImmutableList.<Model<?>> of(createRoadModel(), createPDPModel());
    }

    RoadModel createRoadModel() {
      return new PlaneRoadModel(new Point(0, 0), new Point(10, 10), 1d);
    }

    PDPModel createPDPModel() {
      return new DefaultPDPModel(new TardyAllowedPolicy());
    }

    @Override
    public Unit<Duration> getTimeUnit() {
      return SI.SECOND;
    }

    @Override
    public Unit<Velocity> getSpeedUnit() {
      throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public Unit<Length> getDistanceUnit() {
      throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public ProblemClass getProblemClass() {
      throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public String getProblemInstanceId() {
      throw new UnsupportedOperationException("Not implemented.");
    }

  }

}
