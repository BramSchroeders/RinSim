package rinde.sim.pdptw.scenario;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.core.graph.Point;
import rinde.sim.pdptw.common.AddDepotEvent;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRngs;

import com.google.common.collect.ImmutableList;

/**
 * Utility class for creating {@link DepotGenerator}s.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public final class Depots {
  private static final DepotGenerator SINGLE_CENTERED_DEPOT_GENERATOR = new DepotGenerator() {
    @Override
    public Iterable<? extends AddDepotEvent> generate(long seed, Point center) {
      return ImmutableList.of(new AddDepotEvent(-1, center));
    }
  };

  private Depots() {}

  /**
   * @return A {@link DepotGenerator} that creates a single
   *         {@link AddDepotEvent} that places the depot at the center of the
   *         area.
   */
  public static DepotGenerator singleCenteredDepot() {
    return SINGLE_CENTERED_DEPOT_GENERATOR;
  }

  /**
   * @return A new builder for creating arbitrarily complex
   *         {@link DepotGenerator}s.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Generator of {@link AddDepotEvent}s.
   * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
   */
  public interface DepotGenerator {
    /**
     * Should create a {@link AddDepotEvent} for each required depot.
     * @param seed The random seed to use for generating the depots.
     * @param center The center of the area as defined by the context (usually a
     *          scenario generator).
     * @return The list of events.
     */
    Iterable<? extends AddDepotEvent> generate(long seed, Point center);
  }

  /**
   * Builder for creating {@link DepotGenerator}s.
   * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
   */
  public static class Builder {
    SupplierRng<Point> positions;
    SupplierRng<Integer> numberOfDepots;
    SupplierRng<Long> times;

    Builder() {
      positions = SupplierRngs.constant(new Point(0d, 0d));
      numberOfDepots = SupplierRngs.constant(1);
      times = SupplierRngs.constant(-1L);
    }

    /**
     * Set where the positions of the depots should be coming from.
     * @param ps The supplier to use for points.
     * @return This, as per the builder pattern.
     */
    public Builder positions(SupplierRng<Point> ps) {
      positions = ps;
      return this;
    }

    /**
     * Sets the number of depots that the {@link DepotGenerator} should
     * generate. This number is {@link SupplierRng} itself meaning that it can
     * be drawn from a random distribution.
     * @param nd The number of depots.
     * @return This, as per the builder pattern.
     */
    public Builder numerOfDepots(SupplierRng<Integer> nd) {
      numberOfDepots = nd;
      return this;
    }

    /**
     * Sets the event times that will be used for the creation of the depots.
     * @param ts The event times.
     * @return This, as per the builder pattern.
     */
    public Builder times(SupplierRng<Long> ts) {
      times = ts;
      return this;
    }

    /**
     * @return Creates a new {@link DepotGenerator} based on this builder.
     */
    public DepotGenerator build() {
      return new MultiDepotGenerator(this);
    }
  }

  private static class MultiDepotGenerator implements DepotGenerator {
    private final SupplierRng<Point> positions;
    private final SupplierRng<Integer> numberOfDepots;
    private final SupplierRng<Long> times;
    private final RandomGenerator rng;

    MultiDepotGenerator(Builder b) {
      positions = b.positions;
      numberOfDepots = b.numberOfDepots;
      times = b.times;
      rng = new MersenneTwister();
    }

    @Override
    public Iterable<? extends AddDepotEvent> generate(long seed, Point center) {
      rng.setSeed(seed);
      final int num = numberOfDepots.get(rng.nextLong());
      final ImmutableList.Builder<AddDepotEvent> builder = ImmutableList
          .builder();
      for (int i = 0; i < num; i++) {
        final long time = times.get(rng.nextLong());
        final Point position = positions.get(rng.nextLong());
        builder.add(new AddDepotEvent(time, position));
      }
      return builder.build();
    }
  }
}
