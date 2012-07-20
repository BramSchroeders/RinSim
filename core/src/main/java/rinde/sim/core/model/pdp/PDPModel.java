/**
 * 
 */
package rinde.sim.core.model.pdp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.unmodifiableCollection;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import rinde.sim.core.TimeLapse;
import rinde.sim.core.model.Model;
import rinde.sim.core.model.road.RoadModel;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * 
 * 
 * Assumptions of the model, any truck can pickup any (kind of) package (as long
 * as size constraints are met).
 * 
 * Currently supports three kinds of objects:
 * <ul>
 * <li> {@link Package}</li>
 * <li> {@link Truck}</li>
 * <li> {@link Depot}</li>
 * </ul>
 * 
 * A package must be in one of three locations: on a truck, in a depot or on a
 * road (roadmodel).
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class PDPModel implements Model<PDPObject> {
    protected final RoadModel roadModel;
    protected final Multimap<PackageContainer, Package> containerContents;
    protected final Map<PackageContainer, Double> containerContentsSize;
    protected final Map<PackageContainer, Double> containerCapacities;
    protected final Map<Truck, TruckState> truckState;
    protected final Map<Package, PackageState> packageState;

    protected final Map<Truck, Action> truckActions;

    protected final Set<Package> knownPackages;

    enum PackageState {
        LOADING, UNLOADING, AVAILABLE, IN_CARGO, DELIVERED
    }

    enum TruckState {
        IDLE, LOADING, UNLOADING
    }

    // TODO what if we give all model actions default visibility? and let them
    // be accesible only through the abstract class

    // TODO where to store pickup and delivery processing times?

    // a Package must always be either in the PDPModel or in the RoadModel not
    // both! if it exists in neither it doesn't exist at all.

    public PDPModel(RoadModel rm) {
        roadModel = rm;
        containerContents = HashMultimap.create();
        containerContentsSize = newHashMap();
        containerCapacities = newHashMap();
        knownPackages = newHashSet();
        truckActions = newHashMap();
        truckState = newHashMap();
        packageState = newHashMap();
    }

    // protected Table<Truck>

    public Collection<Package> getContents(PackageContainer truck) {
        checkArgument(containerCapacities.containsKey(truck));
        return unmodifiableCollection(containerContents.get(truck));
    }

    public double getContentsSize(PackageContainer truck) {
        return containerContentsSize.get(truck);
    }

    // public void getPack

    /**
     * {@link Truck} <code>t</code> attempts to pickup {@link Package}
     * <code>p</code>.
     * @param t
     * @param p
     */
    public void pickup(Truck t, Package p, TimeLapse time) {

        // TODO add event
        // TODO package.isPickupAllowedBy(truck)
        // TODO truck.canPickup(package)

        /* 1 */checkArgument(time.hasTimeLeft(), "there must be time available to perform the action");
        /* 2 */checkArgument(roadModel.containsObject(t), "truck does not exist in RoadModel");
        /* 3 */checkArgument(roadModel.containsObject(p), "package does not exist in RoadModel");
        /* 4 */checkArgument(packageState.get(p) == PackageState.AVAILABLE, "package must be registered and must be available");
        /* 5 */checkArgument(truckState.get(t) == TruckState.IDLE, "truck must be registered and must be available");
        /* 6 */checkArgument(roadModel.equalPosition(t, p), "truck must be at the same location as the package it wishes to pickup");
        final double newSize = containerContentsSize.get(t) + p.getMagnitude();
        /* 7 */checkArgument(newSize <= containerCapacities.get(t), "package does not fit in truck");

        // remove the package such that it can no longer be attempted to be
        // picked up by anyone else
        roadModel.removeObject(p);
        // in this case we know we cannot finish this action with the available
        // time. We must continue in the next tick.
        if (time.getTimeLeft() < p.getLoadingDuration()) {
            truckState.put(t, TruckState.LOADING);
            packageState.put(p, PackageState.LOADING);

            truckActions.put(t, new PickupAction(this, t, p, p
                    .getLoadingDuration() - time.getTimeLeft()));
            time.consumeAll();
        } else {
            time.consume(p.getLoadingDuration());
            doPickup(t, p);
        }
    }

    protected void doPickup(Truck t, Package p) {
        containerContents.put(t, p);
        containerContentsSize.put(t, containerContentsSize.get(t)
                + p.getMagnitude());

        packageState.put(p, PackageState.IN_CARGO);
    }

    // should deliver (put down) the package p that truck t is carrying.
    // TODO what to do with the time that is needed for delivery?
    // TODO check for constraints, can a package just be put down everywhere? or
    // only at Depots? is this a global constraint? or only at its predefined
    // destination?
    // public void deliver(Truck t, Package p, TimeLapse time) {

    // partial delivery?
    // what if we didn't have time to completely deliver? we should enforce
    // continuing the delivery in the next tick
    // how to avoid that the truck moves in the mean time? listening to move
    // events in the RoadModel? intercepting TimeLapses in the Truck? (could
    // be done by making an abstract class). the state should be saved
    // somewhere, in the model? in the truck?

    // TODO package.isDeliveryAllowedAt(currentPos)
    // TODO truck.canDeliverAt(currentPos / currentDepot)
    // TODO some of these constraints might be too much for a basic
    // implementation, if needed they should be implemented in an overriding
    // subclass
    // }

    public void deliver(Truck t, Package p, TimeLapse time) {
        /* 1 */checkArgument(time.hasTimeLeft(), "there must be time available to perform the action");
        /* 2 */checkArgument(roadModel.containsObject(t), "truck does not exist in RoadModel");
        /* 3 */checkArgument(truckState.get(t).equals(TruckState.IDLE), "truck must be idle");
        /* 4 */checkArgument(containerContents.get(t).contains(p), "truck does not contain package");
        /* 5 */checkArgument(p.getDestination()
                .equals(roadModel.getPosition(t)), "package must be delivered at its destination, truck should move there first");

        if (time.getTimeLeft() < p.getUnloadingDuration()) {
            truckState.put(t, TruckState.UNLOADING);
            packageState.put(p, PackageState.UNLOADING);
            truckActions.put(t, new DeliverAction(this, t, p, p
                    .getUnloadingDuration() - time.getTimeLeft()));
            time.consumeAll();
        } else {
            time.consume(p.getUnloadingDuration());
            doDeliver(t, p);
        }
    }

    /**
     * @param truck
     * @param pack
     */
    protected void doDeliver(Truck truck, Package pack) {
        containerContents.remove(truck, pack);
        containerContentsSize.put(truck, containerContentsSize.get(truck)
                - pack.getMagnitude());

        packageState.put(pack, PackageState.DELIVERED);

    }

    /**
     * This method is intended for Packages that wish to add themselves to
     * either a Truck or a Depot.
     * @param container
     * @param p
     */
    // TODO can this method be misused for hacking the model? try to avoid!
    public void addPackageIn(PackageContainer container, Package p) {
        checkArgument(!roadModel.containsObject(p));
        checkArgument(knownPackages.contains(p));
        checkArgument(containerContents.containsKey(container));
        // TODO implement
    }

    public PackageState getPackageState(Package p) {
        return packageState.get(p);
    }

    public TruckState getTruckState(Truck t) {
        return truckState.get(t);
    }

    @Override
    public boolean register(PDPObject element) {
        if (element.getType() == PDPType.PACKAGE) {
            checkArgument(!knownPackages.contains(element));
            knownPackages.add((Package) element);
            packageState.put((Package) element, PackageState.AVAILABLE);
        } else if (element.getType() == PDPType.TRUCK
                || element.getType() == PDPType.DEPOT) {
            final PackageContainer pc = (PackageContainer) element;
            containerCapacities.put(pc, pc.getCapacity());
            containerContentsSize.put(pc, 0d);

            if (element.getType() == PDPType.TRUCK) {
                truckState.put((Truck) element, TruckState.IDLE);
            }
        }
        element.initPDPObject(this);

        return true;
    }

    @Override
    public boolean unregister(PDPObject element) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Class<PDPObject> getSupportedType() {
        return PDPObject.class;
    }

    /**
     * @param p
     * @return
     */
    public boolean truckContains(Truck t, Package p) {
        return containerContents.containsEntry(t, p);
    }

    public boolean depotContains(Depot d, Package p) {
        return containerContents.containsEntry(d, p);
    }

    /**
     * @param truck
     * @param time
     */
    protected void continuePreviousActions(Truck truck, TimeLapse time) {
        if (truckActions.containsKey(truck)) {
            final Action action = truckActions.get(truck);
            action.perform(time);
            if (action.isDone()) {
                truckActions.remove(truck);
            }
        }
    }

    interface Action {
        void perform(TimeLapse time);

        boolean isDone();
    }

    abstract class TruckPackageAction implements Action {
        protected final PDPModel modelRef;
        protected final Truck truck;
        protected final Package pack;
        protected long timeNeeded;

        public TruckPackageAction(PDPModel model, Truck t, Package p,
                long pTimeNeeded) {
            modelRef = model;
            truck = t;
            pack = p;
            timeNeeded = pTimeNeeded;
        }

        @Override
        public void perform(TimeLapse time) {
            // there is enough time to finish action
            if (time.getTimeLeft() >= timeNeeded) {
                time.consume(timeNeeded);
                timeNeeded = 0;
                finish(time);
            } else { // there is not enough time to finish action in this step
                timeNeeded -= time.getTimeLeft();
                time.consumeAll();
            }
        }

        abstract protected void finish(TimeLapse time);

        @Override
        public boolean isDone() {
            return timeNeeded == 0;
        }

        public long timeNeeded() {
            return timeNeeded;
        }
    }

    class PickupAction extends TruckPackageAction {

        public PickupAction(PDPModel model, Truck t, Package p, long pTimeNeeded) {
            super(model, t, p, pTimeNeeded);
        }

        @Override
        public void finish(TimeLapse time) {
            modelRef.truckState.put(truck, TruckState.IDLE);
            modelRef.doPickup(truck, pack);

        }
    }

    class DeliverAction extends TruckPackageAction {

        public DeliverAction(PDPModel model, Truck t, Package p,
                long pTimeNeeded) {
            super(model, t, p, pTimeNeeded);
        }

        @Override
        public void finish(TimeLapse time) {
            modelRef.truckState.put(truck, TruckState.IDLE);
            modelRef.doDeliver(truck, pack);
        }
    }

}
