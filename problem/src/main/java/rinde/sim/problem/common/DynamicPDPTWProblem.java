/**
 * 
 */
package rinde.sim.problem.common;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;
import static rinde.sim.core.model.pdp.PDPScenarioEvent.TIME_OUT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.random.MersenneTwister;
import org.eclipse.swt.graphics.RGB;

import rinde.sim.core.Simulator;
import rinde.sim.core.model.Model;
import rinde.sim.core.model.pdp.Depot;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.pdp.Vehicle;
import rinde.sim.core.model.pdp.twpolicy.TardyAllowedPolicy;
import rinde.sim.core.model.road.PlaneRoadModel;
import rinde.sim.problem.gendreau06.Gendreau06Scenario;
import rinde.sim.scenario.ScenarioController;
import rinde.sim.scenario.ScenarioController.UICreator;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.scenario.TimedEventHandler;
import rinde.sim.ui.View;
import rinde.sim.ui.renderers.PDPModelRenderer;
import rinde.sim.ui.renderers.PlaneRoadModelRenderer;
import rinde.sim.ui.renderers.Renderer;
import rinde.sim.ui.renderers.RoadUserRenderer;
import rinde.sim.ui.renderers.UiSchema;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class DynamicPDPTWProblem {

	protected static final Map<Class<?>, Creator<?>> DEFAULT_EVENT_CREATOR_MAP = initDefaultEventCreatorMap();

	protected final Map<Class<?>, Creator<?>> eventCreatorMap;
	protected final ScenarioController controller;
	protected final Simulator simulator;
	protected final DefaultUICreator defaultUICreator;
	protected TimeOutHandler timeOutHandler;

	public DynamicPDPTWProblem(DynamicPDPTWScenario scen, long randomSeed, Model<?>... models) {
		simulator = new Simulator(new MersenneTwister(randomSeed), scen.getTickSize());
		// TODO (max) speed must be dependent on scenario
		simulator.register(new PlaneRoadModel(scen.getMin(), scen.getMax(), scen instanceof Gendreau06Scenario, scen
				.getMaxSpeed()));
		simulator.register(new PDPModel(new TardyAllowedPolicy()));
		for (final Model<?> m : models) {
			simulator.register(m);
		}
		eventCreatorMap = newHashMap();

		timeOutHandler = new TimeOutHandler() {
			@Override
			public void handleTimeOut(Simulator sim) {
				sim.stop();
			}
		};

		final TimedEventHandler handler = new TimedEventHandler() {

			@SuppressWarnings("unchecked")
			@Override
			public boolean handleTimedEvent(TimedEvent event) {
				if (eventCreatorMap.containsKey(event.getClass())) {
					return ((Creator<TimedEvent>) eventCreatorMap.get(event.getClass())).create(simulator, event);
				} else if (DEFAULT_EVENT_CREATOR_MAP.containsKey(event.getClass())) {
					return ((Creator<TimedEvent>) DEFAULT_EVENT_CREATOR_MAP.get(event.getClass()))
							.create(simulator, event);
				} else if (event.getEventType() == TIME_OUT) {
					timeOutHandler.handleTimeOut(simulator);
					return true;
				}
				return false;
			}
		};
		final int ticks = scen.getTimeWindow().end == Long.MAX_VALUE ? -1 : (int) (scen.getTimeWindow().end - scen
				.getTimeWindow().begin);
		controller = new ScenarioController(scen, simulator, handler, ticks);

		defaultUICreator = new DefaultUICreator();
	}

	/**
	 * Enables UI using a default visualization.
	 */
	public void enableUI() {
		enableUI(defaultUICreator);
	}

	/**
	 * Allows to add an additional {@link Renderer} to the default UI.
	 * @param r The {@link Renderer} to add.
	 */
	public void addRendererToUI(Renderer r) {
		defaultUICreator.addRenderer(r);
	}

	/**
	 * Enables UI by allowing plugging in a custom {@link UICreator}.
	 * @param creator The creator to use.
	 */
	public void enableUI(UICreator creator) {
		controller.enableUI(creator);
	}

	/**
	 * Executes a simulation of the problem.
	 */
	public void simulate() {
		checkState(eventCreatorMap.containsKey(AddVehicleEvent.class), "A creator for AddVehicleEvent is required, use addCreator(..)");
		controller.start();
	}

	public void forceStop() {
		// this forces the simulation to stop. this should be used to indicate
		// that the simulation has to terminate earlier than expected.
		// TODO add special event? -> at least have property available somewhere
		simulator.stop();
	}

	public Simulator getSimulator() {
		return simulator;
	}

	public void addStatisticsListener(StatisticsListener statList) {
		statList.register(controller, simulator);
	}

	// @SuppressWarnings("unchecked")
	// private <T extends TimedEvent> Creator<T> get(Class<T> clazz) {
	// return (Creator<T>) eventCreatorMap.get(clazz);
	//
	// }

	public <T extends TimedEvent> void addCreator(Class<T> clazz, Creator<T> creator) {
		eventCreatorMap.put(clazz, creator);
	}

	public void setTimeOutHandler(TimeOutHandler toh) {
		timeOutHandler = toh;
	}

	static Map<Class<?>, Creator<?>> initDefaultEventCreatorMap() {
		final Map<Class<?>, Creator<?>> map = newHashMap();
		map.put(AddParcelEvent.class, new Creator<AddParcelEvent>() {
			@Override
			public boolean create(Simulator sim, AddParcelEvent event) {
				return sim.register(new DefaultParcel(event.parcelDTO));
			}
		});
		map.put(AddDepotEvent.class, new Creator<AddDepotEvent>() {
			@Override
			public boolean create(Simulator sim, AddDepotEvent event) {
				return sim.register(new DefaultDepot(event.position));
			}
		});
		return unmodifiableMap(map);
	}

	// factory method for dealing with scenario events, note that the created
	// object must be registered to the simulator, not returned
	public interface Creator<T extends TimedEvent> {

		boolean create(Simulator sim, T event);
	}

	public interface TimeOutHandler {
		void handleTimeOut(Simulator sim);
	}

	public interface StatisticsListener {
		void register(ScenarioController scenContr, Simulator sim);
	}

	class DefaultUICreator implements UICreator {
		protected List<Renderer> renderers;

		public DefaultUICreator() {
			final UiSchema schema = new UiSchema(false);
			schema.add(Vehicle.class, new RGB(255, 0, 0));
			schema.add(Depot.class, new RGB(0, 255, 0));
			schema.add(Parcel.class, new RGB(0, 0, 255));
			renderers = new ArrayList<Renderer>(asList(new PlaneRoadModelRenderer(40), new RoadUserRenderer(schema,
					false), new PDPModelRenderer()));
		}

		@Override
		public void createUI(Simulator sim) {
			View.startGui(sim, 1, renderers.toArray(new Renderer[] {}));
		}

		public void addRenderer(Renderer r) {
			renderers.add(r);
		}
	}
}
