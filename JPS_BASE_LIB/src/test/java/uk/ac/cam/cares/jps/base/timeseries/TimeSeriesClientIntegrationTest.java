package uk.ac.cam.cares.jps.base.timeseries;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.*;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.PostgreSQLContainer;

import uk.ac.cam.cares.jps.base.query.RemoteRDBStoreClient;
import uk.ac.cam.cares.jps.base.query.RemoteStoreClient;
import uk.ac.cam.cares.jps.base.exception.JPSRuntimeException;

import static org.mockito.Mockito.*;

/**
 * This class provides integration tests for the TimeSeriesClient class
 */

//@Ignore("Requires both triple store endpoint and postgreSQL database set up and running (using testcontainers)\n" +
//		"Requires Docker to run the tests. When on Windows, WSL2 as backend is required to ensure proper execution")
@Testcontainers
public class TimeSeriesClientIntegrationTest {

	// TimeSeries client (with RDB and Sparql client)
	private static TimeSeriesClient<Instant> tsClient;

	private static RemoteRDBStoreClient rdbStoreClient;

	// Time series test data
	private static List<String> dataIRI_1, dataIRI_2;
	private static List<Class<?>> dataClass_1, dataClass_2;
	private static String timeUnit;
	private static Duration duration;
	private static ChronoUnit chronoUnit;
	private static Double numericalDuration;
	private static String temporalUnit;
	private final double epsilon = 0.000001d;

	// Will create two Docker containers for Blazegraph and postgreSQL
	// NOTE: requires access to the docker.cmclinnovations.com registry from the machine the test is run on.

	// Create Docker container with Blazegraph image from CMCL registry (image uses port 9999)
	// For more information regarding the registry, see: https://github.com/cambridge-cares/TheWorldAvatar/wiki/Docker%3A-Image-registry
	@Container
	private GenericContainer<?> blazegraph = new GenericContainer<>(
			DockerImageName.parse("docker.cmclinnovations.com/blazegraph_for_tests:1.0.0"))
			.withExposedPorts(9999);
	// Create Docker container with postgres 13.3 image from Docker Hub
	@Container
	private PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13.3");

	// Initialise 2 test time series data sets
	@Before
	public void initialiseData() {
		// Initialise time unit for all test data series
		timeUnit = "http://s";
		/*
		 * Initialise 1st time series with 3 associated data series
		 */
		dataIRI_1 = new ArrayList<>();
	    dataIRI_1.add("http://data1"); dataIRI_1.add("http://data2"); dataIRI_1.add("http://data3");
		// Specify type of data for each column (most data will be in doubles, but one can specify different data types)
		dataClass_1 = new ArrayList<>();
		dataClass_1.add(Double.class); dataClass_1.add(String.class); dataClass_1.add(Integer.class);
		/*
		 * Initialise 2nd time series with only one associated data series
		 */
		dataIRI_2 = new ArrayList<>();
		dataIRI_2.add("http://data4");
		// Specify type of data for each column (most data will be in doubles, but one can specify different data types)
		dataClass_2 = new ArrayList<>();
		dataClass_2.add(Double.class);

		duration = Duration.ofDays(33*9);
		chronoUnit = ChronoUnit.MONTHS;
		numericalDuration = 9.0;
		temporalUnit = TimeSeriesSparql.NS_TIME+"unitMonth";
	}

