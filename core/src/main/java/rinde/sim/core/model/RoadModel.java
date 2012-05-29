/**
 * 
 */
package rinde.sim.core.model;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import rinde.sim.core.graph.EdgeData;
import rinde.sim.core.graph.Graph;
import rinde.sim.core.graph.Graphs;
import rinde.sim.core.graph.MultiAttributeEdgeData;
import rinde.sim.core.graph.Point;
import rinde.sim.util.SpeedConverter;
import rinde.sim.util.TimeUnit;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

/**
 * @author Rinde van Lon (rinde.vanlon@cs.kuleuven.be)
 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be> changes wrt.
 *         models infrastructure
 * 
 */
public class RoadModel implements Model<RoadUser> {
	// TODO remove the Graph related functions, and give a reference to an
	// unmodifiable Graph instance instead

	protected volatile Map<RoadUser, Location> objLocs;
	final Graph<? extends EdgeData> graph;

	public RoadModel(Graph<? extends EdgeData> pGraph) {
		checkArgument(pGraph != null, "Graph can not be null");
		graph = pGraph;
		objLocs = Collections.synchronizedMap(new LinkedHashMap<RoadUser, Location>());
	}

	public void addObjectAt(RoadUser newObj, Point pos) {
		checkArgument(graph.containsNode(pos), "Object must be initiated on a crossroad.");
		checkArgument(!objLocs.containsKey(newObj), "Object is already added");
		objLocs.put(newObj, new Location(pos, null, 0));
	}

	public void addObjectAtSamePosition(RoadUser newObj, RoadUser existingObj) {
		checkArgument(!objLocs.containsKey(newObj), "Object " + newObj + " is already added.");
		checkArgument(objLocs.containsKey(existingObj), "Object " + existingObj + " does not exist.");
		objLocs.put(newObj, objLocs.get(existingObj));
	}

	/**
	 * Removes all objects on this RoadStructure instance.
	 */
	public void clear() {
		objLocs.clear();
	}

	public boolean containsObject(RoadUser obj) {
		checkArgument(obj != null, "obj can not be null");
		return objLocs.containsKey(obj);
	}

	public boolean containsObjectAt(RoadUser obj, Point p) {
		checkArgument(p != null, "point can not be null");
		if (containsObject(obj)) {
			return objLocs.get(obj).getPosition().equals(p);
		}
		return false;
	}

	public boolean equalPosition(RoadUser obj1, RoadUser obj2) {
		return containsObject(obj1) && containsObject(obj2) && getPosition(obj1).equals(getPosition(obj2));
	}

