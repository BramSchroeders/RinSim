/**
 * 
 */
package rinde.sim.ui.renderers;

import org.eclipse.swt.graphics.GC;

import rinde.sim.core.graph.Point;
import rinde.sim.core.model.road.PlaneRoadModel;

/**
 * @author Rinde van Lon (rinde.vanlon@cs.kuleuven.be)
 * 
 */
public class PlaneRoadModelRenderer implements ModelRenderer<PlaneRoadModel> {

	protected PlaneRoadModel rm;
	protected final int margin;

	public PlaneRoadModelRenderer() {
		this(0);
	}

	public PlaneRoadModelRenderer(int pMargin) {
		margin = pMargin;
	}

	@Override
	public void renderStatic(GC gc, ViewPort vp) {
		int xMin = vp.toCoordX(rm.min.x) - margin;
		int yMin = vp.toCoordY(rm.min.y) - margin;
		int xMax = vp.toCoordX(rm.max.x) + margin;
		int yMax = vp.toCoordY(rm.max.y) + margin;
		gc.drawRectangle(xMin, yMin, xMax - xMin, yMax - yMin);
	}

	@Override
	public void renderDynamic(GC gc, ViewPort vp) {}

	@Override
	public ViewRect getViewRect() {

		return new ViewRect(new Point(rm.min.x - margin, rm.min.y - margin), new Point(rm.max.x + margin, rm.max.y
				+ margin));
	}

	@Override
	public void register(PlaneRoadModel model) {
		rm = model;
	}

	@Override
	public Class<PlaneRoadModel> getSupportedModelType() {
		return PlaneRoadModel.class;
	}

}
