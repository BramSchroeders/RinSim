/**
 * 
 */
package rinde.sim.pdptw.fabrirecht;

import java.util.List;
import java.util.Set;

import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Length;
import javax.measure.quantity.Velocity;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import rinde.sim.core.graph.Point;
import rinde.sim.core.model.Model;
import rinde.sim.core.model.pdp.TimeWindowPolicy.TimeWindowPolicies;
import rinde.sim.core.model.road.PlaneRoadModel;
import rinde.sim.core.pdptw.VehicleDTO;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.StopConditions;
import rinde.sim.pdptw.scenario.Models;
import rinde.sim.scenario.Scenario;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.util.TimeWindow;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/**
 * A scenario for Fabri & Recht problems.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class FabriRechtScenario extends Scenario {
  /**
   * Minimum position.
   */
  public final Point min;
  /**
   * Maximum position.
   */
  public final Point max;
  /**
   * Time window of the scenario.
   */
  public final TimeWindow timeWindow;
  /**
   * The default vehicle.
   */
  public final VehicleDTO defaultVehicle;

  FabriRechtScenario(Point pMin, Point pMax, TimeWindow pTimeWindow,
      VehicleDTO pDefaultVehicle) {
    super();
    min = pMin;
    max = pMax;
    timeWindow = pTimeWindow;
    defaultVehicle = pDefaultVehicle;
  }

  /**
   * Create a new scenario.
   * @param pEvents The event list.
   * @param pSupportedTypes The event types.
   * @param pMin {@link #min}.
   * @param pMax {@link #max}.
   * @param pTimeWindow {@link #timeWindow}.
   * @param pDefaultVehicle {@link #defaultVehicle}.
   */
  public FabriRechtScenario(List<? extends TimedEvent> pEvents,
      Set<Enum<?>> pSupportedTypes, Point pMin, Point pMax,
      TimeWindow pTimeWindow, VehicleDTO pDefaultVehicle) {
    super(pEvents, pSupportedTypes);
    min = pMin;
    max = pMax;
    timeWindow = pTimeWindow;
    defaultVehicle = pDefaultVehicle;
  }

  @Override
  public TimeWindow getTimeWindow() {
    return timeWindow;
  }

  @Override
  public long getTickSize() {
    return 1L;
  }

  @Override
  public StopConditions getStopCondition() {
    return StopConditions.TIME_OUT_EVENT;
  }

  @Override
  public ImmutableList<? extends Supplier<? extends Model<?>>> getModelSuppliers() {
    return ImmutableList.<Supplier<? extends Model<?>>> builder()
        .add(PlaneRoadModel.supplier(min, max, getDistanceUnit(),
            Measure.valueOf(100d, getSpeedUnit())))
        .add(Models.pdpModel(TimeWindowPolicies.TARDY_ALLOWED))
        .build();
  }

  @Override
  public Unit<Duration> getTimeUnit() {
    return NonSI.MINUTE;
  }

  @Override
  public Unit<Velocity> getSpeedUnit() {
    return SI.KILOMETRE.divide(NonSI.MINUTE).asType(Velocity.class);
  }

  @Override
  public Unit<Length> getDistanceUnit() {
    return SI.KILOMETER;
  }

  @Override
  public ProblemClass getProblemClass() {
    return FabriRechtProblemClass.SINGLETON;
  }

  @Override
  public String getProblemInstanceId() {
    return "1";
  }

  enum FabriRechtProblemClass implements ProblemClass {
    SINGLETON;

    @Override
    public String getId() {
      return "fabrirecht";
    }
  }
}
