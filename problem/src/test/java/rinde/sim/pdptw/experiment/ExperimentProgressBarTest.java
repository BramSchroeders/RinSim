package rinde.sim.pdptw.experiment;

import org.junit.Test;

public class ExperimentProgressBarTest {

  @SuppressWarnings("null")
  @Test
  public void test() {
    final ExperimentProgressBar pb = new ExperimentProgressBar();
    pb.startComputing(30);
    for (int i = 0; i < 30; i++) {
      try {
        Thread.sleep(50);
      } catch (final InterruptedException e) {
        throw new IllegalStateException();
      }
      pb.receive(null);
    }
    pb.doneComputing();
  }

}
