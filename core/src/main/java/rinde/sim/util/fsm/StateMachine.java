package rinde.sim.util.fsm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import rinde.sim.event.Event;
import rinde.sim.event.EventAPI;
import rinde.sim.event.EventDispatcher;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table.Cell;

/**
 * A simple state machine. The state machine is represented by a transition
 * table. A transition has the form <code>current state + event </code> &rarr;
 * <code>new state</code>. A transition can be recurrent, meaning that an event
 * will <i>not</i> initiate a transition. Events can be initiated from within a
 * state by means of the {@link State#handle(Object, Object)} method or from
 * outside the state machine by means of the {@link #handle(Object, Object)}. An
 * attempt to perform a transition not present in the transition table result in
 * a {@link RuntimeException}. Note that the transition table is immutable.
 * StateMachine instances can only be created using its builder via the
 * {@link #create(State)} method.
 * 
 * @param <E> The event type. Concrete event objects that describe the same
 *          event should be <i>equal</i> (according to {@link #equals(Object)} )
 *          and their {@link #hashCode()} implementation should return the same
 *          value. If the events do not need to contain additional meta data,
 *          {@link Enum}s are the best choice.
 * @param <C> The context type. This is typically the object that contains the
 *          {@link StateMachine}, a {@link State} represents a state of this
 *          object.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class StateMachine<E, C> {
  // TODO events *inside* the state machine should be renamed to triggers (for
  // example) to avoid confusion with the event package.
  private static final String NL = System.getProperty("line.separator");
  private static final String NODE = "node";
  private static final String NODE_DEFINITION = "[label=\"\",shape=point]" + NL;
  private static final String CONN = " -> ";
  private static final String LABEL_OPEN = "[label=\"";
  private static final String LABEL_CLOSE = "\"]" + NL;
  private static final String FILE_OPEN = "digraph stategraph {" + NL;
  private static final String FILE_CLOSE = "}";

  /**
   * The type of {@link Event}s that this {@link StateMachine} supports.
   */
  public enum StateMachineEvent {
    /**
     * This event is dispatched when the state machine changes its state. It is
     * called right after the actual state change, after the
     * {@link State#onEntry(Object, Object)} is called but before
     * {@link State#handle(Object, Object)} is called.
     */
    STATE_TRANSITION;
  }

  /**
   * The {@link EventDispatcher} used for dispatching events.
   */
  protected final EventDispatcher eventDispatcher;
  /**
   * The transition table which defines the allowed transitions.
   */
  protected final ImmutableTable<State<E, C>, E, State<E, C>> transitionTable;
  /**
   * The current state.
   */
  protected State<E, C> currentState;
  /**
   * The initial state.
   */
  protected final State<E, C> startState;

  /**
   * This indicates whether recursive transitions should be handled explicitly.
   * If true a recursive transition will be handled just like a normal
   * transition, {@link State#onExit(Object, Object)} and
   * {@link State#onEntry(Object, Object)} methods are called.
   */
  protected final boolean explicitRecursiveTransitions;

  StateMachine(State<E, C> start,
      ImmutableTable<State<E, C>, E, State<E, C>> table,
      boolean explRecurTrns) {
    eventDispatcher = new EventDispatcher(StateMachineEvent.values());
    startState = start;
    currentState = start;
    transitionTable = table;
    explicitRecursiveTransitions = explRecurTrns;
  }

  /**
   * Gives the current {@link State} time to update.
   * @param context Reference to the context.
   */
  public void handle(C context) {
    handle(null, context);
  }

  /**
   * Handle the specified event.
   * @param event The event that needs to be handled by the state machine. If
   *          this results in an attempt to perform a transition which is not
   *          allowed an {@link IllegalArgumentException} is thrown.
   * @param context Reference to the context.
   */
  public void handle(@Nullable E event, C context) {
    if (event != null) {
      changeState(event, context);
    }
    @Nullable
    final E newEvent = currentState.handle(event, context);
    if (newEvent != null) {
      handle(newEvent, context);
    }
  }

  /**
   * Perform a state change if possible.
   * @param event The event that may initiate a state change.
   * @param context Reference to the context.
   */
  protected void changeState(E event, C context) {
    checkArgument(transitionTable.contains(currentState, event),
        "The event %s is not supported when in state %s.", event, currentState);
    final State<E, C> newState = transitionTable.get(currentState, event);
    if (!newState.equals(currentState) || explicitRecursiveTransitions) {
      currentState.onExit(event, context);
      final State<E, C> oldState = currentState;
      currentState = newState;
      currentState.onEntry(event, context);
      eventDispatcher.dispatchEvent(new StateTransitionEvent<E, C>(this,
          oldState, event, newState));
    }
  }

  /**
   * @return A reference to the current state of this {@link StateMachine}.
   */
  public State<E, C> getCurrentState() {
    return currentState;
  }

  /**
   * Convenience method for checking whether the current state is the same as
   * the specified state.
   * @param s The state to be checked.
   * @return <code>true</code> when the states are the same object,
   *         <code>false</code> otherwise.
   */
  public boolean stateIs(State<E, C> s) {
    return currentState.equals(s);
  }

  /**
   * Convenience method for checking whether the current state is one of the
   * specified states.
   * @param states The states to be checked.
   * @return <code>true</code> when the current state is one of the specified
   *         states, <code>false</code> otherwise.
   */
  public boolean stateIsOneOf(State<E, C>... states) {
    for (final State<E, C> s : states) {
      if (stateIs(s)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return An {@link ImmutableCollection} of all states in this state machine.
   */
  public ImmutableCollection<State<E, C>> getStates() {
    return transitionTable.values();
  }

  /**
   * Looks up a state of the specified (sub)type if it exists. If there exist
   * multiple the first encountered is returned.
   * @param type The (sub)type to look for.
   * @param <T> The type.
   * @return The state of the specified type.
   * @throws IllegalArgumentException if there is no state of the specified
   *           type.
   */
  public <T> T getStateOfType(Class<T> type) {
    for (final State<E, C> state : getStates()) {
      if (type.isInstance(state)) {
        return type.cast(state);
      }
    }
    throw new IllegalArgumentException("There is no instance of " + type
        + " in this state machine.");
  }

  /**
   * Returns true if the current state supports the event.
   * @param event The event to check.
   * @return <code>true</code> when the specified event is supported by the
   *         current state, <code>false</code> otherwise.
   */
  public boolean isSupported(E event) {
    return transitionTable.contains(currentState, event);
  }

  /**
   * @return The {@link EventAPI} which allows to add and remove
   *         {@link rinde.sim.event.Listener}s to this {@link StateMachine}.
   */
  public EventAPI getEventAPI() {
    return eventDispatcher.getPublicEventAPI();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(startState, transitionTable, currentState,
        explicitRecursiveTransitions);
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (other == null) {
      return false;
    }
    if (this == other) {
      return true;
    }
    if (!(other instanceof StateMachine)) {
      return false;
    }
    @SuppressWarnings("unchecked")
    final StateMachine<E, C> fsm = (StateMachine<E, C>) other;
    return Objects.equal(startState, fsm.startState)
        && Objects.equal(transitionTable, fsm.transitionTable)
        && Objects.equal(currentState, fsm.currentState)
        && Objects.equal(explicitRecursiveTransitions,
            fsm.explicitRecursiveTransitions);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("startState", startState)
        .add("transitionTable", transitionTable)
        .add("currentState", currentState)
        .add("explicitRecursiveTransitions", explicitRecursiveTransitions)
        .toString();
  }

  /**
   * @return A dot representation of the state machine, can be used for
   *         debugging the transition table.
   */
  public String toDot() {
    int id = 0;
    final StringBuilder builder = new StringBuilder();
    builder.append(FILE_OPEN);
    final Set<State<E, C>> allStates = newHashSet();
    allStates.addAll(transitionTable.rowKeySet());
    allStates.addAll(transitionTable.values());
    final Map<State<E, C>, Integer> idMap = newHashMap();
    for (final State<E, C> s : allStates) {
      builder.append(NODE).append(id).append(LABEL_OPEN).append(s.name())
          .append(LABEL_CLOSE);
      idMap.put(s, id);
      id++;
    }
    builder.append(NODE).append(id).append(NODE_DEFINITION);
    builder.append(NODE).append(id).append(CONN).append(NODE)
        .append(idMap.get(startState)).append(NL);

    for (final Cell<State<E, C>, E, State<E, C>> cell : transitionTable
        .cellSet()) {
      final int id1 = idMap.get(cell.getRowKey());
      final int id2 = idMap.get(cell.getValue());
      builder.append(NODE).append(id1).append(CONN).append(NODE).append(id2)
          .append(LABEL_OPEN).append(cell.getColumnKey()).append(LABEL_CLOSE);
    }
    builder.append(FILE_CLOSE);
    return builder.toString();
  }

  /**
   * Create a new {@link StateMachine} instance with the specified initial
   * state. This method returns a reference to the {@link StateMachineBuilder}
   * which allows for adding of transitions to the state machine.
   * @param initialState The start state of the state machine.
   * @param <E> The event type.
   * @param <C> The context type.
   * @return A reference to the {@link StateMachineBuilder} which is used for
   *         creating the {@link StateMachine}.
   */
  public static <E, C> StateMachineBuilder<E, C> create(State<E, C> initialState) {
    return new StateMachineBuilder<E, C>(initialState);
  }

  /**
   * Facilitates the creation of a {@link StateMachine}.
   * @param <E> Event parameter of {@link StateMachine}.
   * @param <C> Context parameter of {@link StateMachine}.
   * @see StateMachine
   */
  public static final class StateMachineBuilder<E, C> {
    private final ImmutableTable.Builder<State<E, C>, E, State<E, C>> tableBuilder;
    private final State<E, C> start;
    private boolean explicitRecursiveTransitions;

    StateMachineBuilder(State<E, C> initialState) {
      tableBuilder = ImmutableTable.builder();
      start = initialState;
      explicitRecursiveTransitions = false;
    }

    /**
     * Add a transition: <code>state + event &rarr; new state</code>.
     * @param from The from state.
     * @param event The event which triggers the transition.
     * @param to The destination of the transition, the new state.
     * @return A reference to this for method chaining.
     */
    public StateMachineBuilder<E, C> addTransition(State<E, C> from, E event,
        State<E, C> to) {
      tableBuilder.put(from, event, to);
      return this;
    }

    /**
     * Adds all transitions in the specified {@link StateMachine} to this
     * builder. Duplicates are not allowed.
     * @param sm The {@link StateMachine} from which the transitions are copied.
     * @return The builder reference.
     */
    public StateMachineBuilder<E, C> addTransitionsFrom(StateMachine<E, C> sm) {
      tableBuilder.putAll(sm.transitionTable);
      return this;
    }

    /**
     * Enables explicit recursive transitions, this means that when an recursive
     * transition is attempted the {@link State#onExit(Object, Object)} and
     * {@link State#onEntry(Object, Object)} are called on that state and a
     * {@link StateMachine.StateTransitionEvent} is dispatched. By default
     * recursive transitions are ignored.
     * @return The builder reference.
     */
    public StateMachineBuilder<E, C> explicitRecursiveTransitions() {
      explicitRecursiveTransitions = true;
      return this;
    }

    /**
     * Builds the {@link StateMachine} as configured by this
     * {@link rinde.sim.util.fsm.StateMachine.StateMachineBuilder}.
     * @return The {@link StateMachine}.
     */
    public StateMachine<E, C> build() {
      return new StateMachine<E, C>(start, tableBuilder.build(),
          explicitRecursiveTransitions);
    }
  }

  /**
   * Event class used by {@link StateMachine}.
   * @param <E> Event parameter of {@link StateMachine}.
   * @param <C> Context parameter of {@link StateMachine}.
   * @see StateMachine
   * @see StateMachineEvent
   */
  public static class StateTransitionEvent<E, C> extends Event {
    private static final long serialVersionUID = -1478171329851890047L;
    /**
     * The previous state of the state machine prior to the current state.
     */
    public final State<E, C> previousState;
    /**
     * The new state which was activated just before this event was issued.
     */
    public final State<E, C> newState;
    /**
     * The event which trigger the event transition.
     */
    public final E event;

    /**
     * Create new event instance.
     * @param issuer Issuer of the event.
     * @param prev {@link #previousState}.
     * @param e {@link #event}.
     * @param next {@link #newState}.
     */
    protected StateTransitionEvent(StateMachine<E, C> issuer, State<E, C> prev,
        E e, State<E, C> next) {
      super(StateMachineEvent.STATE_TRANSITION, issuer);
      previousState = prev;
      event = e;
      newState = next;
    }

    /**
     * Convenience method for checking whether this event equals a transition as
     * specified by the parameters.
     * @param prev Previous state.
     * @param ev Trigger event.
     * @param next New state.
     * @return <code>true</code> when all parameters are equal to the properties
     *         of this event, <code>false</code> otherwise.
     */
    public boolean equalTo(State<E, C> prev, E ev, State<E, C> next) {
      return previousState.equals(prev) && event.equals(ev)
          && newState.equals(next);
    }

    @Override
    public String toString() {
      return new StringBuilder("[Event ").append(getEventType()).append(" ")
          .append(previousState).append(" + ").append(event).append(CONN)
          .append(newState).append("]").toString();
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(previousState, event, newState);
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (other == null) {
        return false;
      }
      if (this == other) {
        return true;
      }
      if (!(other instanceof StateTransitionEvent<?, ?>)) {
        return false;
      }
      @SuppressWarnings("unchecked")
      final StateTransitionEvent<E, C> ev = (StateTransitionEvent<E, C>) other;
      return Objects.equal(previousState, ev.previousState) &&
          Objects.equal(newState, ev.newState) &&
          Objects.equal(event, ev.event) &&
          Objects.equal(eventType, ev.eventType) &&
          Objects.equal(getIssuer(), ev.getIssuer());
    }
  }
}
