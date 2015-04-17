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
package com.github.rinde.rinsim.core.model.road;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.min;
import static java.util.Arrays.asList;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

import javax.annotation.Nullable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Length;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.collect.ImmutableList;
import com.google.common.math.DoubleMath;

/**
 * A {@link RoadModel} that uses a plane as road structure. This assumes that
 * from every point in the plane it is possible to drive to every other point in
 * the plane. The plane has a boundary as defined by a rectangle. Instances can
 * be obtained via {@link #builder()}.
 *
 * @author Rinde van Lon
 */
public class PlaneRoadModel extends AbstractRoadModel<Point> {

  /**
   * The minimum travelable distance.
   */
  protected static final double DELTA = 0.000001;

  /**
   * The minimum x and y of the plane.
   */
  public final Point min;
  /**
   * The maximum x and y of the plane.
   */
  public final Point max;
  /**
   * The width of the plane.
   */
  public final double width;
  /**
   * The height of the plane.
   */
  public final double height;
  /**
   * The maximum speed in meters per second that objects can travel on the
   * plane.
   */
  public final double maxSpeed;

  PlaneRoadModel(Builder b) {
    super(b.getDistanceUnit(), b.getSpeedUnit());
    min = b.min;
    max = b.max;
    width = max.x - min.x;
    height = max.y - min.y;
    maxSpeed = unitConversion.toInSpeed(b.maxSpeed);
  }

  @Override
  public Point getRandomPosition(RandomGenerator rnd) {
    return new Point(min.x + rnd.nextDouble() * width, min.y
      + rnd.nextDouble() * height);
  }

  @Override
  public void addObjectAt(RoadUser obj, Point pos) {
    checkArgument(
      isPointInBoundary(pos),
      "objects can only be added within the boundaries of the plane, %s is not in the boundary.",
      pos);
    super.addObjectAt(obj, pos);
  }

  @Override
  protected MoveProgress doFollowPath(MovingRoadUser object, Queue<Point> path,
    TimeLapse time) {
    final long startTimeConsumed = time.getTimeConsumed();
    Point loc = objLocs.get(object);

    double traveled = 0;
    final double speed = min(unitConversion.toInSpeed(object.getSpeed()),
      maxSpeed);
    if (speed == 0d) {
      // FIXME add test for this case, also check GraphRoadModel
      final Measure<Double, Length> dist = Measure.valueOf(0d,
        getDistanceUnit());
      final Measure<Long, Duration> dur = Measure.valueOf(0L,
        time.getTimeUnit());
      return MoveProgress.create(dist, dur, new ArrayList<Point>());
    }

    final List<Point> travelledNodes = new ArrayList<>();
    while (time.hasTimeLeft() && !path.isEmpty()) {
      checkArgument(isPointInBoundary(path.peek()),
        "points in the path must be within the predefined boundary of the plane");

      // distance in internal time unit that can be traveled with timeleft
      final double travelDistance = speed
        * unitConversion.toInTime(time.getTimeLeft(),
          time.getTimeUnit());
      final double stepLength = unitConversion.toInDist(Point
        .distance(loc, path.peek()));

      if (travelDistance >= stepLength) {
        loc = path.remove();
        travelledNodes.add(loc);

        final long timeSpent = DoubleMath.roundToLong(
          unitConversion.toExTime(stepLength / speed,
            time.getTimeUnit()),
          RoundingMode.HALF_DOWN);
        time.consume(timeSpent);
        traveled += stepLength;
      } else {
        final Point diff = Point.diff(path.peek(), loc);

        if (stepLength - travelDistance < DELTA) {
          loc = path.peek();
          traveled += stepLength;
        } else {
          final double perc = travelDistance / stepLength;
          loc = new Point(loc.x + perc * diff.x, loc.y + perc * diff.y);
          traveled += travelDistance;
        }
        time.consumeAll();

      }
    }
    objLocs.put(object, loc);

    // convert to external units
    final Measure<Double, Length> distTraveled = unitConversion
      .toExDistMeasure(traveled);
    final Measure<Long, Duration> timeConsumed = Measure.valueOf(
      time.getTimeConsumed() - startTimeConsumed, time.getTimeUnit());
    return MoveProgress.create(distTraveled, timeConsumed, travelledNodes);
  }

