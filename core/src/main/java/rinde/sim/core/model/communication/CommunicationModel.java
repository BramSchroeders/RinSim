package rinde.sim.core.model.communication;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rinde.sim.core.graph.Point;
import rinde.sim.core.model.SimpleModel;
import rinde.sim.core.model.road.RoadUser;
import rinde.sim.core.model.time.TickListener;
import rinde.sim.core.model.time.TimeLapse;

import com.google.common.base.Predicate;

/**
 * The communication model. Messages are send at the end of a current tick.
 * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be>
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * @since 2.0
 */
public class CommunicationModel extends SimpleModel<CommunicationUser>
        implements TickListener, CommunicationAPI {

    protected static final Logger LOGGER = LoggerFactory
            .getLogger(CommunicationModel.class);

    protected final Set<CommunicationUser> users;
    protected List<Entry<CommunicationUser, Message>> sendQueue;
    protected RandomGenerator generator;
    private final boolean ignoreDistances;

    /**
     * Constructs the communication model.
     * @param pGenerator the random number generator that is used for
     *            reliability computations
     * @param pIgnoreDistances when <code>true</code> the distances constrains
     *            are ignored.
     */
    public CommunicationModel(RandomGenerator pGenerator,
            boolean pIgnoreDistances) {
        super(CommunicationUser.class);
        checkArgument(pGenerator != null, "generator can not be null");
        users = new LinkedHashSet<CommunicationUser>();
        sendQueue = new LinkedList<Entry<CommunicationUser, Message>>();
        generator = pGenerator;
        ignoreDistances = pIgnoreDistances;
    }

    /**
     * Construct the communication model that respects the distance constrains
     * @param pGenerator the random number generator that is used for
     *            reliability computations
     */
    public CommunicationModel(RandomGenerator pGenerator) {
        this(pGenerator, false);
    }

    /**
     * Register communication user {@link CommunicationUser}. Communication user
     * is registered only when it is also {@link RoadUser}. This is required as
     * communication model depends on elements positions.
     */
    @Override
    public boolean register(CommunicationUser element) {
        if (element == null) {
            throw new IllegalArgumentException("element can not be null");
        }
        final boolean result = users.add(element);
        if (!result) {
            return false;
        }
        // callback
        try {
            element.setCommunicationAPI(this);
        } catch (final Exception e) {
            // if you miss-behave you don't deserve to use our infrastructure :D
            LOGGER.warn("callback for the communication user failed. Unregistering", e);
            users.remove(element);
            return false;
        }
        return true;
    }

    @Override
    public boolean unregister(CommunicationUser element) {
        if (element == null) {
            return false;
        }
        final List<Entry<CommunicationUser, Message>> toRemove = new LinkedList<Entry<CommunicationUser, Message>>();
        for (final Entry<CommunicationUser, Message> e : sendQueue) {
            if (element.equals(e.getKey())
                    || element.equals(e.getValue().getSender())) {
                toRemove.add(e);
            }
        }
        sendQueue.removeAll(toRemove);

        return users.remove(element);
    }

    @Override
    public void tick(TimeLapse tl) {
        // empty implementation
    }

    @Override
    public void afterTick(TimeLapse tl) {
        long timeMillis = System.currentTimeMillis();
        final List<Entry<CommunicationUser, Message>> cache = sendQueue;
        sendQueue = new LinkedList<Entry<CommunicationUser, Message>>();
        for (final Entry<CommunicationUser, Message> e : cache) {
            try {
                e.getKey().receive(e.getValue());
                // TODO [bm] add msg delivered event
            } catch (final Exception e1) {
                LOGGER.warn("unexpected exception while passing message", e1);
            }
        }
        if (LOGGER.isDebugEnabled()) {
            timeMillis = (System.currentTimeMillis() - timeMillis);
            LOGGER.debug("broadcast lasted for:" + timeMillis);
        }
    }

    @Override
    public void send(CommunicationUser recipient, Message message) {
        if (!users.contains(recipient)) {
            // TODO [bm] implement dropped message EVENT
            return;
        }

        if (new CanCommunicate(message.sender).apply(recipient)) {
            sendQueue.add(SimpleEntry.entry(recipient, message));
        } else {
            // TODO [bm] implement dropped message EVENT
            return;
        }

    }

    @Override
    public void broadcast(Message message) {
        broadcast(message, new CanCommunicate(message.sender));
    }

    @Override
    public void broadcast(Message message,
            Class<? extends CommunicationUser> type) {
        broadcast(message, new CanCommunicate(message.sender, type));

    }

    private void broadcast(Message message,
            Predicate<CommunicationUser> predicate) {
        if (!users.contains(message.sender)) {
            return;
        }
        final HashSet<CommunicationUser> uSet = new HashSet<CommunicationUser>(
                users.size() / 2);

        for (final CommunicationUser u : users) {
            if (predicate.apply(u)) {
                uSet.add(u);
            }
        }

        for (final CommunicationUser u : uSet) {
            try {
                sendQueue.add(SimpleEntry.entry(u, message.clone()));
            } catch (final CloneNotSupportedException e) {
                LOGGER.error("clonning exception for message", e);
            }
        }
    }

    /**
     * Check if an message from a given sender can be deliver to recipient
     * @see CanCommunicate#apply(CommunicationUser)
     * @author Bartosz Michalik <bartosz.michalik@cs.kuleuven.be>
     * @since 2.0
     */
    class CanCommunicate implements Predicate<CommunicationUser> {

        @Nullable
        private final Class<? extends CommunicationUser> clazz;
        private final CommunicationUser sender;
        @Nullable
        private Rectangle rec;

        public CanCommunicate(CommunicationUser sender,
                @Nullable Class<? extends CommunicationUser> clazz) {
            this.sender = sender;
            this.clazz = clazz;
            if (sender.getPosition() != null) {
                rec = new Rectangle(sender.getPosition(), sender.getRadius());
            }
        }

        public CanCommunicate(CommunicationUser sender) {
            this(sender, null);
        }

        @Override
        public boolean apply(CommunicationUser input) {
            if (input == null || rec == null) {
                return false;
            }
            if (clazz != null && !clazz.equals(input.getClass())) {
                return false;
            }
            if (input.equals(sender)) {
                return false;
            }
            final Point iPos = input.getPosition();
            if (!ignoreDistances && !rec.contains(iPos)) {
                return false;
            }
            final double prob = input.getReliability()
                    * sender.getReliability();
            final double minRadius = Math.min(input.getRadius(), sender
                    .getRadius());
            final double rand = generator.nextDouble();
            final Point sPos = sender.getPosition();
            return prob > rand
                    && (ignoreDistances ? true
                            : Point.distance(sPos, iPos) <= minRadius);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return super.equals(o);
        }
    }

    private static class Rectangle {
        private final double y1;
        private final double x1;
        private final double y0;
        private final double x0;

        public Rectangle(Point p, double radius) {
            x0 = p.x - radius;
            y0 = p.y - radius;
            x1 = p.x + radius;
            y1 = p.y + radius;
        }

        public boolean contains(Point p) {
            if (p == null) {
                return false;
            }
            if (p.x < x0 || p.x > x1) {
                return false;
            }
            if (p.y < y0 || p.y > y1) {
                return false;
            }
            return true;
        }
    }

    protected static class SimpleEntry<K, V> implements Entry<K, V> {
        @Nullable
        private final V value;
        private final K key;

        public SimpleEntry(K key, @Nullable V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Nullable
        @Override
        public V getValue() {
            return value;
        }

        @Nullable
        @Override
        public V setValue(V value) {
            return null;
        }

        static <V, K> Entry<V, K> entry(V v, K k) {
            return new SimpleEntry<V, K>(v, k);
        }

    }
}