	/**
	 * This method moves the specified {@link RoadUser} using the specified path
	 * and with the specified time. The road model is using the information
	 * about speed of the {@link RoadUser} and constrains on the graph to
	 * reposition the object.
	 * <p>
	 * This method can be called repeatedly to follow a path. Each time this
	 * method is invoked the <code>path</code> {@link Queue} can be modified.
	 * When a vertex in <code>path</code> has been visited, it is removed from
	 * the {@link Queue}.
	 * @param object The object in the physical world that is to be moved.
	 * @param path The path that is followed, it is modified by this method.
	 * @param time The time that has elapsed. The actual distance that the
	 *            {@link MovingRoadUser} has traveld is based on its speed an
	 *            the elapsed time.
	 * @return The actual distance that <code>object</code> has traveled after
	 *         the execution of this method has finished.
	 */
	// TODO write more tests checking for travel distance etc
	public PathProgress followPath(MovingRoadUser object, Queue<Point> path, long time) {
		checkArgument(object != null, "object cannot be null");
		checkArgument(objLocs.containsKey(object), "object must have a location");
		if (path == null) {// to avoid warnings about potential null pointers we
							// use the old fashioned way
			throw new IllegalArgumentException("path can not be null");
		}
		checkArgument(path.peek() != null, "path can not be empty");
		checkArgument(time > 0, "time must be a positive number");

		Location objLoc = objLocs.get(object);
		checkLocation(objLoc);

		long timeLeft = time;
		double traveled = 0;

		Location tempLoc = objLoc;
		Point tempPos = objLoc.getPosition();

		double newDis = Double.NaN;

		final SpeedConverter sc = new SpeedConverter();

		List<Point> travelledNodes = new ArrayList<Point>();
		while (timeLeft > 0 && path.size() > 0) {
			checkIsValidMove(tempLoc, path.peek());

			// speed in graph units per hour -> converting to miliseconds
			double speed = getMaxSpeed(object, tempPos, path.peek());
			speed = sc.from(speed, TimeUnit.H).to(TimeUnit.MS);

			// distance that can be traveled in current edge with timeleft
			double travelDistance = speed * timeLeft;
			double connLength = computeConnectionLength(tempPos, path.peek());

			if (travelDistance >= connLength) {
				// jump to next vertex
				tempPos = path.remove();
				travelledNodes.add(tempPos);
				long timeSpent = Math.round(connLength / speed);
				timeLeft -= timeSpent;
				traveled += connLength;

				if (tempPos instanceof MidPoint) {
					tempLoc = checkLocation(((MidPoint) tempPos).loc);
				} else {
					tempLoc = checkLocation(new Location(tempPos));
				}

			} else { // distanceLeft < connLength
				newDis = travelDistance;
				timeLeft = 0;
				long timeSpent = Math.round(travelDistance / speed);
				timeLeft -= timeSpent;
				traveled += travelDistance;

				// we have jumped OVER the next vertex and landed somewhere
				// after
				// if (nextVertex) {
				// if (path.peek() instanceof MidPoint) {
				// tempLoc = checkLocation(new Location(tempLoc.from,
				// ((MidPoint) path.peek()).loc.to, newDis));
				// } else {
				// tempLoc = checkLocation(new Location(tempLoc.from,
				// path.peek(), newDis));
				// }
				// }
				// // we have moved on the current edge
				// else {
				// // double relpos = tempLoc.isEdgePoint() ?
				// // tempLoc.relativePos + newDis : newDis;
				// tempLoc = checkLocation(new Location(tempLoc.from,
				// path.peek(), tempLoc.relativePos + newDis));
				// }

				// Point to = ;
				// if (nextVertex && path.peek() instanceof MidPoint) {
				// to = ((MidPoint) path.peek()).loc.to;
				// }
				//
				// double d = newDis;
				// if (!nextVertex) {
				// d = tempLoc.relativePos + newDis;
				// }

				tempLoc = checkLocation(new Location(tempLoc.from, path.peek(), tempLoc.relativePos + newDis));

			}
			tempPos = tempLoc.getPosition();
		}

		objLocs.put(object, tempLoc);
		return new PathProgress(traveled, time - (timeLeft > 0 ? timeLeft : 0), travelledNodes);
	}

	protected void checkIsValidMove(Location objLoc, Point nextHop) {
		// in case we start from an edge and our next destination is to go to
		// the end of the current edge then its ok. Otherwise more checks are
		// required..
		if (objLoc.isEdgePoint() && !nextHop.equals(objLoc.to)) {
			// check if next destination is a MidPoint
			checkArgument(nextHop instanceof MidPoint, "Illegal path for this object, from a position on an edge we can not jump to another edge or go back.");
			MidPoint dest = (MidPoint) nextHop;
			// check for same edge
			checkArgument(objLoc.isOnSameEdge(dest.loc), "Illegal path for this object, first point is not on the same edge as the object.");
			// check for relative position
			checkArgument(objLoc.relativePos <= dest.loc.relativePos, "Illegal path for this object, can not move backward over an edge.");
		}
		// in case we start from a node and we are not going to another node
		else if (!objLoc.isEdgePoint() && !nextHop.equals(objLoc.from) && !graph.hasConnection(objLoc.from, nextHop)) {
			checkArgument(nextHop instanceof MidPoint, "Illegal path, first point should be directly connected to object location.");
			MidPoint dest = (MidPoint) nextHop;
			checkArgument(graph.hasConnection(objLoc.from, dest.loc.to), "Illegal path, first point is on an edge not connected to object location. ");
			checkArgument(objLoc.from.equals(dest.loc.from), "Illegal path, first point is on a different edge.");
		}
	}

	/**
	 * Compute distance between two points. If points are equal the distance is
	 * 0. If both points are graph's nodes the method checks if there is a
	 * length of edge defined. If the from/to is on edge or the length of edge
	 * is not defined {@link Point#distance(Point, Point)} is used.
	 * @return the distance between two points
	 * @throws IllegalArgumentException when two points are part of the graph
	 *             but are not equal or there is no connection between them
	 */
	private double computeConnectionLength(Point from, Point to) {
		if (from == null) {
			throw new IllegalArgumentException("from can not be null");
		}
		checkArgument(to != null, "to can not be null");
		if (from.equals(to)) {
			return 0;
		}
		if (from instanceof MidPoint || to instanceof MidPoint) {
			return Point.distance(from, to);
		}

		if (!graph.hasConnection(from, to)) {
			throw new IllegalArgumentException("followPath() attempts to use non-existing connection: " + from + " >> "
					+ to + ".");
		}

		EdgeData data = graph.connectionData(from, to);
		assert data == null || !Double.isNaN(data.getLength()) : "edge length cannot be NaN";
		return data == null ? Point.distance(from, to) : data.getLength();
	}

