package za.ac.sun.cs.green.util;

import org.junit.Test;
import za.ac.sun.cs.green.Green;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class SetTaskManagerTest {

//	private void load(Properties properties, String filename) throws FileNotFoundException, IOException {
//		properties.load(new FileInputStream(filename));
//	}

	@Test
	public void test() {
		Green solver = new Green();
		Properties props = new Properties();
		props.setProperty("green.taskmanager", DummyTaskManager.class.getCanonicalName());
		Configuration config = new Configuration(solver, props);
		config.configure();
		assertEquals(DummyTaskManager.class, solver.getTaskManager().getClass());
	}

}
