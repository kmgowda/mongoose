package com.emc.mongoose.tests.system.feature.limit;

import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.tests.system.base.HttpStorageDistributedScenarioTestBase;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.appenders.LoadJobLogFileManager;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.Level;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 Created by andrey on 19.01.17.
 */
public class DistributedCreateByTimeTest
extends HttpStorageDistributedScenarioTestBase {

	private static final SizeInBytes ITEM_DATA_SIZE = new SizeInBytes("123KB");
	private static final int LOAD_LIMIT_TIME = 45;
	private static final int LOAD_CONCURRENCY = 3;

	private static boolean FINISHED_IN_TIME = true;
	private static String STD_OUTPUT = null;

	@BeforeClass public static void setUpClass()
	throws Exception {
		CONFIG_ARGS.add("--item-data-size=" + ITEM_DATA_SIZE.toString());
		CONFIG_ARGS.add("--load-limit-time=" + LOAD_LIMIT_TIME);
		CONFIG_ARGS.add("--load-concurrency=" + LOAD_CONCURRENCY);
		HttpStorageDistributedScenarioTestBase.setUpClass();
		final Thread runner = new Thread(
			() -> {
				try {
					STD_OUT_STREAM.startRecording();
					SCENARIO.run();
					STD_OUTPUT = STD_OUT_STREAM.stopRecording();
				} catch(final Throwable t) {
					LogUtil.exception(LOG, Level.ERROR, t, "Failed to run the scenario");
				}
			}
		);
		runner.start();
		try {
			TimeUnit.SECONDS.timedJoin(runner, LOAD_LIMIT_TIME + 2);
		} catch(final InterruptedException e) {
		}
		if(runner.isAlive()) {
			runner.interrupt();
			FINISHED_IN_TIME = false;
		}
		LoadJobLogFileManager.flush(JOB_NAME);
		TimeUnit.SECONDS.sleep(1);
	}

	@AfterClass public static void tearDownClass()
	throws Exception {
		HttpStorageDistributedScenarioTestBase.tearDownClass();
	}

	@Test public void testFinishedInTime()
	throws Exception {
		assertTrue(FINISHED_IN_TIME);
	}

	@Test public void testMetricsLogFile()
	throws Exception {
		testMetricsLogFile(
			IoType.CREATE, LOAD_CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE, LOAD_LIMIT_TIME,
			CONFIG.getLoadConfig().getMetricsConfig().getPeriod()
		);
	}

	@Test public void testTotalMetricsLogFile()
	throws Exception {
		testTotalMetricsLogFile(
			IoType.CREATE, LOAD_CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE, LOAD_LIMIT_TIME
		);
	}

	@Test public void testMetricsStdout()
	throws Exception {
		testMetricsStdout(
			STD_OUTPUT.replaceAll("[\r\n]+", " "),
			IoType.CREATE, LOAD_CONCURRENCY, STORAGE_DRIVERS_COUNT, ITEM_DATA_SIZE,
			CONFIG.getLoadConfig().getMetricsConfig().getPeriod()
		);
	}

	@Test public void testIoTraceLogFile()
	throws Exception {
		final String nodeAddr = STORAGE_MOCKS.keySet().iterator().next();
		final List<CSVRecord> ioTraceRecords = getIoTraceLogRecords();
		for(final CSVRecord ioTraceRecord : ioTraceRecords) {
			testIoTraceRecord(ioTraceRecord, IoType.CREATE.ordinal(), ITEM_DATA_SIZE);
			testHttpStorageMockContains(
				nodeAddr, ioTraceRecord.get("ItemPath"),
				Long.parseLong(ioTraceRecord.get("TransferSize"))
			);
		}
	}
}
