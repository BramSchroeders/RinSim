/**
 * 
 */
package rinde.sim.problem.fabrirecht;

import java.io.FileReader;
import java.io.IOException;

import rinde.sim.core.Simulator;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.model.pdp.PDPModel.ParcelState;
import rinde.sim.core.model.road.RoadModels;
import rinde.sim.core.model.road.RoadUser;
import rinde.sim.problem.common.AddVehicleEvent;
import rinde.sim.problem.common.DefaultParcel;
import rinde.sim.problem.common.DefaultVehicle;
import rinde.sim.problem.common.DynamicPDPTWProblem;
import rinde.sim.problem.common.DynamicPDPTWProblem.Creator;
import rinde.sim.problem.common.DynamicPDPTWProblem.DefaultUICreator;
import rinde.sim.problem.common.VehicleDTO;
import rinde.sim.scenario.ConfigurationException;
import rinde.sim.ui.View;
import rinde.sim.ui.renderers.Renderer;

import com.google.common.base.Predicate;

/**
 * Simplest example showing how the Fabri & Recht problem can be configured
 * using a custom vehicle.
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class RepetitiousGUITest {

	public static void main(String[] args) throws IOException, ConfigurationException {
		System.out.println();
		for (int i = 0; i < 1; i++) {
			final FabriRechtScenario scenario = FabriRechtParser.fromJson(new FileReader(
					"../problem/data/test/fabri-recht/lc101.scenario"), 8, 20);

			final DynamicPDPTWProblem problem = new DynamicPDPTWProblem(scenario, 123);
			problem.addCreator(AddVehicleEvent.class, new Creator<AddVehicleEvent>() {
				@Override
				public boolean create(Simulator sim, AddVehicleEvent event) {
					return sim.register(new Truck(event.vehicleDTO));
				}
			});
			final int iteration = i;

			View.setAutoPlay(true);
			View.setAutoClose(false);
			problem.enableUI(new DefaultUICreator() {
				@Override
				public void createUI(Simulator sim) {
					try {
						View.startGui(sim, 15, renderers.toArray(new Renderer[] {}));
					} catch (final Throwable e) {
						System.err.println("Crash occured at iteration " + iteration);
						e.printStackTrace();
						throw new RuntimeException("This is the end, resistance is futile.");
					}
				}
			});
			problem.simulate();
			if (i % 5 == 0) {
				System.out.print(i);
			} else {
				System.out.print(".");
			}
		}
	}
}

/**
 * This truck implementation only picks parcels up, it does not deliver them.
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
class Truck extends DefaultVehicle {

	public Truck(VehicleDTO pDto) {
		super(pDto);
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		// we always go to the closest available parcel
		final DefaultParcel closest = (DefaultParcel) RoadModels
				.findClosestObject(roadModel.getPosition(this), roadModel, new Predicate<RoadUser>() {
					@Override
					public boolean apply(RoadUser input) {
						return input instanceof DefaultParcel
								&& pdpModel.getParcelState(((DefaultParcel) input)) == ParcelState.AVAILABLE;
					}
				});

		if (closest != null) {
			roadModel.moveTo(this, closest, time);
			if (roadModel.equalPosition(closest, this)
					&& pdpModel.getTimeWindowPolicy()
							.canPickup(closest.getPickupTimeWindow(), time.getTime(), closest.getPickupDuration())) {
				pdpModel.pickup(this, closest, time);
			}
		}
	}
}