	/**
	 * Compute speed of the object taking into account the speed limits of the
	 * object
	 * @param object traveling object
	 * @param from the point on the graph object is located
	 * @param to the next point on the path it want to reach
	 */
	protected double getMaxSpeed(MovingRoadUser object, Point from, Point to) {
		if (object == null) {
			throw new IllegalArgumentException("object can not be null");
		}
		if (from == null) {
			throw new IllegalArgumentException("to can not be null");
		}
		checkArgument(to != null, "to can not be null");

		assert object != null && from != null && to != null;
		if (from.equals(to)) {
			return object.getSpeed();
		}

		Point start = from instanceof MidPoint ? ((MidPoint) from).loc.from : from;
		Point stop = to instanceof MidPoint ? ((MidPoint) to).loc.to : to;
		checkArgument(graph.hasConnection(start, stop), "points not connected " + from + " >> " + to);
		EdgeData data = graph.connectionData(start, stop);
		if (data instanceof MultiAttributeEdgeData) {
			MultiAttributeEdgeData maed = (MultiAttributeEdgeData) data;
			double speed = maed.getMaxSpeed();
			return Double.isNaN(speed) ? object.getSpeed() : Math.min(speed, object.getSpeed());
		}
		return object.getSpeed();
	}

	/**
	 * @return An unmodifiable view on the graph.
	 */
	public Graph<? extends EdgeData> getGraph() {
		return Graphs.unmodifiableGraph(graph);
	}

	/**
	 * This method returns a collection of {@link Point} objects which are the
	 * positions of the objects that exist in this model. The returned
	 * collection is not a live view on the set, but a new created copy.
	 * @return The collection of {@link Point} objects.
	 */
	public Collection<Point> getObjectPositions() {
		return getObjectsAndPositions().values();
	}

	/**
	 * This method returns the set of {@link RoadUser} objects which exist in
	 * this model. The returned set is not a live view on the set, but a new
	 * created copy.
	 * @return The set of {@link RoadUser} objects.
	 */
	public Set<RoadUser> getObjects() {
		synchronized (objLocs) {
			Set<RoadUser> copy = new LinkedHashSet<RoadUser>();
			copy.addAll(objLocs.keySet());
			return copy;
		}
	}

	/**
	 * This method returns a set of {@link RoadUser} objects which exist in this
	 * model and satisfy the given {@link Predicate}. The returned set is not a
	 * live view on this model, but a new created copy.
	 * @param predicate The predicate that decides which objects to return.
	 * @return A set of {@link RoadUser} objects.
	 */
	public Set<RoadUser> getObjects(Predicate<RoadUser> predicate) {
		return Sets.filter(getObjects(), predicate);
	}

	/**
	 * Returns all objects of the given type located in the same position as the
	 * given {@link RoadUser}.
	 * @param roadUser The object which location is checked for other objects.
	 * @param type The type of the objects to be returned.
	 * @return A set of objects of type <code>type</code>.
	 */
	@SuppressWarnings("unchecked")
	public <Y extends RoadUser> Set<Y> getObjectsAt(RoadUser roadUser, Class<Y> type) {
		Set<Y> result = new HashSet<Y>();
		for (RoadUser ru : getObjects(new SameLocationPredicate(roadUser, type, this))) {
			result.add((Y) ru);
		}
		return result;
	}

	private static class SameLocationPredicate implements Predicate<RoadUser> {
		private final RoadUser reference;
		private final RoadModel model;
		private final Class<?> type;

		public SameLocationPredicate(final RoadUser pReference, final Class<?> pType, final RoadModel pModel) {
			reference = pReference;
			type = pType;
			model = pModel;
		}

		@Override
		public boolean apply(RoadUser input) {
			return type.isInstance(input) && model.equalPosition(input, reference);
		}
	}

