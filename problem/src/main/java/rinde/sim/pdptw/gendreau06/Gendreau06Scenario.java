/**
 * 
 */
package rinde.sim.pdptw.gendreau06;

import java.util.Collection;
import java.util.Set;

import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Length;
import javax.measure.quantity.Velocity;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import rinde.sim.core.graph.Point;
import rinde.sim.core.model.Model;
import rinde.sim.core.model.pdp.DefaultPDPModel;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.TimeWindowPolicy.TimeWindowPolicies;
import rinde.sim.core.model.road.PlaneRoadModel;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.SimulationInfo;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.StopConditions;
import rinde.sim.pdptw.common.PDPRoadModel;
import rinde.sim.pdptw.scenario.Models;
import rinde.sim.pdptw.scenario.PDPScenario;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.util.TimeWindow;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/**
 * 
 * The length of the scenario is a soft constraint. There is a pre defined
 * length of the day (either 4 hours or 7.5 hours), vehicles are allowed to
 * continue driving after the end of the day.
 * <p>
 * Once a vehicle is moving towards a Parcel it is obliged to service it. This
 * means that diversion is not allowed.
 * <p>
 * Distance is expressed in km, time is expressed in ms (the original format is
 * in seconds, however it allows fractions as such it was translated to ms),
 * speed is expressed as km/h.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public final class Gendreau06Scenario extends PDPScenario {
  static final Point MIN = new Point(0, 0);
  static final Point MAX = new Point(5, 5);
  static final Measure<Double, Velocity> MAX_SPEED = Measure.valueOf(
      30d, NonSI.KILOMETERS_PER_HOUR);

  private final long tickSize;
  private final GendreauProblemClass problemClass;
  private final int instanceNumber;
  private final boolean allowDiversion;

  Gendreau06Scenario(Collection<? extends TimedEvent> pEvents,
      Set<Enum<?>> pSupportedTypes, long ts, GendreauProblemClass problemClass,
      int instanceNumber, boolean diversion) {
    super(pEvents, pSupportedTypes);
    tickSize = ts;
    this.problemClass = problemClass;
    this.instanceNumber = instanceNumber;
    allowDiversion = diversion;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this,
        ToStringStyle.MULTI_LINE_STYLE);
  }

  @Override
  public TimeWindow getTimeWindow() {
    return TimeWindow.ALWAYS;
  }

  @Override
  public long getTickSize() {
    return tickSize;
  }

  @Override
  public Predicate<SimulationInfo> getStopCondition() {
    return Predicates.and(StopConditions.VEHICLES_DONE_AND_BACK_AT_DEPOT,
        StopConditions.TIME_OUT_EVENT);
  }

  @Override
  public ImmutableList<? extends Supplier<? extends Model<?>>> getModelSuppliers() {
    return ImmutableList.<Supplier<? extends Model<?>>> builder()
        .add(new RoadModelSupplier(getDistanceUnit(), allowDiversion))
        .add(Models.pdpModel(TimeWindowPolicies.TARDY_ALLOWED))
        .build();
  }

  PDPModel createPDPModel() {
    return new DefaultPDPModel(TimeWindowPolicies.TARDY_ALLOWED);
  }

  @Override
  public Unit<Duration> getTimeUnit() {
    return SI.MILLI(SI.SECOND);
  }

  @Override
  public Unit<Velocity> getSpeedUnit() {
    return NonSI.KILOMETERS_PER_HOUR;
  }

  @Override
  public Unit<Length> getDistanceUnit() {
    return SI.KILOMETER;
  }

  @Override
  public ProblemClass getProblemClass() {
    return problemClass;
  }

  @Override
  public String getProblemInstanceId() {
    return Integer.toString(instanceNumber);
  }

  static class RoadModelSupplier implements Supplier<PDPRoadModel> {
    private final Unit<Length> distanceUnit;
    private final boolean allowDiversion;

    RoadModelSupplier(Unit<Length> du, boolean ad) {
      distanceUnit = du;
      allowDiversion = ad;
    }

    @Override
    public PDPRoadModel get() {
      return new PDPRoadModel(new PlaneRoadModel(MIN, MAX, distanceUnit,
          MAX_SPEED), allowDiversion);
    }
  }
}
