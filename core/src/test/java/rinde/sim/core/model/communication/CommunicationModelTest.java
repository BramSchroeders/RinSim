package rinde.sim.core.model.communication;

import static org.junit.Assert.*;

import java.util.Random;

import org.apache.commons.math.random.MersenneTwister;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import rinde.sim.core.graph.Point;

public class CommunicationModelTest {

	private CommunicationModel model;
	
	@Before
	public void setUp() throws Exception {
		model = new CommunicationModel(new MersenneTwister(123));
	}

	@Test
	public void testRegister() {
		
		TestCommunicationUser user = new TestCommunicationUser(new Point(0,10), 10, 1, null);
		boolean register = model.register(user);
		assertTrue(register);
		assertTrue(model.users.contains(user));
	}
	
	@Test
	public void testRegisterException() {
		
		TestCommunicationUser user = new TestCommunicationUser(new Point(0,10), 10, 1, null) {

			@Override
			public void setCommunicationAPI(CommunicationAPI api) {
				throw new RuntimeException();
			}
			
		};
		boolean register = model.register(user);
		assertFalse(register);
		assertFalse(model.users.contains(user));
	}

	@Test
	public void testUnregister() {
		TestCommunicationUser user = new TestCommunicationUser(new Point(0,10), 10, 1, null);
		boolean res = model.register(user);
		assertTrue(res);
		assertTrue(model.users.contains(user));
		res = model.unregister(user);
		
		assertTrue(res);
		assertTrue(model.users.isEmpty());
	}
	
	@Test
	public void testSimpleSend() {
		final boolean[] res = new boolean[1];
		
		TestCommunicationUser sender = new TestCommunicationUser(new Point(0,10), 10, 1, null);
		TestCommunicationUser recipient = new TestCommunicationUser(new Point(0,10), 10, 1, new Callback() {
			
			@Override
			void callBack(Message m) {
				res[0] = true;
			}
		});
		
		model.register(sender);
		model.register(recipient);
		
		model.send(recipient, new Message(sender) {
		});
		
		assertFalse(res[0]);
		assertEquals(1, model.sendQueue.size());
		
		model.tick(0, 100);
		
		assertFalse(res[0]);
		assertEquals(1, model.sendQueue.size());
		
		model.afterTick(0, 100);
		
		assertTrue(res[0]);
		assertEquals(0, model.sendQueue.size());
	}
	
	
	@Test
	public void testMaxDistanceSend() {
		final boolean[] res = new boolean[1];
		
		TestCommunicationUser sender = new TestCommunicationUser(new Point(0,0), 10, 1, null);
		TestCommunicationUser recipient = new TestCommunicationUser(new Point(0,5), 5, 1, new Callback() {
			
			@Override
			void callBack(Message m) {
				res[0] = true;
			}
		});
		
		model.register(sender);
		model.register(recipient);
		
		model.send(recipient, new Message(sender) {
		});
		
		assertFalse(res[0]);
		assertEquals(1, model.sendQueue.size());
		
		
		model.afterTick(0, 100);
		
		assertTrue(res[0]);
		assertEquals(0, model.sendQueue.size());
	}
	
	@Test
	public void testUnsendSend() {
		final boolean[] res = new boolean[1];
		
		
		//the distance is greater than min radius
		TestCommunicationUser sender = new TestCommunicationUser(new Point(0,0), 10, 1, null);
		TestCommunicationUser recipient = new TestCommunicationUser(new Point(0,5), 4, 1, new Callback() {
			
			@Override
			void callBack(Message m) {
				res[0] = true;
			}
		});
		
		model.register(sender);
		model.register(recipient);
		
		model.send(recipient, new Message(sender) {
		});
		
		assertFalse(res[0]);
		assertEquals(0, model.sendQueue.size());
		
		
		model.afterTick(0, 100);
		
		assertFalse(res[0]);
		assertEquals(0, model.sendQueue.size());
	}
	
