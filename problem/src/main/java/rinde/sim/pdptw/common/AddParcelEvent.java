package rinde.sim.pdptw.common;

import rinde.sim.core.model.pdp.PDPScenarioEvent;
import rinde.sim.scenario.TimedEvent;

/**
 * Event indicating that a parcel can be created.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class AddParcelEvent extends TimedEvent {
	private static final long serialVersionUID = -5053179165237737817L;

	/**
	 * The data which should be used to instantiate a new parcel.
	 */
	public final ParcelDTO parcelDTO;

	/**
	 * New instance.
	 * @param dto {@link #parcelDTO}
	 */
	public AddParcelEvent(ParcelDTO dto) {
		super(PDPScenarioEvent.ADD_PARCEL, dto.orderArrivalTime);
		parcelDTO = dto;
	}
}