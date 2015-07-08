package com.emc.mongoose.integ;

import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.integ.integTestTools.IntegConstants;
import com.emc.mongoose.integ.integTestTools.LogFileManager;
import com.emc.mongoose.integ.integTestTools.PortListener;
import com.emc.mongoose.integ.integTestTools.SavedOutputStream;
import com.emc.mongoose.run.scenario.ScriptRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;

/**
 * Created by olga on 08.07.15.
 * Covers TC #4(name: "Single write load using several concurrent threads/connections.", steps: all for load.threads=100)
 * in Mongoose Core Functional Testing
 */
public class SingleWriteScenarioWith100ConcurrentConnectionsIntegTest {
	//
	private static SavedOutputStream savedOutputStream;
	//
	private static final int DATA_COUNT = 1000000, LOAD_THREADS = 100;
	private static String createRunId = IntegConstants.LOAD_CREATE;
	private static final String DATA_SIZE = "0B";
	private static Thread writeScenarioMongoose;

	@BeforeClass
	public static void before()
	throws Exception {
		// Set new saved console output stream
		savedOutputStream = new SavedOutputStream(System.out);
		System.setOut(new PrintStream(savedOutputStream));
		//Create run ID
		createRunId += ":" + DATA_SIZE + ":" + IntegConstants.FMT_DT.format(
			Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).getTime()
		);
		System.setProperty(RunTimeConfig.KEY_RUN_ID, createRunId);
		// If tests run from the IDEA full logging file must be set
		final String fullLogConfFile = Paths
			.get(System.getProperty(IntegConstants.USER_DIR_PROPERTY_NAME), Constants.DIR_CONF, IntegConstants.LOG_FILE_NAME)
			.toString();
		System.setProperty(IntegConstants.LOG_CONF_PROPERTY_KEY, fullLogConfFile);
		LogUtil.init();
		final Logger rootLogger = LogManager.getRootLogger();
		//Reload default properties
		RunTimeConfig runTimeConfig = new  RunTimeConfig();
		RunTimeConfig.setContext(runTimeConfig);
		//run mongoose default scenario in standalone mode
		writeScenarioMongoose = new Thread(new Runnable() {
			@Override
			public void run() {
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_RUN_ID, createRunId);
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_LOAD_LIMIT_COUNT, DATA_COUNT);
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_DATA_SIZE_MAX, DATA_SIZE);
				RunTimeConfig.getContext().set(RunTimeConfig.KEY_DATA_SIZE_MIN, DATA_SIZE);
				RunTimeConfig.getContext().set("load.type.create.threads", LOAD_THREADS);
				rootLogger.info(Markers.MSG, RunTimeConfig.getContext().toString());
				new ScriptRunner().run();
			}
		}, "writeScenarioMongoose");
		// Before start Mongoose
		int countConnections = PortListener.getCountConnectionsOnPort(IntegConstants.PORT_INDICATOR);
		// Check that actual connection count = 1 because cinderella is run local
		Assert.assertEquals(1, countConnections);
		// Start Mongoose
		writeScenarioMongoose.start();
		writeScenarioMongoose.join(30000);
	}

	@AfterClass
	public static void after()
	throws Exception {
		if (!writeScenarioMongoose.isInterrupted()) {
			writeScenarioMongoose.join();
			writeScenarioMongoose.interrupt();
		}

		Path expectedFile = LogFileManager.getMessageFile(createRunId).toPath();
		//Check that messages.log file is contained
		Assert.assertTrue(Files.exists(expectedFile));

		expectedFile = LogFileManager.getPerfAvgFile(createRunId).toPath();
		//Check that perf.avg.csv file is contained
		Assert.assertTrue(Files.exists(expectedFile));

		expectedFile = LogFileManager.getPerfTraceFile(createRunId).toPath();
		//Check that perf.trace.csv file is contained
		Assert.assertTrue(Files.exists(expectedFile));

		expectedFile = LogFileManager.getDataItemsFile(createRunId).toPath();
		//Check that data.items.csv file is contained
		Assert.assertTrue(Files.exists(expectedFile));

		expectedFile = LogFileManager.getErrorsFile(createRunId).toPath();
		//Check that errors.log file is not created
		Assert.assertFalse(Files.exists(expectedFile));

		//Read data.items.csv file of create scenario run
		final File dataItemsFile = LogFileManager.getDataItemsFile(createRunId);
		final BufferedReader bufferedReader = new BufferedReader(new FileReader(dataItemsFile));

		int dataSize, countDataItems = 0;
		String line = bufferedReader.readLine();

		while (line != null){
			// Get dataSize from each line
			dataSize = Integer.valueOf(line.split(",")[IntegConstants.DATA_SIZE_COLUMN_INDEX]);
			Assert.assertEquals(SizeUtil.toSize(DATA_SIZE), dataSize);
			countDataItems++;
			line = bufferedReader.readLine();
		}
		//Check that lines count in data.items.csv file is equal DATA_COUNT
		Assert.assertEquals(DATA_COUNT, countDataItems);
		//
		Assert.assertTrue(savedOutputStream.toString().contains(IntegConstants.SCENARIO_END_INDICATOR));
		System.setOut(savedOutputStream.getPrintStream());
	}

	@Test
	public void shouldBeActiveAllConnections()
	throws Exception {
		for (int i = 0; i < 3; i++) {
			int countConnections = PortListener.getCountConnectionsOnPort(IntegConstants.PORT_INDICATOR);
			// Check that actual connection count = (LOAD_THREADS * 2 + 1) because cinderella is run local
			Assert.assertEquals((LOAD_THREADS * 2 + 1), countConnections);
		}
	}

	@Test
	public void shouldAllThreadsProduceWorkload()
	throws Exception {
		Matcher matcher;
		String threadName;
		int countProduceWorkloadThreads = 0;
		final Map<Thread, StackTraceElement[]> stackTraceElementMap = Thread.getAllStackTraces();
		for (final Thread thread : stackTraceElementMap.keySet()) {
			threadName = thread.getName();
			matcher = IntegConstants.LOAD_THRED_NAME_PATTERN.matcher(threadName);
			if (matcher.find()) {
				countProduceWorkloadThreads++;
			}
		}
		Assert.assertEquals(LOAD_THREADS, countProduceWorkloadThreads);
	}

	@Test
	public void shouldGeneralStatusOfTheRunIsRegularlyReports()
	throws Exception {
		// Get perf.avg.csv file
		final File perfAvgFile = LogFileManager.getPerfAvgFile(createRunId);
		final BufferedReader bufferedReader = new BufferedReader(new FileReader(perfAvgFile));

		String line;
		Matcher matcher;
		line = bufferedReader.readLine();
		List<Integer> listSecOfReports = new ArrayList<>();
		while (line != null) {
			matcher = IntegConstants.TIME_PATTERN.matcher(line);
			if (matcher.find()) {
				// Get seconds of report's timestamp
				listSecOfReports.add(Integer.valueOf(matcher.group().split(":")[2]));
			}
			line = bufferedReader.readLine();
		}
		// Check period of reports is correct
		int firstTime, nextTime;
		// Period must be equal 10 sec
		final int period = RunTimeConfig.getContext().getLoadMetricsPeriodSec();
		Assert.assertEquals(10, period);
		//
		for (int i = 0; i < listSecOfReports.size() -1; i++) {
			firstTime = listSecOfReports.get(i) % period;
			nextTime = listSecOfReports.get(i+1) % period;
			Assert.assertEquals(firstTime, nextTime);
		}
	}

	@Test
	public void shouldCreateCorrectPerfAvgFiles()
	throws Exception {
		// Get perf.avg.csv file of write scenario run
		final File writePerfAvgFile = LogFileManager.getPerfAvgFile(createRunId);
		final BufferedReader bufferedReader = new BufferedReader(new FileReader(writePerfAvgFile));
		//
		String line = bufferedReader.readLine();
		//Check that header of file is correct
		Assert.assertEquals(LogFileManager.HEADER_PERF_AVG_FILE, line);
		line = bufferedReader.readLine();
		while (line != null) {
			Assert.assertTrue(LogFileManager.matchWithPerfAvgFilePattern(line));
			line = bufferedReader.readLine();
		}
	}

	@Test
	public void shouldCreateCorrectInformationAboutLoad()
	throws Exception {
		// Get perf.avg.csv file of write scenario run
		final File writePerfAvgFile = LogFileManager.getPerfAvgFile(createRunId);
		final BufferedReader bufferedReader = new BufferedReader(new FileReader(writePerfAvgFile));
		//
		String line = bufferedReader.readLine();
		//Check that header of file is correct
		Assert.assertEquals(LogFileManager.HEADER_PERF_AVG_FILE, line);
		//
		Matcher matcher;
		String loadType, actualLoadType;
		String[] loadInfo;
		int threadsPerNode;
		//
		line = bufferedReader.readLine();
		while (line != null) {
			//
			matcher = IntegConstants.LOAD_PATTERN.matcher(line);
			if (matcher.find()) {
				loadInfo = matcher.group().split("(-|x)");
				// Check load type and load limit count values are correct
				loadType = RunTimeConfig.getContext().getScenarioSingleLoad().toLowerCase() + String.valueOf(DATA_COUNT);
				actualLoadType = loadInfo[2].toLowerCase();
				Assert.assertEquals(loadType, actualLoadType);
				// Check "threads per node" value is correct
				threadsPerNode = Integer.valueOf(loadInfo[3]);
				Assert.assertEquals(LOAD_THREADS, threadsPerNode);
			}
			line = bufferedReader.readLine();
		}
	}

	@Test
	public void shouldCreateCorrectDataItemsFilesAfterWriteScenario()
	throws Exception {
		// Get data.items.csv file of write scenario run
		final File writeDataItemFile = LogFileManager.getDataItemsFile(createRunId);
		final BufferedReader bufferedReader = new BufferedReader(new FileReader(writeDataItemFile));
		//
		String line = bufferedReader.readLine();
		while (line != null) {
			Assert.assertTrue(LogFileManager.matchWithDataItemsFilePattern(line));
			line = bufferedReader.readLine();
		}
	}
}
