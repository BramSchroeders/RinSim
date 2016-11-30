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
package com.github.rinde.rinsim.core.model.pdp;

import javax.measure.Measure;
import javax.measure.quantity.Velocity;

import com.github.rinde.rinsim.geom.Point;

/**
 * Interface to be used for determining distance (both physical as well as in
 * travel time) between two points with a given speed.
 * @author Vincent Van Gestel
 */
public interface TravelTimes {

  /**
   * Computes the theoretical travel time between <code>from</code> and
   * <code>to</code> using the fastest available vehicle.
   * @param from The origin position.
   * @param to The destination position.
   * @param vehicleSpeed the maximum speed of the vehicle that will be using the
   *          result.
   * @return The expected travel time between the two positions based on a given
   *         maximum vehicle speed.
   */
  long getTheoreticalShortestTravelTime(Point from, Point to,
      Measure<Double, Velocity> vehicleSpeed);

  /**
   * Computes the current (based on a snapshot) travel time between
   * <code>from</code> and <code>to</code> using the fastest available vehicle.
   * @param from The origin position.
   * @param to The destination position.
   * @param vehicleSpeed the maximum speed of the vehicle that will be using the
   *          result.
   * @return The expected travel time between the two positions based on a given
   *         maximum vehicle speed.
   */
  long getCurrentShortestTravelTime(Point from, Point to,
      Measure<Double, Velocity> vehicleSpeed);

  /**
   * Computes the distance between two points, denoted as <code>from</code> and
   * <code>to</code> using the theoretically fastest possible route.
   * @param from The origin position
   * @param to The destination position.
   * @param vehicleSpeed the maximum speed of the vehicle that will be using the
   *          result.
   * @return The expected distance between two positions based on a given
   *         maximum vehicle speed.
   */
  double computeTheoreticalDistance(Point from, Point to,
      Measure<Double, Velocity> vehicleSpeed);

  /**
   * Computes the current distance between two points, denoted as
   * <code>from</code> and <code>to</code> using the fastest possible route.
   * @param from The origin position
   * @param to The destination position.
   * @param vehicleSpeed the maximum speed of the vehicle that will be using the
   *          result.
   * @return The expected distance between two positions based on a given
   *         maximum vehicle speed.
   */
  double computeCurrentDistance(Point from, Point to,
      Measure<Double, Velocity> vehicleSpeed);

}
