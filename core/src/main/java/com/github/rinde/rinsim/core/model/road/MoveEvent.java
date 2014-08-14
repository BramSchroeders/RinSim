/**
 * 
 */
package com.github.rinde.rinsim.core.model.road;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.github.rinde.rinsim.core.model.road.AbstractRoadModel.RoadEventType;
import com.github.rinde.rinsim.event.Event;

/**
 * Event representing a move of a {@link MovingRoadUser}.
 * @author Rinde van Lon 
 */
public class MoveEvent extends Event {

  /**
   * The {@link RoadModel} that dispatched this event.
   */
  public final RoadModel roadModel;

  /**
   * The {@link MovingRoadUser} that moved.
   */
  public final MovingRoadUser roadUser;

  /**
   * Object containing the distance, time and path of this move.
   */
  public final MoveProgress pathProgress;

  MoveEvent(RoadModel rm, MovingRoadUser ru, MoveProgress pp) {
    super(RoadEventType.MOVE, rm);
    roadModel = rm;
    roadUser = ru;
    pathProgress = pp;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }
}
