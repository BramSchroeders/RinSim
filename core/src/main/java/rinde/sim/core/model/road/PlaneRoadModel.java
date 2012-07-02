/**
 * 
 */
package rinde.sim.core.model.road;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.min;
import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Point;
import rinde.sim.util.SpeedConverter;
import rinde.sim.util.TimeUnit;

/**
 * A {@link RoadModel} that uses a plane as road structure. This assumes that
 * from every point in the plane it is possible to drive to every other point in
 * the plane. The plane has a boundary as defined by a rectangle.
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class PlaneRoadModel extends AbstractRoadModel<Point> {

    /**
     * The minimum travelable distance.
     */
    // TODO should this be dynamic?
    protected static final double DELTA = 0.000001;

    /**
     * The minimum x and y of the plane.
     */
    public final Point min;
    /**
     * The maximum x and y of the plane.
     */
    public final Point max;
    /**
     * The width of the plane.
     */
    public final double width;
    /**
     * The height of the plane.
     */
    public final double height;
    /**
     * The maximum speed that objects can travel on the plane.
     */
    public final double maxSpeed;

    /**
     * Create a new plane road model using the specified boundaries and max
     * speed.
     * @param pMin The minimum x and y of the plane.
     * @param pMax The maximum x and y of the plane.
     * @param pMaxSpeed The maximum speed that objects can travel on the plane.
     */
    public PlaneRoadModel(Point pMin, Point pMax, double pMaxSpeed) {
        checkArgument(pMin.x < pMax.x && pMin.y < pMax.y, "min should have coordinates smaller than max");
        checkArgument(pMaxSpeed > 0, "max speed must be positive");
        min = pMin;
        max = pMax;
        width = max.x - min.x;
        height = max.y - min.y;
        maxSpeed = pMaxSpeed;
    }

    @Override
    public Point getRandomPosition(RandomGenerator rnd) {
        return new Point(min.x + (rnd.nextDouble() * width), min.y
                + (rnd.nextDouble() * height));
    }

    @Override
    public void addObjectAt(RoadUser obj, Point pos) {
        checkArgument(isPointInBoundary(pos));
        super.addObjectAt(obj, pos);
    }

    @Override
    protected PathProgress doFollowPath(MovingRoadUser object,
            Queue<Point> path, TimeLapse time) {
        Point loc = objLocs.get(object);

        double traveled = 0;
        final SpeedConverter sc = new SpeedConverter();
        // speed in graph units per hour -> converting to milliseconds
        double speed = min(object.getSpeed(), maxSpeed);
        speed = sc.from(speed, TimeUnit.H).to(TimeUnit.MS);

        final List<Point> travelledNodes = new ArrayList<Point>();
        while (time.hasTimeLeft() && path.size() > 0) {
            checkArgument(isPointInBoundary(path.peek()), "points in the path must be within the predefined boundary of the plane");

            // distance that can be traveled with timeleft
            final double travelDistance = speed * time.getTimeLeft();
            final double stepLength = Point.distance(loc, path.peek());
            final double perc = travelDistance / stepLength;

            if (perc + DELTA >= 1) {
                loc = path.remove();
                travelledNodes.add(loc);
                time.consume(Math.round(stepLength / speed));
                traveled += stepLength;
            } else {
                final Point diff = Point.diff(path.peek(), loc);
                loc = new Point(loc.x + perc * diff.x, loc.y + perc * diff.y);
                time.consume(Math.round(travelDistance / speed));
                traveled += travelDistance;
            }
        }
        objLocs.put(object, loc);
        return new PathProgress(traveled, time.getTimeConsumed(),
                travelledNodes);
    }

    @Override
    public List<Point> getShortestPathTo(Point from, Point to) {
        checkArgument(isPointInBoundary(from), "from must be within the predefined boundary of the plane");
        checkArgument(isPointInBoundary(to), "to must be within the predefined boundary of the plane");
        return asList(from, to);
    }

    @Override
    protected Point locObj2point(Point locObj) {
        return locObj;
    }

    @Override
    protected Point point2LocObj(Point point) {
        return point;
    }

    /**
     * Checks whether the specified point is within the plane as defined by this
     * model.
     * @param p The point to check.
     * @return <code>true</code> if the points is within the boundary,
     *         <code>false</code> otherwise.
     */
    protected boolean isPointInBoundary(Point p) {
        return p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y;
    }

}