	/**
	 * This method returns a mapping of {@link RoadUser} to {@link Point}
	 * objects which exist in this model. The returned map is not a live view on
	 * this model, but a new created copy.
	 * @return A map of {@link RoadUser} to {@link Point} objects.
	 */
	public Map<RoadUser, Point> getObjectsAndPositions() {
		Map<RoadUser, Location> copiedMap;
		synchronized (objLocs) {
			copiedMap = new LinkedHashMap<RoadUser, Location>();
			copiedMap.putAll(objLocs);
		}// it is save to release the lock now

		Map<RoadUser, Point> theMap = new LinkedHashMap<RoadUser, Point>();
		for (java.util.Map.Entry<RoadUser, Location> entry : copiedMap.entrySet()) {
			theMap.put(entry.getKey(), entry.getValue().getPosition());
		}
		return theMap;
	}

	/**
	 * This method returns a set of {@link RoadUser} objects which exist in this
	 * model and are instances of the specified {@link Class}. The returned set
	 * is not a live view on the set, but a new created copy.
	 * @param type The type of returned objects.
	 * @return A set of {@link RoadUser} objects.
	 */
	@SuppressWarnings("unchecked")
	public <Y extends RoadUser> Set<Y> getObjectsOfType(final Class<Y> type) {
		if (type == null) {
			throw new IllegalArgumentException("type can not be null");
		}
		return (Set<Y>) getObjects(new Predicate<RoadUser>() {
			@Override
			public boolean apply(RoadUser input) {
				return type.isInstance(input);
			}
		});
	}

	/**
	 * Method to retrieve the location of an object.
	 * @param roadUser The object for which the position is examined.
	 * @return The position (as a {@link Point} object) for the specified
	 *         <code>obj</code> object.
	 */
	public Point getPosition(RoadUser roadUser) {
		checkArgument(roadUser != null, "object can not be null");
		checkArgument(containsObject(roadUser), "RoadUser does not exist");
		return objLocs.get(roadUser).getPosition();
	}

	public Point getLastCrossRoad(RoadUser roadUser) {
		checkArgument(roadUser != null, "RoadUser can not be null");
		checkArgument(containsObject(roadUser), "RoadUser does not exist");
		return objLocs.get(roadUser).from;
	}

	/**
	 * Convenience method for {@link #getShortestPathTo(Point, Point)}
	 * @param fromObj The object which is used as the path origin
	 * @param toObj The object which is used as the path destination
	 * @return The shortest path from 'fromObj' to 'toObj'.
	 */
	public List<Point> getShortestPathTo(RoadUser fromObj, RoadUser toObj) {
		checkArgument(fromObj != null, "fromObj can not be null");
		checkArgument(objLocs.containsKey(toObj), " to object should be in RoadModel. " + toObj);
		// Location l = objLocs.get(toObj);
		List<Point> path = getShortestPathTo(fromObj, getPosition(toObj));
		// if (l.isEdgePoint()) {
		// path.add(l.getPosition());
		// }
		return path;
	}

	/**
	 * Convenience method for {@link #getShortestPathTo(Point, Point)}
	 * @param fromObj The object which is used as the path origin
	 * @param to The path destination
	 * @return The shortest path from 'fromObj' to 'to'
	 */
	public List<Point> getShortestPathTo(RoadUser fromObj, Point to) {
		checkArgument(fromObj != null, "fromObj can not be null");
		checkArgument(objLocs.containsKey(fromObj), " from object should be in RoadModel. " + fromObj);
		Point from = getNode(fromObj);
		return getShortestPathTo(from, to);
	}

	public List<Point> getShortestPathTo(Point from, Point to) {
		checkArgument(from != null, "from can not be null");
		checkArgument(to != null, "to can not be null");
		List<Point> path = new ArrayList<Point>();
		Point f = from;
		if (from instanceof MidPoint) {
			f = ((MidPoint) from).loc.to;
			path.add(from);
		}

		Point t = to;
		if (to instanceof MidPoint) {
			t = ((MidPoint) to).loc.from;
		}
		path.addAll(doGetShortestPathTo(f, t));
		if (to instanceof MidPoint) {
			path.add(to);
		}
		return path;
	}

	protected List<Point> doGetShortestPathTo(Point from, Point to) {
		return Graphs.shortestPathEuclidianDistance(graph, from, to);
	}

	/**
	 * Use {@link Graph#hasConnection(Point, Point)} instead.
	 * @param from Start position of connection.
	 * @param to End position of connection.
	 * @return True if the connection exists, false otherwise.
	 */
	// TODO remove
	@Deprecated
	public boolean hasConnection(Point from, Point to) {
		return graph.hasConnection(from, to);
	}

	/**
	 * Use {@link Graph#addConnection(Point, Point)} instead.
	 * @param from Start point of connection
	 * @param to End point of connection
	 */
	// TODO remove
	@Deprecated
	public void addConnection(Point from, Point to) {
		checkArgument(!graph.hasConnection(from, to), "Connection already exists.");
		graph.addConnection(from, to);
		assert graph.containsNode(from);
	}

