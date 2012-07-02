/**
 * 
 */
package rinde.sim.event;

/**
 * Interface for listening to {@link Event}s.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public interface Listener {
    /**
     * Is called to notify the listener that an {@link Event} was issued.
     * @param e The event.
     */
    void handleEvent(Event e);
}
