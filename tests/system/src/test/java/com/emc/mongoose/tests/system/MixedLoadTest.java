package com.emc.mongoose.tests.system;

import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.common.env.PathUtil;
import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.run.scenario.JsonScenario;
import com.emc.mongoose.tests.system.base.EnvConfiguredScenarioTestBase;
import com.emc.mongoose.tests.system.util.DirWithManyFilesDeleter;
import com.emc.mongoose.tests.system.util.OpenFilesCounter;
import com.emc.mongoose.tests.system.util.PortListener;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.appenders.LoadJobLogFileManager;
import static com.emc.mongoose.common.Constants.KEY_STEP_NAME;
import static com.emc.mongoose.common.env.PathUtil.getBaseDir;
import static com.emc.mongoose.run.scenario.Scenario.DIR_SCENARIO;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 Created by kurila on 23.03.17.
 Covered Use Cases:
 * 2.1.1.1.3. Intermediate Size Data Items (100KB-10MB)
 * 2.3.2. Items Output File
 * 2.3.3.1. Constant Items Destination Path
 * 4.3. Medium Concurrency Level (11-100)
 * 5. Circularity
 * 6.2.5. Limit Load Job by Time
 * 7.1. Metrics Periodic Reporting
 * 8.2.1. Create New Items
 * 8.3.1. Read With Disabled Validation
 * 9.1. Scenarios Syntax
 * 9.4.1. Override Default Configuration in the Scenario
 * 9.4.3. Reusing The Items in the Scenario
 * 9.5.3. Precondition Load Job
 * 9.5.7.1. Separate Configuration in the Mixed Load Job
 * 10.1.2. Many Local Separate Storage Driver Services (at different ports)
 */

public class MixedLoadTest
extends EnvConfiguredScenarioTestBase {

	static {
		EXCLUDE_PARAMS.put(
			KEY_ENV_ITEM_DATA_SIZE,
			Arrays.asList(new SizeInBytes("100MB"), new SizeInBytes("10GB"))
		);
		STEP_NAME = MixedLoadTest.class.getSimpleName();
		SCENARIO_PATH = Paths.get(
			getBaseDir(), DIR_SCENARIO, "systest", "MixedLoad.json"
		);
	}

	private static boolean FINISHED_IN_TIME;
	private static String STD_OUTPUT;
	private static int ACTUAL_CONCURRENCY;
	private static String ITEM_OUTPUT_PATH;
	
	@BeforeClass
	public static void setUpClass()
	throws Exception {
		ThreadContext.put(KEY_STEP_NAME, STEP_NAME);
		CONFIG_ARGS.add("--storage-net-http-namespace=ns1");
		EnvConfiguredScenarioTestBase.setUpClass();
		if(EXCLUDE_FLAG) {
			return;
		}
		if(STORAGE_DRIVER_TYPE.equals(STORAGE_TYPE_FS)) {
			ITEM_OUTPUT_PATH = Paths.get(
				Paths.get(PathUtil.getBaseDir()).getParent().toString(), STEP_NAME
			).toString();
			CONFIG.getItemConfig().getOutputConfig().setPath(ITEM_OUTPUT_PATH);
		}
		SCENARIO = new JsonScenario(CONFIG, SCENARIO_PATH.toFile());
		final Thread runner = new Thread(
			() -> {
				try {
					SCENARIO.run();
				} catch(final Throwable t) {
					LogUtil.exception(Level.ERROR, t, "Failed to run the scenario");
				}
			}
		);
		runner.start();
		TimeUnit.SECONDS.sleep(10);
		STD_OUT_STREAM.startRecording();
		TimeUnit.SECONDS.sleep(10);

		switch(STORAGE_DRIVER_TYPE) {
			case STORAGE_TYPE_FS:
				ACTUAL_CONCURRENCY = OpenFilesCounter.getOpenFilesCount(ITEM_OUTPUT_PATH);
				break;
			case STORAGE_TYPE_ATMOS:
			case STORAGE_TYPE_S3:
			case STORAGE_TYPE_SWIFT:
				final int startPort = CONFIG.getStorageConfig().getNetConfig().getNodeConfig().getPort();
				for(int i = 0; i < HTTP_STORAGE_NODE_COUNT; i ++) {
					ACTUAL_CONCURRENCY += PortListener
						.getCountConnectionsOnPort("127.0.0.1:" + (startPort + i));
				}
				break;
		}

		TimeUnit.SECONDS.timedJoin(runner, 50);
		FINISHED_IN_TIME = !runner.isAlive();
		runner.interrupt();
		STD_OUTPUT = STD_OUT_STREAM.stopRecordingAndGet();
		LoadJobLogFileManager.flushAll();
		TimeUnit.SECONDS.sleep(10);
	}
	
	@AfterClass
	public static void tearDownClass()
	throws Exception {
		if(!EXCLUDE_FLAG) {
			if(STORAGE_DRIVER_TYPE.equals(STORAGE_TYPE_FS)) {
				try {
					DirWithManyFilesDeleter.deleteExternal(ITEM_OUTPUT_PATH);
				} catch(final Exception e) {
					e.printStackTrace(System.err);
				}
			}
		}
		EnvConfiguredScenarioTestBase.tearDownClass();
	}
	
	@Test
	public void testFinishedInTime() {
		assumeFalse(EXCLUDE_FLAG);
		assertTrue("Scenario didn't finished in time", FINISHED_IN_TIME);
	}

	@Test
	public void testActualConcurrency() {
		assumeFalse(EXCLUDE_FLAG);
		assertEquals(2 * STORAGE_DRIVERS_COUNT * CONCURRENCY, ACTUAL_CONCURRENCY, 5);
	}
	
	@Test
	public void testMetricsStdout()
	throws Exception {
		assumeFalse(EXCLUDE_FLAG);
		final Map<IoType, Integer> concurrencyMap = new HashMap<>();
		concurrencyMap.put(IoType.CREATE, CONCURRENCY);
		concurrencyMap.put(IoType.READ, CONCURRENCY);
		testMetricsTableStdout(STD_OUTPUT, STEP_NAME, STORAGE_DRIVERS_COUNT, 0, concurrencyMap);
	}
}
