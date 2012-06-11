package rinde.sim.examples.rwalk3;

import java.util.LinkedList;
import java.util.Queue;

import org.apache.commons.math.random.RandomGenerator;

import rinde.sim.core.SimulatorAPI;
import rinde.sim.core.SimulatorUser;
import rinde.sim.core.TickListener;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.road.MovingRoadUser;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.core.model.road.RoadUser;
import rinde.sim.example.rwalk.common.Package;

/**
 * Example of the simple random agent with the use of simulation facilities.
 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be>
 */
public class RandomWalkAgent implements TickListener, MovingRoadUser, SimulatorUser {

	protected RoadModel rs;
	protected RoadUser currentPackage;
	protected Queue<Point> path;
	protected RandomGenerator rnd;
	private SimulatorAPI simulator;
	private final double speed;

	/**
	 * Create simple agent.
	 * @param speed default speed of object in graph units per millisecond
	 */
	public RandomWalkAgent(double speed) {
		this.speed = speed;
	}

	@Override
	public void tick(long currentTime, long timeStep) {
		if (path == null || path.isEmpty()) {
			if (currentPackage != null && rs.containsObject(currentPackage)) {
				rs.removeObject(currentPackage);
			}

			Point destination = rs.getRandomPosition(rnd);
			currentPackage = new Package("dummy package", destination);
			simulator.register(currentPackage);
			path = new LinkedList<Point>(rs.getShortestPathTo(this, destination));
		} else {
			rs.followPath(this, path, timeStep);
		}

	}

	@Override
	public void initRoadUser(RoadModel model) {
		rs = model;
		Point pos = rs.getRandomPosition(rnd);
		rs.addObjectAt(this, pos);
	}

	@Override
	public void setSimulator(SimulatorAPI api) {
		simulator = api;
		rnd = api.getRandomGenerator();
	}

	@Override
	public double getSpeed() {
		return speed;
	}

	@Override
	public void afterTick(long currentTime, long timeStep) {
		// empty on purpose
	}
}
