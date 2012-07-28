/**
 * 
 */
package rinde.sim.problem.fabrirecht;

import java.util.Collection;
import java.util.Set;

import rinde.sim.core.graph.Point;
import rinde.sim.scenario.Scenario;
import rinde.sim.scenario.TimedEvent;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class FabriRechtScenario extends Scenario {
	public final Point min;
	public final Point max;
	public final TimeWindow timeWindow;

	/**
	 * @param pEvents
	 * @param pSupportedTypes
	 */
	public FabriRechtScenario(Collection<? extends TimedEvent> pEvents, Set<Enum<?>> pSupportedTypes, Point pMin,
			Point pMax, TimeWindow pTimeWindow) {
		super(pEvents, pSupportedTypes);
		min = pMin;
		max = pMax;
		timeWindow = pTimeWindow;
	}

}
