/**
 * 
 */
package rinde.sim.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

/**
 * @author Rinde van Lon (rinde.vanlon@cs.kuleuven.be)
 * 
 */
public class RoadStructure {

	// mapping of object with their respective locations
	private volatile Map<Object, Location> objLocs;

	// contains vertices with outgoing connections
	private final HashMultimap<Point, Point> struc;

	public RoadStructure() {
		struc = HashMultimap.create();
		objLocs = Collections.synchronizedMap(new HashMap<Object, Location>());
	}

	public void addObjectAt(Object newObj, Point pos) {
		if (!struc.containsKey(pos)) {
			throw new IllegalArgumentException("Object must be initiated on a crossroad.");
		} else if (objLocs.containsKey(newObj)) {
			throw new IllegalArgumentException("Object is already added.");
		}
		objLocs.put(newObj, new Location(pos, null, 0));
	}

	public void addObjectAtSamePosition(Object newObj, Object existingObj) {
		if (objLocs.containsKey(newObj)) {
			throw new IllegalArgumentException("Object " + newObj + " is already added.");
		} else if (!objLocs.containsKey(existingObj)) {
			throw new IllegalArgumentException("Object " + existingObj + " does not exist.");
		}
		objLocs.put(newObj, objLocs.get(existingObj));
	}

	public List<Point> getObjectPositions() {
		List<Point> positions = new ArrayList<Point>();
		for (Location l : objLocs.values()) {
			positions.add(l.getPosition());
		}
		return positions;
	}

	public void removeObject(Object o) {
		objLocs.remove(o);
	}

	/**
	 * @return A synchronized and unmodifiable set of the objects in the road
	 *         structure.
	 */
	public Set<Object> getObjects() {
		synchronized (objLocs) {
			return Collections.unmodifiableSet(objLocs.keySet());
		}
	}

	@SuppressWarnings("unchecked")
	public <T> Set<T> getObjectsOfType(final Class<T> type) {
		return (Set<T>) getObjects(new Predicate<Object>() {
			@Override
			public boolean apply(Object input) {
				return input.getClass().equals(type);
			}
		});
	}

	public Set<Object> getObjects(Predicate<Object> p) {
		synchronized (objLocs) {
			return Collections.unmodifiableSet(Sets.filter(objLocs.keySet(), p));
		}
	}

	public Map<Object, Point> getObjectsAndPositions() {
		synchronized (objLocs) {
			Map<Object, Point> map = new HashMap<Object, Point>();
			for (Entry<Object, Location> entry : objLocs.entrySet()) {
				map.put(entry.getKey(), entry.getValue().getPosition());
			}
			return map;
		}
	}

	/**
	 * This method can be called repeatedly to follow a path. Each time this
	 * method is invoked the <code>path</code> {@link Queue} can be modified.
	 * When a vertex in <code>path</code> has been visited, it is removed from
	 * the {@link Queue}.
	 * @param object The object in the physical world that is to be moved.
	 * @param path The path that is followed, it is modified by this method.
	 * @param distance The distance that is attempted to be traveled over the
	 *            <code>path</code>.
	 * @return The actual distance that <code>object</code> has traveled after
	 *         the execution of this method has finished.
	 */
	public double followPath(Object object, Queue<Point> path, double distance) {
		assert path != null : "path cannot be null";
		assert path.peek() != null : "path cannot be empty";
		assert distance > 0 : "distance must be greater than 0";
		assert object != null : "object cannot be null";
		assert objLocs.containsKey(object) : "object must have a location";

		Location objLoc = objLocs.get(object);
		checkLocation(objLoc);

		if (objLoc.to != null && !path.peek().equals(objLoc.to) && (path.peek() instanceof MidPoint ? !((MidPoint) path.peek()).loc.to.equals(objLoc.to) : true)) {
			throw new IllegalArgumentException("Illegal path for this object, first point should be in current direction. " + path);
		} else if (objLoc.to == null && !path.peek().equals(objLoc.from) && !hasConnection(objLoc.from, path.peek())
				&& (path.peek() instanceof MidPoint ? !hasConnection(objLoc.from, ((MidPoint) path.peek()).loc.to) : true)) {
			throw new IllegalArgumentException("Illegal path for this object, first point should be current point.");
		}
		double travelDistance = distance;

		Point tempPos = objLoc.getPosition();
		double newDis = -1;
		boolean nextVertex = false;
		while (travelDistance > 0 && path.size() >= 1) {
			double dist = Point.distance(tempPos, path.peek());

			if (dist > 0 && struc.containsKey(tempPos) && !struc.containsEntry(tempPos, path.peek()) && !(path.peek() instanceof MidPoint)) {
				throw new IllegalStateException("followPath() attempts to use non-existing connection: " + tempPos + " >> " + path.peek() + ".");
			}

			if (travelDistance >= dist) {
				tempPos = path.remove();
				travelDistance -= dist;
				nextVertex = true;
			} else { // distanceLeft < dist
				newDis = travelDistance;
				travelDistance = 0;
			}
		}

		if (newDis == -1) {
			if (tempPos instanceof MidPoint) {
				objLocs.put(object, checkLocation(((MidPoint) tempPos).loc));
			} else {
				objLocs.put(object, checkLocation(new Location(tempPos)));
			}
		} else if (nextVertex) {
			if (path.peek() instanceof MidPoint) {
				objLocs.put(object, checkLocation(new Location(tempPos, ((MidPoint) path.peek()).loc.to, newDis)));
			} else {
				objLocs.put(object, checkLocation(new Location(tempPos, path.peek(), newDis)));
			}
		} else {
			Point t = objLoc.to;
			double relpos = objLoc.relativePos + newDis;
			if (t == null) {
				t = path.peek();
				relpos = newDis;
			}
			objLocs.put(object, checkLocation(new Location(objLoc.from, t, relpos)));
		}
		return distance - travelDistance;
	}