	/**
	 * unregister recipient
	 */
	@Test
	public void testUnsendOnUnregister() {
		final boolean[] res = new boolean[1];
		
		
		//the distance is greater than min radius
		TestCommunicationUser sender = new TestCommunicationUser(new Point(0,0), 10, 1, null);
		TestCommunicationUser recipient = new TestCommunicationUser(new Point(0,5), 15, 1, new Callback() {
			
			@Override
			void callBack(Message m) {
				res[0] = true;
			}
		});
		
		model.register(sender);
		model.register(recipient);
		
		model.send(recipient, new Message(sender) {
		});
		
		assertFalse(res[0]);
		assertEquals(1, model.sendQueue.size());
		
		model.unregister(recipient);
		
		assertEquals(0, model.sendQueue.size());
		
		model.afterTick(0, 100);
		
		assertFalse(res[0]);
		assertEquals(0, model.sendQueue.size());
	}
	
	/**
	 * unregister sender
	 */
	@Test
	public void testUnsendOnUnregister2() {
		final boolean[] res = new boolean[1];
		
		
		//the distance is greater than min radius
		TestCommunicationUser sender = new TestCommunicationUser(new Point(0,0), 10, 1, null);
		TestCommunicationUser recipient = new TestCommunicationUser(new Point(0,5), 15, 1, new Callback() {
			
			@Override
			void callBack(Message m) {
				res[0] = true;
			}
		});
		
		model.register(sender);
		model.register(recipient);
		
		model.send(recipient, new Message(sender) {
		});
		
		assertFalse(res[0]);
		assertEquals(1, model.sendQueue.size());
		
		model.unregister(sender);
		
		assertEquals(0, model.sendQueue.size());
		
		model.afterTick(0, 100);
		
		assertFalse(res[0]);
		assertEquals(0, model.sendQueue.size());
	}
	
	@Test
	public void broadCastPerformanceTest() {
		Random r = new Random();
		for(int i = 0; i < 10000; ++i) {
			TestCommunicationUser t = new TestCommunicationUser(new Point(r.nextDouble() * 100, r.nextDouble() * 100), r.nextDouble() * 100, 1, null);
			model.register(t);
		}
		TestCommunicationUser sender = new TestCommunicationUser(new Point(r.nextDouble() * 100, r.nextDouble() * 100), 200, 1, null);
		model.register(sender);
		long time = System.currentTimeMillis();
		model.broadcast(new Message(sender) {});
		model.afterTick(0, 100);
		time = System.currentTimeMillis() - time;
		System.err.println(time);
		assertTrue(time  < 120);
		
	}
	

	@Test
	public void testGetSupportedType() {
		assertEquals(CommunicationUser.class, model.getSupportedType());
	}

	@Test
	public void testBroadcastMessage() {
		final boolean[] res = new boolean[1];
		
		TestCommunicationUser sender = new TestCommunicationUser(new Point(0,0), 10, 1, null);
		TestCommunicationUser recipient = new TestCommunicationUser(new Point(0,5), 5, 1, new Callback() {
			
			@Override
			void callBack(Message m) {
				res[0] = true;
			}
		});
		TestCommunicationUser recipient2 = new TestCommunicationUser(new Point(0,5), 15, 1, null);
		
		model.register(sender);
		model.register(recipient);
		model.register(recipient2);
		
		model.broadcast(new Message(sender) {
		});
		
		assertEquals(3, model.users.size());
		
		assertFalse(res[0]);
		assertEquals(2, model.sendQueue.size());
		
		
		model.afterTick(0, 100);
		
		assertTrue(res[0]);
		assertEquals(0, model.sendQueue.size());
	}

	
	class TestCommunicationUser implements CommunicationUser {

		Point position;
		double radius;
		double reliability;
		Callback callback;
		
		
		
		public TestCommunicationUser(Point position, double radius,
				double reliability, Callback c) {
			this.position = position;
			this.radius = radius;
			this.reliability = reliability;
			this.callback = c;
		}

		@Override
		public void setCommunicationAPI(CommunicationAPI api) {
			
		}

		@Override
		public Point getPosition() {
			
			return position;
		}

		@Override
		public double getRadius() {
			return radius;
		}

		@Override
		public double getReliability() {
			return reliability;
		}

		@Override
		public void receive(Message message) {
			if(callback != null) callback.callBack(message);
		}
	}
	
	abstract class Callback {
		abstract void callBack(Message m);
	}
}
