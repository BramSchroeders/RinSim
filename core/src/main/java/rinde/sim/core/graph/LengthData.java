package rinde.sim.core.graph;

/**
 * Simple implementation of {@link ConnectionData}, allowing to specify the
 * length of a connection.
 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be>
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class LengthData implements ConnectionData {

    private final double length;

    /**
     * Instantiate a new instance using the specified length.
     * @param pLength The length of the connection.
     */
    public LengthData(double pLength) {
        length = pLength;
    }

    @Override
    public double getLength() {
        return length;
    }

    @Override
    public int hashCode() {
        return Double.valueOf(length).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LengthData) {
            return Double.compare(length, ((LengthData) obj).length) == 0;
        }
        return false;
    }

    @Override
    public String toString() {
        return length + "";
    }

    /**
     * Represents an empty value for usage in a {@link TableGraph}.
     */
    public static final LengthData EMPTY = new LengthData(Double.NaN);
}