  @Override
  public List<Point> getShortestPathTo(Point from, Point to) {
    checkArgument(
      isPointInBoundary(from),
      "from must be within the predefined boundary of the plane, from is %s, boundary: min %s, max %s.",
      to, min, max);
    checkArgument(
      isPointInBoundary(to),
      "to must be within the predefined boundary of the plane, to is %s, boundary: min %s, max %s.",
      to, min, max);
    return asList(from, to);
  }

  @Override
  protected Point locObj2point(Point locObj) {
    return locObj;
  }

  @Override
  protected Point point2LocObj(Point point) {
    return point;
  }

  /**
   * Checks whether the specified point is within the plane as defined by this
   * model.
   * @param p The point to check.
   * @return <code>true</code> if the points is within the boundary,
   *         <code>false</code> otherwise.
   */
  // TODO give more general name?
  protected boolean isPointInBoundary(Point p) {
    return p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y;
  }

  @Override
  public ImmutableList<Point> getBounds() {
    return ImmutableList.of(min, max);
  }

  @Override
  public <U> U get(Class<U> type) {
    return type.cast(this);
  }

  /**
   * @return A new {@link Builder} for creating a {@link PlaneRoadModel}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder for {@link PlaneRoadModel}. Instances can be obtained via
   * {@link PlaneRoadModel#builder()}.
   * @author Rinde van Lon
   */
  public static class Builder extends
    AbstractRoadModelBuilder<PlaneRoadModel, Builder> {
    static final double DEFAULT_MAX_SPEED = 50d;
    static final Point DEFAULT_MIN_POINT = new Point(0, 0);
    static final Point DEFAULT_MAX_POINT = new Point(10, 10);

    Point min;
    Point max;
    double maxSpeed;

    Builder() {
      setProvidingTypes(RoadModel.class, PlaneRoadModel.class);
      min = DEFAULT_MIN_POINT;
      max = DEFAULT_MAX_POINT;
      maxSpeed = DEFAULT_MAX_SPEED;
    }

    /**
     * Sets the min point that defines the left top corner of the plane. The
     * default is <code>(0,0)</code>.
     * @param minPoint The min point to set.
     * @return This, as per the builder pattern.
     */
    public Builder setMinPoint(Point minPoint) {
      min = minPoint;
      return self();
    }

    /**
     * Sets the max point that defines the right bottom corner of the plane. The
     * default is <code>(10,10)</code>.
     * @param maxPoint The max point to set.
     * @return This, as per the builder pattern.
     */
    public Builder setMaxPoint(Point maxPoint) {
      max = maxPoint;
      return self();
    }

    /**
     * Sets the maximum speed to use for all vehicles in the model. The default
     * is <code>50</code>.
     * @param speed The max speed to set.
     * @return This, as per the builder pattern.
     */
    public Builder setMaxSpeed(double speed) {
      checkArgument(speed > 0d,
        "Max speed must be strictly positive but is %s.",
        speed);
      maxSpeed = speed;
      return self();
    }

    @Override
    public PlaneRoadModel build(DependencyProvider dependencyProvider) {
      checkArgument(
        min.x < max.x && min.y < max.y,
        "Min should have coordinates smaller than max, found min %s and max %s.",
        min, max);
      return new PlaneRoadModel(this);
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      final Builder o = (Builder) other;
      return Objects.equals(min, o.min)
        && Objects.equals(max, o.max)
        && Objects.equals(getDistanceUnit(), o.getDistanceUnit())
        && Objects.equals(getSpeedUnit(), o.getSpeedUnit())
        && Objects.equals(maxSpeed, o.maxSpeed)
        && AbstractModelBuilder.equal(this, o);
    }

    @Override
    public int hashCode() {
      return Objects
        .hash(min, max, getDistanceUnit(), getSpeedUnit(), maxSpeed);
    }

    @Override
    protected Builder self() {
      return this;
    }
  }
}