	/**
	 * Method to retrieve the location of an object.
	 * @param obj The object for which the position is examined.
	 * @return The position (as a {@link Point} object) for the specified
	 *         <code>obj</code> object.
	 */
	public Point getPosition(Object obj) {
		assert obj != null : "object cannot be null";
		assert objLocs.containsKey(obj) : "object must have a location in RoadStructure";
		return objLocs.get(obj).getPosition();
	}

	public List<Point> getShortestPathTo(Object obj, Point dest) {
		assert objLocs.containsKey(obj);
		Point n = getNode(obj);
		return PathFinder.shortestDistance(struc, n, dest);
	}

	public List<Point> getShortestPathTo(Object from, Object to) {
		assert objLocs.containsKey(to);
		Location l = objLocs.get(to);
		List<Point> path = getShortestPathTo(from, l.from);
		if (l.to != null) {
			path.add(new MidPoint(l));
		}
		return path;
	}

	protected Point getNode(Object obj) {
		assert obj != null;
		assert objLocs.containsKey(obj);

		if (objLocs.get(obj).to != null) {
			return objLocs.get(obj).to;
		} else {
			return objLocs.get(obj).from;
		}
	}

	public void addConnection(Point from, Point to) {
		if (struc.containsEntry(from, to)) {
			throw new IllegalArgumentException("Connection already exists.");
		}
		struc.put(from, to);
		assert struc.containsKey(from);
	}

	public int getNumberOfConnections() {
		return struc.size();
	}

	public int getNumberOfNodes() {
		return struc.keySet().size();
	}

	public boolean hasConnection(Point from, Point to) {
		return struc.containsEntry(from, to);
	}

	public List<Point> getNodes() {
		return Collections.unmodifiableList(new ArrayList<Point>(struc.keySet()));
	}

	/**
	 * @return An unmodifiable view on the graph.
	 */
	public Multimap<Point, Point> getGraph() {
		return Multimaps.unmodifiableMultimap(struc);
	}

	protected Location checkLocation(Location l) {
		if (l.to == null && !struc.containsKey(l.from)) {
			throw new IllegalStateException("Location points to non-existing vertex: " + l.from + ".");
		} else if (l.to != null && !struc.containsEntry(l.from, l.to)) {
			throw new IllegalStateException("Location points to non-existing connection: " + l.from + " >> " + l.to + ".");
		}
		return l;
	}

	private class MidPoint extends Point {
		final Location loc;

		public MidPoint(Location l) {
			super(l.getPosition().x, l.getPosition().y);
			loc = l;
		}
	}

	// internal usage only
	private class Location {
		final Point from;
		final Point to;
		final double relativePos;
		final double roadLength;

		public Location(Point from) {
			this(from, null, -1);
		}

		public Location(Point from, Point to, double relativePos) {
			this.from = from;
			this.to = to;
			if (to != null) {
				this.relativePos = relativePos;
				roadLength = Point.distance(from, to);
			} else {
				roadLength = -1;
				this.relativePos = -1;
			}
		}

		Point getPosition() {
			if (to == null) {
				return from;
			}
			Point diff = Point.diff(to, from);
			double perc = relativePos / roadLength;
			return new Point(from.x + perc * diff.x, from.y + perc * diff.y);
		}

		@Override
		public String toString() {
			return "from:" + from + ", to:" + to + ", relativepos:" + relativePos;
		}
	}

	public boolean containsObject(Object obj) {
		return objLocs.containsKey(obj);
	}

	public boolean containsObjectAt(Object obj, Point p) {
		if (containsObject(obj)) {
			return objLocs.get(obj).getPosition().equals(p);
		}
		return false;
	}

	// also checks whether both objects have a valid position
	public boolean equalPosition(Object obj1, Object obj2) {
		return containsObject(obj1) && containsObject(obj2) && getPosition(obj1).equals(getPosition(obj2));
	}

	/**
	 * Merges the specified graph into this graph.
	 * @param graph The specified graph.
	 */
	public void addGraph(Multimap<Point, Point> graph) {
		struc.putAll(graph);
	}
}
