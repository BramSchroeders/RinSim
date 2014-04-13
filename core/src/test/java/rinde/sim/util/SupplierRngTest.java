package rinde.sim.util;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertTrue;
import static rinde.sim.util.SupplierRngs.uniformInt;

import java.util.List;

import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.stat.inference.TestUtils;
import org.junit.Test;

import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;

public class SupplierRngTest {

  @Test
  public void testUniform() {
    final RandomGenerator rng = new MersenneTwister(123L);
    final SupplierRng<Integer> sup = uniformInt(2, 10);
    final IntegerDistribution id = new UniformIntegerDistribution(2, 10);

    final Multiset<Integer> ms = TreeMultiset.create();
    for (int i = 0; i < 1000; i++) {
      ms.add(sup.get(rng.nextLong()));
    }
    final List<Integer> observations = newArrayList();
    final List<Double> expectations = newArrayList();
    for (final Multiset.Entry<Integer> entry : ms.entrySet()) {
      observations.add(entry.getCount());
      expectations.add(id.probability(entry.getElement()));
    }
    assertTrue(chiSquare(expectations, observations, .01));
  }

  boolean chiSquare(List<? extends Number> expectations,
      List<? extends Number> observations, double confidence) {
    final double chi = TestUtils.chiSquareTest(Doubles.toArray(expectations),
        Longs.toArray(observations));
    return !(chi < confidence);
  }
}
