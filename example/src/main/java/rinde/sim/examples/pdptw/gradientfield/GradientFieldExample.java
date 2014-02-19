/**
 * 
 */
package rinde.sim.examples.pdptw.gradientfield;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import rinde.sim.core.Simulator;
import rinde.sim.pdptw.common.DefaultDepot;
import rinde.sim.pdptw.common.RouteRenderer;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.scenario.ScenarioController.UICreator;
import rinde.sim.ui.View;
import rinde.sim.ui.renderers.PDPModelRenderer;
import rinde.sim.ui.renderers.PlaneRoadModelRenderer;
import rinde.sim.ui.renderers.RoadUserRenderer;
import rinde.sim.ui.renderers.UiSchema;

/**
 * @author David Merckx
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class GradientFieldExample {

  public static void main(String[] args) {

    final UICreator uic = new UICreator() {
      @Override
      public void createUI(Simulator sim) {
        final UiSchema schema = new UiSchema(false);
        schema.add(Truck.class, "/graphics/perspective/bus-44.png");
        schema.add(DefaultDepot.class, "/graphics/flat/warehouse-32.png");
        schema.add(GFParcel.class, "/graphics/flat/hailing-cab-32.png");
        View.create(sim)
            .with(
                new PlaneRoadModelRenderer(),
                new RoadUserRenderer(schema, false),
                new RouteRenderer(),
                new GradientFieldRenderer(),
                new PDPModelRenderer(false)
            ).show();
      }
    };

    final Gendreau06Scenario scenario = Gendreau06Parser
        .parse(new BufferedReader(new InputStreamReader(
            GradientFieldExample.class
                .getResourceAsStream("/data/gendreau06/req_rapide_1_240_24"))),
            "req_rapide_1_240_24", 10, 1000, true);

    final Gendreau06ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();
    Experiment
        .build(objFunc)
        .withRandomSeed(123)
        .addConfiguration(new GradientFieldConfiguration())
        .addScenario(scenario)
        .showGui(uic)
        .repeat(1)
        .perform();
  }
}
