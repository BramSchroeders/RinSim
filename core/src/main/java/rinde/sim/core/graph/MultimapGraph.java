/**
 * 
 */
package rinde.sim.core.graph;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

/**
 * Multimap-based implementation of a graph.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be> - added edge data
 *         + and dead end nodes
 * @param <E> The type of {@link ConnectionData} that is used in the edges.
 */
public class MultimapGraph<E extends ConnectionData> extends AbstractGraph<E> {

    private final Multimap<Point, Point> data;
    private final Map<Connection<E>, E> edgeData;
    private final Set<Point> deadEndNodes;

    /**
     * Create a new empty graph.
     */
    public MultimapGraph() {
        data = LinkedHashMultimap.create();
        this.edgeData = new HashMap<Connection<E>, E>();
        deadEndNodes = new LinkedHashSet<Point>();
    }

    /**
     * Instantiates a new graph using the specified multimap.
     * @param map The multimap that is copied into this new graph.
     */
    public MultimapGraph(Multimap<Point, Point> map) {
        this.data = LinkedHashMultimap.create(map);
        this.edgeData = new HashMap<Connection<E>, E>();
        this.deadEndNodes = new HashSet<Point>();
        deadEndNodes.addAll(data.values());
        deadEndNodes.removeAll(data.keySet());
    }

    @Override
    public boolean containsNode(Point node) {
        return data.containsKey(node) || deadEndNodes.contains(node);
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
        return data.keySet().size() + deadEndNodes.size();
    }

    @Override
    public E setEdgeData(Point from, Point to, E connData) {
        if (!hasConnection(from, to)) {
            throw new IllegalArgumentException("the connection " + from
                    + " -> " + to + "does not exist");
        }
        return this.edgeData.put(new Connection<E>(from, to, null), connData);
    }

    @Override
    public E connectionData(Point from, Point to) {
        return edgeData.get(new Connection<E>(from, to, null));
    }

    @Override
    public Set<Point> getNodes() {
        final Set<Point> nodes = new LinkedHashSet<Point>(data.keySet());
        nodes.addAll(deadEndNodes);
        return nodes;
    }

    @Override
    public List<Connection<E>> getConnections() {
        final List<Connection<E>> res = new ArrayList<Connection<E>>(
                edgeData.size());
        for (final Entry<Point, Point> p : data.entries()) {
            final Connection<E> connection = new Connection<E>(p.getKey(),
                    p.getValue(), null);
            final E eD = edgeData.get(connection);
            connection.setData(eD);
            res.add(connection);
        }
        return res;
    }

    /**
     * Returns an unmodifiable view on the {@link Multimap} which back this
     * graph.
     * @return The view on the multimap.
     */
    public Multimap<Point, Point> getMultimap() {
        return Multimaps.unmodifiableMultimap(data);
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * Warning: very inefficient! If this function is needed regularly it is
     * advised to use {@link TableGraph} instead. {@inheritDoc}
     */
    @Override
    public Collection<Point> getIncomingConnections(Point node) {
        final Set<Point> set = new LinkedHashSet<Point>();
        for (final Entry<Point, Point> entry : data.entries()) {
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
        // copy data first to avoid concurrent modification exceptions
        final List<Point> out = new ArrayList<Point>();
        out.addAll(getOutgoingConnections(node));
        for (final Point p : out) {
            removeConnection(node, p);
        }
        final List<Point> in = new ArrayList<Point>();
        in.addAll(getIncomingConnections(node));
        for (final Point p : in) {
            removeConnection(p, node);
        }
        deadEndNodes.remove(node);
    }

    @Override
    public void removeConnection(Point from, Point to) {
        checkArgument(hasConnection(from, to), "Can not remove non-existing connection: "
                + from + " -> " + to);
        data.remove(from, to);
        removeData(from, to);
        if (!data.containsKey(to)) {
            deadEndNodes.add(to);
        }
    }

    private void removeData(Point from, Point to) {
        edgeData.remove(new Connection<ConnectionData>(from, to, null));
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(201, 199).append(data).append(deadEndNodes)
                .append(edgeData).toHashCode();
    }

    @Override
    protected void doAddConnection(Point from, Point to, E connData) {
        data.put(from, to);
        deadEndNodes.remove(from);
        if (!data.containsKey(to)) {
            deadEndNodes.add(to);
        }
        if (connData != null) {
            this.edgeData.put(new Connection<E>(from, to, null), connData);
        }
    }
}
