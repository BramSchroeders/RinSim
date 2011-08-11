/**
 * 
 */
package rinde.sim.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rinde.sim.core.graph.Graph;
import rinde.sim.core.graph.Point;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

/**
 * @author Rinde van Lon (rinde.vanlon@cs.kuleuven.be)
 * 
 */
public class CachedRoadModel extends RoadModel {

	private Table<Point, Point, List<Point>> pathTable;

	private final Multimap<Class<?>, RoadUser> classObjectMap;

	public CachedRoadModel(Graph graph) {
		super(graph);

		pathTable = HashBasedTable.create();
		classObjectMap = HashMultimap.create();
	}

	public void setPathCache(Table<Point, Point, List<Point>> pathTable) {
		this.pathTable = pathTable;
	}

	public Table<Point, Point, List<Point>> getPathCache() {
		return pathTable;
	}

	@Override
	public List<Point> getShortestPathTo(Point from, Point to) {
		if (pathTable.contains(from, to)) {
			return pathTable.get(from, to);
		} else {
			List<Point> path = super.getShortestPathTo(from, to);
			pathTable.put(from, to, path);
			return path;
		}
	}

	@Override
	public void addObjectAt(RoadUser newObj, Point pos) {
		super.addObjectAt(newObj, pos);
		classObjectMap.put(newObj.getClass(), newObj);
	}

	@Override
	public void addObjectAtSamePosition(RoadUser newObj, RoadUser existingObj) {
		super.addObjectAtSamePosition(newObj, existingObj);
		classObjectMap.put(newObj.getClass(), newObj);
	}

	@Override
	public void clear() {
		super.clear();
		classObjectMap.clear();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <Y extends RoadUser> Set<Y> getObjectsOfType(final Class<Y> type) {
		Set<Y> set = new HashSet<Y>();
		set.addAll((Set<Y>) classObjectMap.get(type));
		return set;
	}

	@Override
	public void removeObject(RoadUser o) {
		super.removeObject(o);
		classObjectMap.remove(o.getClass(), o);
	}

}
