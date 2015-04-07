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

import javax.annotation.CheckReturnValue;
import javax.measure.quantity.Duration;
import javax.measure.unit.Unit;

/**
 * @author Rinde van Lon
 *
 */
public interface Clock {

  void start();

  void stop();

  void tick();

  @CheckReturnValue
  boolean isTicking();

  @CheckReturnValue
  Unit<Duration> getTimeUnit();

  @CheckReturnValue
  long getCurrentTime();

  @CheckReturnValue
  long getTimeStep();

}
