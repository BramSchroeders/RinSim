package rinde.sim.examples.rwalk4;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;

import rinde.sim.core.graph.Point;
import rinde.sim.core.model.RoadModel;
import rinde.sim.core.model.RoadUser;
import rinde.sim.ui.renderers.Renderer;
import rinde.sim.ui.renderers.UiSchema;

public class MessagingLayerRenderer implements Renderer {

	protected RoadModel rs;
	protected boolean useEncirclement;
	private UiSchema uiSchema;
	
	public MessagingLayerRenderer(RoadModel rs,
			UiSchema uiSchema) {
		this.rs = rs;
		this.uiSchema = uiSchema;
	}

	@Override
	public void render(GC gc, double xOrigin, double yOrigin, double minX,
			double minY, double scale) {
		final int size = 4;
		uiSchema.initialize();
		
		

		Set<RandomWalkAgent> objects = rs.getObjectsOfType(RandomWalkAgent.class);
		
		synchronized (objects) {
			for (RandomWalkAgent a : objects) {
				Point p = a.getPosition();
				if(p == null) continue;
				final int x = (int) (xOrigin + (p.x - minX) * scale);
				final int y = (int) (yOrigin + (p.y - minY) * scale);
				
				final int radius = (int) (a.getRadius() * scale);
				
				gc.setForeground(uiSchema.getColor(RandomWalkAgent.class));
				gc.setBackground(uiSchema.getColor(RandomWalkAgent.class));
				
				gc.fillOval(x-size, y-size,  size*2, size*2);
				
				gc.drawOval(x-radius, y-radius, radius*2, radius*2);
				
				Set<RandomWalkAgent> communicatedWith = a.getCommunicatedWith();
				for (RandomWalkAgent cw : communicatedWith) {
					p = cw.getPosition();
					if(p == null) continue;
					final int xCW = (int) (xOrigin + (p.x - minX) * scale);
					final int yCW = (int) (yOrigin + (p.y - minY) * scale);
					gc.drawLine(x, y, xCW, yCW);
				}
			}
		}
	}

}
