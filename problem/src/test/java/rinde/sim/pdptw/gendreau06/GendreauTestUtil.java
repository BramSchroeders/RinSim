/**
 * 
 */
package rinde.sim.pdptw.gendreau06;

import static com.google.common.collect.Lists.newArrayList;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import rinde.sim.scenario.TimedEvent;
import rinde.sim.scenario.TimedEvent.TimeComparator;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class GendreauTestUtil {

  public static Gendreau06Scenario create(
      List<? extends TimedEvent> events, Set<Enum<?>> eventTypes,
      long ts) {
    Collections.sort(events, TimeComparator.INSTANCE);
    return new Gendreau06Scenario(events, eventTypes, ts,
        GendreauProblemClass.SHORT_LOW_FREQ, 1, false);
  }

  public static Gendreau06Scenario create(Collection<TimedEvent> parcels) {
    return create(parcels, 1);
  }

  public static Gendreau06Scenario create(Collection<TimedEvent> parcels,
      int numTrucks) {

    final Gendreau06Scenario gs = Gendreau06Parser
        .parser()
        .addFile(new ByteArrayInputStream("".getBytes()), "req_rapide_1_240_24")
        .setNumVehicles(numTrucks)
        .parse().get(0);

    final List<TimedEvent> events = newArrayList();
    events.addAll(gs.asList());
    events.addAll(parcels);
    return create(events, gs.getPossibleEventTypes(), 1000);
  }

}
