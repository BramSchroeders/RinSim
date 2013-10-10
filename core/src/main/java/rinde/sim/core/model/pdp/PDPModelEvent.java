package rinde.sim.core.model.pdp;

import javax.annotation.Nullable;

import rinde.sim.core.model.pdp.PDPModel.PDPModelEventType;
import rinde.sim.event.Event;

/**
 * Event object that is dispatched by the {@link DefaultPDPModel}.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class PDPModelEvent extends Event {
  private static final long serialVersionUID = 7008138533321063591L;

  /**
   * The {@link DefaultPDPModel} that dispatched this event.
   */
  public final PDPModel pdpModel;

  /**
   * The time at which the event was dispatched.
   */
  public final long time;

  /**
   * The {@link Parcel} that was involved in the event, or <code>null</code>
   * if there was no {@link Parcel} involved in the event.
   */
  @Nullable
  public final Parcel parcel;

  /**
   * The {@link Vehicle} that was involved in the event, or <code>null</code>
   * if there was no {@link Vehicle} involved in the event.
   */
  @Nullable
  public final Vehicle vehicle;

  PDPModelEvent(PDPModelEventType type, PDPModel model, long t,
      @Nullable Parcel p, @Nullable Vehicle v) {
    super(type, model);
    pdpModel = model;
    time = t;
    parcel = p;
    vehicle = v;
  }
}