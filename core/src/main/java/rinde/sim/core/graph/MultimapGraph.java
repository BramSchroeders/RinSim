/**
 * 
 */
package rinde.sim.core.graph;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

/**
 * @author Rinde van Lon (rinde.vanlon@cs.kuleuven.be)
 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be> - added edge data
 */
public class MultimapGraph<E extends EdgeData> implements Graph<E> {

	private final Multimap<Point, Point> data;
	private final HashMap<Connection<E>, E> edgeData;

	public MultimapGraph(Multimap<Point, Point> data) {
		this.data = LinkedHashMultimap.create(data);
		this.edgeData = new HashMap<Connection<E>, E>();
	}

	public MultimapGraph() {
		data = LinkedHashMultimap.create();
		this.edgeData = new HashMap<Connection<E>, E>();
	}

	@Override
	public boolean containsNode(Point node) {
		return data.containsKey(node);
	}

	@Override
	public Collection<Point> getOutgoingConnections(Point node) {
		return data.get(node);
	}

	@Override
	public boolean hasConnection(Point from, Point to) {
		return data.containsEntry(from, to);
	}

	@Override
	public int getNumberOfConnections() {
		return data.size();
	}

	@Override
	public int getNumberOfNodes() {
		return data.keySet().size();
	}

	@Override
	public void addConnection(Point from, Point to) {
		addConnection(from,to,null);
	}
	
	

	@Override
	public void addConnection(Point from, Point to, E edgeData) {
		if (from.equals(to)) {
			throw new IllegalArgumentException("A connection cannot be circular");
		}
		data.put(from, to);
		if(edgeData != null) {
			this.edgeData.put(new Connection<E>(from, to, null), edgeData);
		}
	}

	@Override
	public void addConnection(Connection<E> c) {
		if(c == null) return;
		addConnection(c.from, c.to, c.edgeData);
	}

	@Override
	public E setEdgeData(Point from, Point to, E edgeData) {
		if(! hasConnection(from, to)) throw new IllegalArgumentException("the connection " + from + " -> " + to + "does not exist");
		return this.edgeData.put(new Connection<E>(from, to, null), edgeData);
	}
	
	@Override
	public E connectionData(Point from, Point to) {
		return edgeData.get(new Connection<E>(from, to, null));
	}

	@Override
	public Set<Point> getNodes() {
		return Collections.unmodifiableSet(new LinkedHashSet<Point>(data.keySet()));
	}

	@Override
	public List<Connection<E>> getConnections() {
		ArrayList<Connection<E>> res = new ArrayList<Connection<E>>(edgeData.size());
		for (Entry<Point, Point> p : data.entries()) {
			Connection<E> connection = new Connection<E>(p.getKey(), p.getValue(), null);
			E eD = edgeData.get(connection);
			connection.setEdgeData(eD);
			res.add(connection);
		}
		return res;
	}

	// returns the backing multimap
	public Multimap<Point, Point> getMultimap() {
		return Multimaps.unmodifiableMultimap(data);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void merge(Graph<E> other) {
		//FIXME [bm]
		if (other instanceof MultimapGraph) {
			data.putAll(((MultimapGraph) other).getMultimap());
		} else {
			addConnections(other.getConnections());
		}

	}

	@Override
	public void addConnections(Collection<Connection<E>> connections) {
		for (Connection<E> connection : connections) {
			addConnection(connection);
		}
	}

	@Override
	public boolean isEmpty() {
		return data.isEmpty();
	}

	/**
	 * Warning: very inefficient! If this function is needed regularly it is
	 * advised to use {@link TableGraph} instead.
	 */
	@Override
	public Collection<Point> getIncomingConnections(Point node) {
		HashSet<Point> set = new LinkedHashSet<Point>();
		for (Entry<Point, Point> entry : data.entries()) {
			if (entry.getValue().equals(node)) {
				set.add(entry.getKey());
			}
		}
		return set;
	}

	/**
	 * Warning: very inefficient! If this function is needed regularly it is
	 * advised to use {@link TableGraph} instead.
	 */
	@Override
	public void removeNode(Point node) {
		for(Point p : getOutgoingConnections(node)) {
			removeConnection(node, p);
		}
		for (Point p : getIncomingConnections(node)) {
			removeConnection(p, node);
		}
	}

	@Override
	public void removeConnection(Point from, Point to) {
		if (hasConnection(from, to)) {
			data.remove(from, to);
			removeData(from, to);
		} else {
			throw new IllegalArgumentException("Can not remove non-existing connection: " + from + " -> " + to);
		}
	}
	
	private void removeData(Point from, Point to) {
		edgeData.remove(new Connection<EdgeData>(from, to, null));
	}

	@Override
	public double connectionLength(Point from, Point to) {
		if (hasConnection(from, to)) {
			return Point.distance(from, to);
		}
		throw new IllegalArgumentException("Can not get connection length from a non-existing connection.");
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public boolean equals(Object other) {
		return other instanceof Graph ? equals((Graph) other) : false;
	}


	@Override
	public boolean equals(Graph<? extends E> other) {
		return Graphs.equals(this, other);
	}
}