/**
 * 
 */
package rinde.sim.pdptw.generator;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static rinde.sim.pdptw.generator.Metrics.measureDynamism;
import static rinde.sim.pdptw.generator.Metrics.measureLoad;
import static rinde.sim.pdptw.generator.Metrics.sum;
import static rinde.sim.pdptw.generator.Metrics.travelTime;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import rinde.sim.core.graph.Point;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.pdptw.generator.Metrics.LoadPart;
import rinde.sim.util.TimeWindow;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class MetricsTest {

  static final double EPSILON = 0.00001;

  @Test
  public void testLoad1() {
    // distance is 1 km which is traveled in 2 minutes with 30km/h
    final ParcelDTO dto = new ParcelDTO(new Point(0, 0), new Point(0, 1),
        new TimeWindow(0, 10), new TimeWindow(10, 20), 0, 0, 5, 5);

    final List<LoadPart> parts = measureLoad(new AddParcelEvent(dto), 30);
    assertEquals(3, parts.size());

    // pickup load in [0,15), duration is 5 minutes, so load is 5/15 = 1/3
    assertEquals(0, parts.get(0).begin);
    assertEquals(1 / 3d, parts.get(0).get(0), EPSILON);
    assertEquals(15, parts.get(0).length());

    // travel load in [5,20), duration is 2 minutes, so load is 2/15
    assertEquals(5, parts.get(1).begin);
    assertEquals(2 / 15d, parts.get(1).get(5), EPSILON);
    assertEquals(15, parts.get(1).length());

    // delivery load in [10,25), duration is 5 minutes, so load is 5/15 =
    // 1/3
    assertEquals(10, parts.get(2).begin);
    assertEquals(1 / 3d, parts.get(2).get(10), EPSILON);
    assertEquals(15, parts.get(2).length());

    // summing results:
    // [0,5) - 5/15
    // [5,10) - 7/15
    // [10,15) - 12/15
    // [15,20) - 7/15
    // [20,25) - 5/15

    final List<Double> load = sum(0, parts, 1);
    checkRange(load, 0, 5, 5 / 15d);
    checkRange(load, 5, 10, 7 / 15d);
    checkRange(load, 10, 15, 12 / 15d);
    checkRange(load, 15, 20, 7 / 15d);
    checkRange(load, 20, 25, 5 / 15d);
    assertEquals(25, load.size());
  }

  @Test
  public void testLoad2() {
    // distance is 10km which is travelled in 20 minutes with 30km/h
    final ParcelDTO dto = new ParcelDTO(new Point(0, 0), new Point(0, 10),
        new TimeWindow(15, 15), new TimeWindow(15, 15), 0, 0, 5, 5);

    final List<LoadPart> parts = measureLoad(new AddParcelEvent(dto), 30);
    assertEquals(3, parts.size());

    // pickup load in [15,20), duration is 5 minutes, so load is 5/5 = 1
    assertEquals(15, parts.get(0).begin);
    assertEquals(1d, parts.get(0).get(15), EPSILON);
    assertEquals(0d, parts.get(0).get(20), EPSILON);
    assertEquals(5, parts.get(0).length());

    // travel load in [20,40), duration is 20 minutes, so load is 20/20 = 1
    assertEquals(20, parts.get(1).begin);
    assertEquals(1, parts.get(1).get(20), EPSILON);
    assertEquals(20, parts.get(1).length());

    // delivery load in [40,45), duration is 5 minutes, so load is 5/5 = 1
    assertEquals(40, parts.get(2).begin);
    assertEquals(1, parts.get(2).get(40), EPSILON);
    assertEquals(5, parts.get(2).length());

    // summing results:
    // [0,15) - 0
    // [15,45) - 1

    final List<Double> load = sum(0, parts, 1);
    checkRange(load, 0, 15, 0);
    checkRange(load, 15, 45, 1);
    assertEquals(45, load.size());
  }

  @Test
  public void testLoad3() {
    // distance is 3 km which is traveled in 6 minutes with 30km/h
    final ParcelDTO dto = new ParcelDTO(new Point(0, 0), new Point(0, 3),
        new TimeWindow(10, 30), new TimeWindow(50, 75), 0, 0, 5, 5);

    final List<LoadPart> parts = measureLoad(new AddParcelEvent(dto), 30);
    assertEquals(3, parts.size());

    // pickup load in [10,35), duration is 5 minutes, so load is 5/25 = 6/30
    assertEquals(10, parts.get(0).begin);
    assertEquals(6 / 30d, parts.get(0).get(10), EPSILON);
    assertEquals(25, parts.get(0).length());

    // travel load in [15,75), duration is 6 minutes, so load is 6/60 = 3/30
    assertEquals(15, parts.get(1).begin);
    assertEquals(3 / 30d, parts.get(1).get(15), EPSILON);
    assertEquals(60, parts.get(1).length());

    // delivery load in [50,80), duration is 5 minutes, so load is 5/30
    assertEquals(50, parts.get(2).begin);
    assertEquals(5 / 30d, parts.get(2).get(50), EPSILON);
    assertEquals(30, parts.get(2).length());

    // summing results:
    // [00,10) - 0/30
    // [10,15) - 6/30
    // [15,35) - 9/30
    // [35,50) - 3/30
    // [50,75) - 8/30
    // [75,80) - 5/30

    final List<Double> load = sum(0, parts, 1);
    checkRange(load, 0, 10, 0d);
    checkRange(load, 10, 15, 6 / 30d);
    checkRange(load, 15, 35, 9 / 30d);
    checkRange(load, 35, 50, 3 / 30d);
    checkRange(load, 50, 75, 8 / 30d);
    checkRange(load, 75, 80, 5 / 30d);
    assertEquals(80, load.size());
  }

  // checks whether the range [from,to) in list contains value val
  static void checkRange(List<Double> list, int from, int to, double val) {
    for (int i = from; i < to; i++) {
      assertEquals(val, list.get(i), EPSILON);
    }
  }

  @Test
  public void testTravelTime() {
    // driving 1 km with 30km/h should take exactly 2 minutes
    assertEquals(2, travelTime(new Point(0, 0), new Point(1, 0), 30d));

    // driving 1 km with 60km/h should take exactly 1 minutes
    assertEquals(1, travelTime(new Point(0, 0), new Point(1, 0), 60d));

    // driving 1 km with 90km/h should take .667 minutes, which should be
    // rounded to 1 minute.
    assertEquals(1, travelTime(new Point(0, 0), new Point(1, 0), 90d));

    // TODO check the rounding behavior
  }

  /**
   * Test for the dynamism function.
   */
  @Test
  public void dynamismTest() {
    assertEquals(.5, measureDynamism(asList(1L, 2L, 3L, 4L, 5L), 10,
        1), EPSILON);
    // duplicates should be ignored
    assertEquals(.5, measureDynamism(asList(1L, 2L, 2L, 3L, 4L, 5L), 10,
        1), EPSILON);

    // granularity equals length of day
    assertEquals(1d, measureDynamism(asList(1L, 2L, 2L, 3L, 4L, 5L), 10,
        10), EPSILON);

    // check valid border values of times
    assertEquals(.6, measureDynamism(asList(0L, 2L, 2L, 3L, 4L, 8L, 9L), 10,
        1), EPSILON);

    assertEquals(.1, measureDynamism(asList(0L, 2L, 2L, 3L, 4L, 8L, 9L), 100,
        5), EPSILON);

    assertEquals(2 / 7d,
        measureDynamism(asList(0L, 2L, 2L, 3L, 4L, 8L, 9L), 49,
            7), EPSILON);

    // both intervals
    assertEquals(1d,
        measureDynamism(asList(0L, 2L, 2L, 3L, 4L, 8L, 9L), 10,
            5), EPSILON);

    // no time
    assertEquals(0d,
        measureDynamism(Arrays.<Long> asList(), 10,
            5), EPSILON);

    // check interval borders
    assertEquals(0.5d,
        measureDynamism(asList(1000L, 1999L), 2000,
            1000), EPSILON);
    assertEquals(2 / 3d,
        measureDynamism(asList(1000L, 2000L), 3000,
            1000), EPSILON);

  }

  /**
   * Length of day must be positive.
   */
  @Test(expected = IllegalArgumentException.class)
  public void dynamismIllegalArgument1() {
    measureDynamism(asList(1L), 0, 7);
  }

  /**
   * Granularity must be <= length of day.
   */
  @Test(expected = IllegalArgumentException.class)
  public void dynamismIllegalArgument2() {
    measureDynamism(asList(1L), 1, 7);
  }

  /**
   * Granularity needs to fit an exact number of times in length of day.
   */
  @Test(expected = IllegalArgumentException.class)
  public void dynamismIllegalArgument3() {
    measureDynamism(asList(1L, 2L), 10, 7);
  }

  /**
   * Times can not be >= lengthOfDay.
   */
  @Test(expected = IllegalArgumentException.class)
  public void dynamismIllegalArgument4a() {
    measureDynamism(asList(1L, 2L, 10L), 10, 1);
  }

  /**
   * Times can not be < 0.
   */
  @Test(expected = IllegalArgumentException.class)
  public void dynamismIllegalArgument4b() {
    measureDynamism(asList(1L, 2L, -1L), 10, 1);
  }

  static <T> boolean areAllValuesTheSame(List<T> list) {
    if (list.isEmpty()) {
      return true;
    }
    return Collections.frequency(list, list.get(0)) == list.size();
  }
}
