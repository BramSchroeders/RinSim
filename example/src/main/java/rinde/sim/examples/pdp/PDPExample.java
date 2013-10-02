/**
 * 
 */
package rinde.sim.examples.pdp;

import static com.google.common.collect.Maps.newHashMap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import rinde.sim.core.Simulator;
import rinde.sim.core.TickListener;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Graph;
import rinde.sim.core.graph.MultiAttributeData;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.Depot;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.GraphRoadModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.serializers.DotGraphSerializer;
import rinde.sim.serializers.SelfCycleFilter;
import rinde.sim.ui.View;
import rinde.sim.ui.renderers.GraphRoadModelRenderer;
import rinde.sim.ui.renderers.RoadUserRenderer;
import rinde.sim.ui.renderers.UiSchema;
import rinde.sim.util.TimeWindow;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public final class PDPExample {

  private static final int NUM_DEPOTS = 0;
  private static final int NUM_TRUCKS = 10;
  private static final int NUM_PARCELS = 60;

  // time in ms
  private static final long SERVICE_DURATION = 60000;
  private static final double PARCEL_MAGNITUDE = 1d;
  private static final int TRUCK_CAPACITY = 10;
  private static final int DEPOT_CAPACITY = 100;

  private static final String MAP_FILE = "../core/files/maps/leuven-simple.dot";
  private static final Map<String, Graph<?>> GRAPH_CACHE = newHashMap();

  static Graph<MultiAttributeData> load(String name) {
    try {
      return DotGraphSerializer.getMultiAttributeGraphSerializer(
          new SelfCycleFilter()).read(name);
    } catch (final FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private PDPExample() {}

  /**
   * Starts the {@link PDPExample}.
   * @param args
   */
  public static void main(String[] args) {

    final long endTime = args != null && args.length >= 1 ? Long
        .parseLong(args[0]) : Long.MAX_VALUE;

    final String graphFile = args != null && args.length >= 2 ? args[1]
        : MAP_FILE;

    final Display d = new Display();
    final Rectangle rect = d.getPrimaryMonitor().getBounds();
    d.dispose();

    Graph<?> g;
    if (GRAPH_CACHE.containsKey(graphFile)) {
      g = GRAPH_CACHE.get(graphFile);
    } else {
      g = load(graphFile);
      GRAPH_CACHE.put(graphFile, g);
    }

    // create a new simulator, load map of Leuven
    final RandomGenerator rng = new MersenneTwister(123);
    final Simulator simulator = new Simulator(rng, Measure.valueOf(1000L,
        SI.MILLI(SI.SECOND)));

    final RoadModel roadModel = new GraphRoadModel(g);
    final PDPModel pdpModel = new PDPModel();
    simulator.register(roadModel);
    simulator.register(pdpModel);
    simulator.configure();

    for (int i = 0; i < NUM_DEPOTS; i++) {
      simulator.register(new ExampleDepot(roadModel.getRandomPosition(rng),
          DEPOT_CAPACITY));
    }
    for (int i = 0; i < NUM_TRUCKS; i++) {
      simulator.register(new ExampleTruck(roadModel.getRandomPosition(rng),
          TRUCK_CAPACITY));
    }
    for (int i = 0; i < NUM_PARCELS; i++) {
      simulator.register(new ExampleParcel(roadModel.getRandomPosition(rng),
          roadModel.getRandomPosition(rng), SERVICE_DURATION, SERVICE_DURATION,
          PARCEL_MAGNITUDE));
    }

    simulator.addTickListener(new TickListener() {
      @Override
      public void tick(TimeLapse time) {
        if (time.getStartTime() > endTime) {
          simulator.stop();
        }
      }

      @Override
      public void afterTick(TimeLapse timeLapse) {}
    });

    final UiSchema uis = new UiSchema();
    uis.add(ExampleDepot.class, "/graphics/perspective/tall-building-64.png");
    uis.add(ExampleTruck.class, "/graphics/flat/taxi-32.png");
    uis.add(ExampleParcel.class, "/graphics/flat/person-red-32.png");
    View.create(simulator)
        .with(new GraphRoadModelRenderer(), new RoadUserRenderer(uis, false))
        .enableAutoClose().enableAutoPlay()
        .setResolution(rect.width, rect.height).setSpeedUp(4).show();
  }

  static class ExampleDepot extends Depot {
    ExampleDepot(Point position, double capacity) {
      setStartPosition(position);
      setCapacity(capacity);
    }

    @Override
    public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}
  }

  static class ExampleParcel extends Parcel {
    ExampleParcel(Point startPosition, Point pDestination,
        long pLoadingDuration, long pUnloadingDuration, double pMagnitude) {
      super(pDestination, pLoadingDuration, TimeWindow.ALWAYS,
          pUnloadingDuration, TimeWindow.ALWAYS, pMagnitude);
      setStartPosition(startPosition);
    }

    @Override
    public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {}
  }
}