	/**
	 * Removes the specified {@link RoadUser} from this model.
	 * @param roadUser the object to be removed.
	 */
	public void removeObject(RoadUser roadUser) {
		checkArgument(roadUser != null, "RoadUser can not be null");
		checkArgument(objLocs.containsKey(roadUser), "RoadUser does not exist.");
		objLocs.remove(roadUser);
	}

	protected Location checkLocation(Location l) {
		checkArgument(l.isEdgePoint() || graph.containsNode(l.from), "Location points to non-existing vertex: "
				+ l.from + ".");
		checkArgument(!l.isEdgePoint() || graph.hasConnection(l.from, l.to), "Location points to non-existing connection: "
				+ l.from + " >> " + l.to + ".");
		return l;
	}

	protected Point getNode(RoadUser obj) {
		checkArgument(obj != null, "object can not be null");
		checkArgument(objLocs.containsKey(obj), "object must exist");
		if (objLocs.get(obj).to != null) {
			return objLocs.get(obj).to;
		} else {
			return objLocs.get(obj).from;
		}
	}

	@Override
	public boolean register(RoadUser element) {
		if (element == null) {
			throw new IllegalArgumentException("element can not be null");
		}
		element.initRoadUser(this);
		return true;
	}

	@Override
	public Class<RoadUser> getSupportedType() {
		return RoadUser.class;
	}

	@Override
	public boolean unregister(RoadUser roadUser) {
		checkArgument(roadUser != null, "RoadUser can not be null");
		if (containsObject(roadUser)) {
			removeObject(roadUser);
			return true;
		}
		return false;
	}

	/**
	 * Represents the distance traveled and time spend in
	 * {@link RoadModel#followPath(MovingRoadUser, Queue, long)}
	 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be>
	 * @since 2.0
	 */
	public static final class PathProgress {
		/**
		 * distance traveled in the
		 * {@link RoadModel#followPath(MovingRoadUser, Queue, long)}
		 */
		public final double distance;
		/**
		 * time spend on traveling the distance
		 */
		public final long time;

		public final List<Point> travelledNodes;

		public PathProgress(double dist, long pTime, List<Point> pTravelledNodes) {
			checkArgument(dist >= 0, "distance must be greater than or equal to 0");
			checkArgument(pTime >= 0, "time must be greather than or equal to 0");
			distance = dist;
			time = pTime;
			travelledNodes = pTravelledNodes;
		}
	}

	// internal usage only
	/**
	 * Object that is on the graph node has to parameter == <code>null</code>.
	 * 
	 */
	class Location {
		private static final double DELTA = 0.000001;
		final Point from;
		final Point to;
		final double relativePos;
		final double roadLength;

		public Location(Point pFrom) {
			this(pFrom, null, 0);
		}

		public Location(Point pFrom, Point pTo, double pRelativePos) {
			from = pFrom;

			if (pTo instanceof MidPoint) {
				to = ((MidPoint) pTo).loc.to;
			} else {
				to = pTo;
			}
			if (isEdgePoint()) {
				relativePos = pRelativePos;
				EdgeData data = graph.connectionData(pFrom, to);
				roadLength = data == null || Double.isNaN(data.getLength()) ? Point.distance(pFrom, to) : data
						.getLength();
			} else {
				roadLength = -1;
				relativePos = 0;
			}
		}

		public boolean isOnSameEdge(Location l) {
			if (!isEdgePoint()) {
				return false;
			}
			return from.equals(l.from) && to.equals(l.to);
		}

		public boolean isEdgePoint() {
			return to != null;
		}

		@Override
		public String toString() {
			return "from:" + from + ", to:" + to + ", relativepos:" + relativePos;
		}

		Point getPosition() {
			if (!isEdgePoint()) {
				return from;
			}
			Point diff = Point.diff(to, from);
			double perc = relativePos / roadLength;
			if (perc + DELTA >= 1) {
				return to;
			}
			return new MidPoint(from.x + perc * diff.x, from.y + perc * diff.y, this);
		}
	}

	final class MidPoint extends Point {
		private static final long serialVersionUID = -8442184033570204979L;
		protected final Location loc;

		public MidPoint(double pX, double pY, Location l) {
			super(pX, pY);
			loc = l;
		}

		@Override
		public String toString() {
			return super.toString() + "{" + loc + "}";
		}
	}

}