	// Create clean slate (new Docker containers) for each test
	@Before
	public void initialiseTimeSeriesClient() {

		try {
			// Start Blazegraph container
			blazegraph.start();
			// Start postgreSQL container
			postgres.start();
		} catch (Exception e) {
			throw new JPSRuntimeException(
					"TimeSeriesClientIntegrationTest: Docker container startup failed. Please try running tests again");
		}

		// Set endpoint to the triple store. The host and port are read from the container
		String endpoint = "http://" + blazegraph.getHost() + ":" + blazegraph.getFirstMappedPort();
		// Default namespace in blazegraph is "kb"
		endpoint = endpoint + "/blazegraph/namespace/kb/sparql";

		// Set up a kb client that points to the location of the triple store
		RemoteStoreClient kbClient = new RemoteStoreClient();
		kbClient.setUpdateEndpoint(endpoint);
		kbClient.setQueryEndpoint(endpoint);

		// Initialise TimeSeriesClient client with pre-configured kb client
		tsClient = new TimeSeriesClient<>(kbClient, Instant.class);

		// Configure database access
		rdbStoreClient = new RemoteRDBStoreClient(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
	}

	// Cleaning up containers after each test, otherwise unused containers will first be killed when all tests finished
	@After
	public void stopContainers() {
		if (blazegraph.isRunning()) {
			blazegraph.stop();
		}
		if (postgres.isRunning()) {
			postgres.stop();
		}
	}

	@Test
	public void testInitTimeSeriesWithoutExceptions() throws SQLException {
		try (Connection conn = rdbStoreClient.getConnection()) {
			// Verify kb is initially empty
			Assert.assertEquals(0, tsClient.countTimeSeries());

			// Initialise time series (3 dataIRIs, 1 tsIRI) in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.AVERAGE, duration, chronoUnit);

			// Verify correct instantiation in both kb and database
			Assert.assertEquals(1, tsClient.countTimeSeries());
			Assert.assertEquals(3, tsClient.getAssociatedData(tsClient.getTimeSeriesIRI(dataIRI_1.get(0))).size());
			TimeSeriesSparql.CustomDuration customDuration = tsClient.getCustomDuration(tsClient.getTimeSeriesIRI(dataIRI_1.get(0)));
			Assert.assertEquals(customDuration.getUnit(), temporalUnit);
			Assert.assertEquals(customDuration.getValue(), numericalDuration, epsilon);
			TimeSeries<Instant> ts = tsClient.getTimeSeries(dataIRI_1, conn);
			Assert.assertEquals(3, ts.getDataIRIs().size());
			for (String iri : dataIRI_1) {
				Assert.assertTrue(ts.getDataIRIs().contains(iri));
			}
			List<String> kb = ts.getDataIRIs();
			List<String> db = tsClient.getAssociatedData(tsClient.getTimeSeriesIRI(dataIRI_1.get(0)));
			kb.sort(null);
			db.sort(null);
			Assert.assertEquals(kb, db);
		}
	}

	@Test
	public void testInitTimeSeriesWithKGInitException() throws SQLException {

		// Interrupt triple store connection
		blazegraph.stop();
		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.INSTANTANEOUS, null, null));
			Assert.assertTrue(e.getMessage().contains("Timeseries was not created!"));
		}
	}

	@Test
	public void testInitTimeSeriesWithUnavailableRDB() throws SQLException {
		try (Connection conn = rdbStoreClient.getConnection()) {
			// Interrupt database connection
			postgres.stop();
			// Initialise time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.CUMULATIVETOTAL, null, null));
			Assert.assertTrue(e.getMessage().contains("Timeseries was not created!"));
		}
	}

	@Test
	public void testInitTimeSeriesWithUnavailableRDBAndKGRevertException() throws NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException, SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Retrieve the value of the private field 'rdfClient' of the time series client
			Field RDFClient = tsClient.getClass().getDeclaredField("rdfClient");
			RDFClient.setAccessible(true);
			TimeSeriesSparql rdfClient = (TimeSeriesSparql) RDFClient.get(tsClient);

			// Create a spy object of the real rdfClient and substitute the initial rdfClient with it
			// Spy's behave exactly like normal instances, except for particularly stubbed methods
			TimeSeriesSparql rdfClient_spy = spy(rdfClient);
			RDFClient.set(tsClient, rdfClient_spy);
			// Throw error when removal of time series in KG is intended (after RDB interaction failed) to simulate connection error etc.
			doThrow(new JPSRuntimeException("")).when(rdfClient_spy).removeTimeSeries(Mockito.anyString());

			// Interrupt database connection
			postgres.stop();
			// Initialise time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.GENERAL, null, null));
			Assert.assertTrue(e.getMessage().contains("Inconsistent state created when initialising time series"));
		}

	}

	@Test
	public void testDeleteIndividualTimeSeriesWithoutExceptions() throws SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.GENERAL, null, null);

			// Verify correct instantiation in both kb and database
			Assert.assertEquals(1, tsClient.countTimeSeries());
			Assert.assertEquals(dataIRI_1.size(),
					tsClient.getAssociatedData(tsClient.getTimeSeriesIRI(dataIRI_1.get(0))).size());
			TimeSeries<Instant> ts = tsClient.getTimeSeries(dataIRI_1, conn);
			Assert.assertEquals(dataIRI_1.size(), ts.getDataIRIs().size());

			// Delete 1st data series - verify deletion and that other data series are still unaltered
			String dataIRI = dataIRI_1.remove(0);
			tsClient.deleteIndividualTimeSeries(dataIRI, conn);
			Assert.assertEquals(1, tsClient.countTimeSeries());
			Assert.assertEquals(dataIRI_1.size(),
					tsClient.getAssociatedData(tsClient.getTimeSeriesIRI(dataIRI_1.get(0))).size());
			Assert.assertNull(tsClient.getTimeSeriesIRI(dataIRI));
			ts = tsClient.getTimeSeries(dataIRI_1, conn);
			Assert.assertFalse(ts.getDataIRIs().contains(dataIRI));
			String finalDataIRI = dataIRI;
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteIndividualTimeSeries(finalDataIRI, conn));
			Assert.assertTrue(e.getMessage().contains("DataIRI " + dataIRI + " not associated with any timeseries."));

			// Delete 2nd data series - verify deletion and that other data series are still unaltered
			dataIRI = dataIRI_1.remove(0);
			tsClient.deleteIndividualTimeSeries(dataIRI, conn);
			Assert.assertEquals(1, tsClient.countTimeSeries());
			Assert.assertEquals(dataIRI_1.size(),
					tsClient.getAssociatedData(tsClient.getTimeSeriesIRI(dataIRI_1.get(0))).size());
			Assert.assertNull(tsClient.getTimeSeriesIRI(dataIRI));
			ts = tsClient.getTimeSeries(dataIRI_1, conn);
			Assert.assertFalse(ts.getDataIRIs().contains(dataIRI));
			String finalDataIRI1 = dataIRI;
			e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteIndividualTimeSeries(finalDataIRI1, conn));
			Assert.assertTrue(e.getMessage().contains("DataIRI " + dataIRI + " not associated with any timeseries."));

			// Delete 3rd data series - verify deletion
			dataIRI = dataIRI_1.remove(0);
			tsClient.deleteIndividualTimeSeries(dataIRI, conn);
			Assert.assertEquals(0, tsClient.countTimeSeries());
			Assert.assertNull(tsClient.getTimeSeriesIRI(dataIRI));

			// Exception from executing SQL command with empty dataIRI
			e = Assert.assertThrows(JPSRuntimeException.class, () -> tsClient.getTimeSeries(dataIRI_1, conn));
			Assert.assertTrue(e.getMessage().contains("Error while executing SQL command"));
			String finalDataIRI2 = dataIRI;
			e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteIndividualTimeSeries(finalDataIRI2, conn));
			Assert.assertTrue(e.getMessage().contains("DataIRI " + dataIRI + " not associated with any timeseries."));
		}
	}

	@Test
	public void testDeleteIndividualTimeSeriesWithUnavailableKG() throws SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.INSTANTANEOUS, null, null);
			String dataIRI = dataIRI_1.get(0);

			// Interrupt triple store connection
			blazegraph.stop();

			// Delete time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteIndividualTimeSeries(dataIRI, conn));
			Assert.assertTrue(e.getCause().getMessage().contains("Error occurred during SPARQL query evaluation"));
		}
	}

	@Test
	public void testDeleteIndividualTimeSeriesWithKGDeleteException() throws IllegalArgumentException,
			IllegalAccessException, NoSuchFieldException, SecurityException, SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.INSTANTANEOUS, null, null);
			String dataIRI = dataIRI_1.get(0);

			// Retrieve the value of the private field 'rdfClient' of the time series client
			Field RDFClient = tsClient.getClass().getDeclaredField("rdfClient");
			RDFClient.setAccessible(true);
			TimeSeriesSparql rdfClient = (TimeSeriesSparql) RDFClient.get(tsClient);

			// Create a spy object of the real rdfClient and substitute the initial rdfClient with it
			// Spy's behave exactly like normal instances, except for particularly stubbed methods
			TimeSeriesSparql rdfClient_spy = spy(rdfClient);
			RDFClient.set(tsClient, rdfClient_spy);
			// Throw error when removal of time series in KG is intended
			doThrow(new JPSRuntimeException("")).when(rdfClient_spy).removeTimeSeriesAssociation(Mockito.anyString());

			// Delete time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteIndividualTimeSeries(dataIRI, conn));
			Assert.assertTrue(e.getMessage().contains("Timeseries association for " + dataIRI + " was not deleted!"));

			// Check that knowledge base and database are still consistent
			TimeSeries<Instant> ts = tsClient.getTimeSeries(dataIRI_1, conn);
			List<String> kb = ts.getDataIRIs();
			List<String> db = rdfClient.getAssociatedData(rdfClient.getTimeSeries(dataIRI));
			kb.sort(null);
			db.sort(null);
			Assert.assertEquals(kb, db);
		}
	}

	@Test
	public void testDeleteIndividualTimeSeriesWithUnavailableRDB() throws SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.GENERAL, null, null);

			// Retrieve latest database state and interrupt connection
			TimeSeries<Instant> ts = tsClient.getTimeSeries(dataIRI_1, conn);
			postgres.stop();

			// DataIRI to be deleted
			String dataIRI = dataIRI_1.get(0);
			// Delete time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteIndividualTimeSeries(dataIRI, conn));
			Assert.assertTrue(e.getMessage().contains("Timeseries association for " + dataIRI + " was not deleted!"));

			// Check that knowledge base and database are still consistent
			List<String> kb = ts.getDataIRIs();
			List<String> db = tsClient.getAssociatedData(tsClient.getTimeSeriesIRI(dataIRI));
			kb.sort(null);
			db.sort(null);
			Assert.assertEquals(kb, db);
			Assert.assertEquals(dataIRI_1.size(), kb.size());
		}
	}

	@Test
	public void testDeleteIndividualTimeSeriesWithKGRevertException() throws IllegalArgumentException,
			IllegalAccessException, NoSuchFieldException, SecurityException, SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.STEPWISECUMULATIVE, null, null);
			String dataIRI = dataIRI_1.get(0);

			// Retrieve the value of the private field 'rdfClient' of the time series client
			Field RDFClient = tsClient.getClass().getDeclaredField("rdfClient");
			RDFClient.setAccessible(true);
			TimeSeriesSparql rdfClient = (TimeSeriesSparql) RDFClient.get(tsClient);

			// Create a spy object of the real rdfClient and substitute the initial rdfClient with it
			// Spy's behave exactly like normal instances, except for particularly stubbed methods
			TimeSeriesSparql rdfClient_spy = spy(rdfClient);
			RDFClient.set(tsClient, rdfClient_spy);
			// Throw error when removal of time series in KG is intended
			doThrow(new JPSRuntimeException("")).when(rdfClient_spy).insertTimeSeriesAssociation(Mockito.any(),
					Mockito.any());

			// Interrupt postgreSQL connection
			postgres.stop();

			// Delete time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteIndividualTimeSeries(dataIRI, conn));
			Assert.assertTrue(e.getMessage()
					.contains("Inconsistent state created when deleting time series association for " + dataIRI));
		}
	}

	@Test
	public void testDeleteTimeSeriesWithoutExceptions() throws SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.CUMULATIVETOTAL, null, null);
			tsClient.initTimeSeries(dataIRI_2, dataClass_2, timeUnit, conn, TimeSeriesClient.Type.INSTANTANEOUS, null, null);

			// Verify correct instantiation in both kb and database
			Assert.assertEquals(2, tsClient.countTimeSeries());
			TimeSeries<Instant> ts1 = tsClient.getTimeSeries(dataIRI_1, conn);
			Assert.assertEquals(dataIRI_1.size(), ts1.getDataIRIs().size());
			TimeSeries<Instant> ts2 = tsClient.getTimeSeries(dataIRI_2, conn);
			Assert.assertEquals(dataIRI_2.size(), ts2.getDataIRIs().size());

			// Delete 1st time series - verify deletion and that 2nd time series is still unaltered
			String tsIRI = tsClient.getTimeSeriesIRI(dataIRI_1.get(0));
			tsClient.deleteTimeSeries(tsIRI, conn);
			Assert.assertEquals(1, tsClient.countTimeSeries());
			Assert.assertNull(tsClient.getTimeSeriesIRI(dataIRI_1.get(0)));
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.getTimeSeries(dataIRI_1, conn));
			Assert.assertTrue(e.getMessage()
					.contains("<" + dataIRI_1.get(0) + "> does not have an assigned time series instance"));

			TimeSeries<Instant> ts3 = tsClient.getTimeSeries(dataIRI_2, conn);
			Assert.assertEquals(ts2.getDataIRIs(), ts3.getDataIRIs());

			// Delete 2nd time series - verify deletion and that nothing remains in KG and database
			tsIRI = tsClient.getTimeSeriesIRI(dataIRI_2.get(0));
			tsClient.deleteTimeSeries(tsIRI, conn);
			Assert.assertEquals(0, tsClient.countTimeSeries());
			Assert.assertNull(tsClient.getTimeSeriesIRI(dataIRI_2.get(0)));
			e = Assert.assertThrows(JPSRuntimeException.class, () -> tsClient.getTimeSeries(dataIRI_2, conn));
			Assert.assertTrue(e.getMessage()
					.contains("<" + dataIRI_2.get(0) + "> does not have an assigned time series instance"));
		}
	}

	@Test
	public void testDeleteTimeSeriesWithUnavailableKG() throws SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.AVERAGE, duration, chronoUnit);
			// Retrieve tsIRI to be deleted
			String tsIRI = tsClient.getTimeSeriesIRI(dataIRI_1.get(0));

			// Interrupt triple store connection
			blazegraph.stop();

			// Delete time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteTimeSeries(tsIRI, conn));
			Assert.assertTrue(e.getCause().getMessage().contains("Error occurred during SPARQL query evaluation"));
		}
	}

	@Test
	public void testDeleteTimeSeriesWithKGDeleteException() throws IllegalArgumentException, IllegalAccessException,
			NoSuchFieldException, SecurityException, SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.CUMULATIVETOTAL, null, null);

			// Retrieve the value of the private field 'rdfClient' of the time series client
			Field RDFClient = tsClient.getClass().getDeclaredField("rdfClient");
			RDFClient.setAccessible(true);
			TimeSeriesSparql rdfClient = (TimeSeriesSparql) RDFClient.get(tsClient);

			// Create a spy object of the real rdfClient and substitute the initial rdfClient with it
			// Spy's behave exactly like normal instances, except for particularly stubbed methods
			TimeSeriesSparql rdfClient_spy = spy(rdfClient);
			RDFClient.set(tsClient, rdfClient_spy);
			// Throw error when removal of time series in KG is intended
			doThrow(new JPSRuntimeException("")).when(rdfClient_spy).removeTimeSeries(Mockito.anyString());

			// Retrieve tsIRI to be deleted
			String tsIRI = rdfClient.getTimeSeries(dataIRI_1.get(0));
			// Delete time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteTimeSeries(tsIRI, conn));
			Assert.assertTrue(e.getMessage().contains("Timeseries " + tsIRI + " was not deleted!"));

			// Check that knowledge base and database are still consistent
			TimeSeries<Instant> ts = tsClient.getTimeSeries(dataIRI_1, conn);
			List<String> kb = ts.getDataIRIs();
			List<String> db = rdfClient.getAssociatedData(rdfClient.getTimeSeries(dataIRI_1.get(0)));
			kb.sort(null);
			db.sort(null);
			Assert.assertEquals(kb, db);
		}
	}

	@Test
	public void testDeleteTimeSeriesWithUnavailableRDB() throws SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.GENERAL, null, null);

			// Retrieve tsIRI to be deleted
			String tsIRI = tsClient.getTimeSeriesIRI(dataIRI_1.get(0));

			// Retrieve latest database state and interrupt connection
			TimeSeries<Instant> ts = tsClient.getTimeSeries(dataIRI_1, conn);
			postgres.stop();

			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteTimeSeries(tsIRI, conn));
			Assert.assertTrue(e.getMessage().contains("Timeseries " + tsIRI + " was not deleted!"));

			// Check that knowledge base and database are still consistent
			List<String> kb = ts.getDataIRIs();
			List<String> db = tsClient.getAssociatedData(tsClient.getTimeSeriesIRI(dataIRI_1.get(0)));
			kb.sort(null);
			db.sort(null);
			Assert.assertEquals(kb, db);
		}
	}

	@Test
	public void testDeleteTimeSeriesWithKGRevertException() throws IllegalArgumentException, IllegalAccessException,
			NoSuchFieldException, SecurityException, SQLException {

		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.INSTANTANEOUS, null, null);

			// Retrieve the value of the private field 'rdfClient' of the time series client
			Field RDFClient = tsClient.getClass().getDeclaredField("rdfClient");
			RDFClient.setAccessible(true);
			TimeSeriesSparql rdfClient = (TimeSeriesSparql) RDFClient.get(tsClient);

			// Create a spy object of the real rdfClient and substitute the initial rdfClient with it
			// Spy's behave exactly like normal instances, except for particularly stubbed methods
			TimeSeriesSparql rdfClient_spy = spy(rdfClient);
			RDFClient.set(tsClient, rdfClient_spy);
			// Throw error when removal of time series in KG is intended
			doThrow(new JPSRuntimeException("")).when(rdfClient_spy).initTS(Mockito.any(), Mockito.any(), Mockito.any(),
					Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

			// Interrupt postgreSQL connection
			postgres.stop();

			// Retrieve tsIRI to be deleted
			String tsIRI = rdfClient.getTimeSeries(dataIRI_1.get(0));
			// Delete time series in knowledge base and database
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
					() -> tsClient.deleteTimeSeries(tsIRI, conn));
			Assert.assertTrue(e.getMessage().contains("Inconsistent state created when deleting time series " + tsIRI));
		}
	}

	@Test
	public void testDeleteAllWithoutExceptions() throws SQLException {
		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.AVERAGE, duration, chronoUnit);
			tsClient.initTimeSeries(dataIRI_2, dataClass_2, timeUnit, conn, TimeSeriesClient.Type.INSTANTANEOUS, null, null);

			// Verify correct instantiation in both kb and database
			Assert.assertEquals(2, tsClient.countTimeSeries());
			TimeSeries<Instant> ts1 = tsClient.getTimeSeries(dataIRI_1, conn);
			Assert.assertEquals(dataIRI_1.size(), ts1.getDataIRIs().size());
			TimeSeries<Instant> ts2 = tsClient.getTimeSeries(dataIRI_2, conn);
			Assert.assertEquals(dataIRI_2.size(), ts2.getDataIRIs().size());

			// Delete all data in database and knowledge base
			tsClient.deleteAll(conn);

			// Verify correct deletion
			Assert.assertEquals(0, tsClient.countTimeSeries());
			List<List<String>> series = Arrays.asList(dataIRI_1, dataIRI_2);
			for (List<String> s : series) {
				JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class,
						() -> tsClient.getTimeSeries(s, conn));
				Assert.assertTrue(e.getMessage().contains("Central RDB lookup table has not been initialised yet"));
			}
		}
	}

	@Test
	public void testDeleteAllWithExceptions() throws SQLException {
		try (Connection conn = rdbStoreClient.getConnection()) {
			// Initialise time series in knowledge base and database
			tsClient.initTimeSeries(dataIRI_1, dataClass_1, timeUnit, conn, TimeSeriesClient.Type.AVERAGE, duration, chronoUnit);
			tsClient.initTimeSeries(dataIRI_2, dataClass_2, timeUnit, conn, TimeSeriesClient.Type.GENERAL, null, null);

			// Interrupt database connection
			postgres.stop();

			// Delete all data in database and knowledge base
			JPSRuntimeException e = Assert.assertThrows(JPSRuntimeException.class, () -> tsClient.deleteAll(conn));
			Assert.assertTrue(e.getMessage().contains("Not all timeseries were deleted from database!"));

			// Interrupt triple store connection
			blazegraph.stop();

			// Delete all data in database and knowledge base
			e = Assert.assertThrows(JPSRuntimeException.class, () -> tsClient.deleteAll(conn));
			Assert.assertTrue(e.getMessage().contains("Not all timeseries were deleted from KG!"));
		}
	}
}
