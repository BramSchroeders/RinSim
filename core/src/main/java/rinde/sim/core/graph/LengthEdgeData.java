package rinde.sim.core.graph;


/**
 * Simple data representing an length of the edge
 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be>
 *
 */
public class LengthEdgeData implements EdgeData {

	private final double length;
	private int hashCode;

	public LengthEdgeData(double length) {
		this.length = length;
	}
	
	@Override
	public double getLength() {
		return length;
	}
	
	@Override
	public int hashCode() {
		return new Double(length).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof LengthEdgeData) {
			return length == ((LengthEdgeData) obj).length;
		}
		return false;
	}

	/**
	 * represents a empty value for purpose of {@link TableGraph}
	 */
	public static final LengthEdgeData EMPTY = new LengthEdgeData(Double.NaN);
}
