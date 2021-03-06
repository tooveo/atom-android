package io.ironsourceatom.sdk;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.ironsourceatom.sdk.HttpClient.Response;
import io.ironsourceatom.sdk.ReportService.HandleStatus;
import io.ironsourceatom.sdk.StorageApi.Batch;
import io.ironsourceatom.sdk.StorageApi.Table;

import static io.ironsourceatom.sdk.TestsUtils.newReport;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReportServiceTest {

	// Two different responses
	final Response       ok            = new HttpClient.Response() {{
		code = 200;
		body = "OK";
	}};
	final Response       fail          = new HttpClient.Response() {{
		code = 503;
		body = "Service Unavailable";
	}};
	// Mocking
	final NetworkManager netManager    = mock(NetworkManager.class);
	final StorageApi     storage       = mock(DbAdapter.class);
	final HttpClient     client        = mock(DefaultHttpClient.class);
	final IsaConfig      config        = mock(IsaConfig.class);
	final Context        context       = mock(Context.class);
	final ReportService  reportService = new ReportService() {
		@Override
		protected HttpClient getClient() {
			return client;
		}

		@Override
		protected IsaConfig getConfig(Context context) {
			return config;
		}

		@Override
		protected StorageApi getStorage(Context context) {
			return storage;
		}

		@Override
		protected NetworkManager getNetManager(Context context) {
			return netManager;
		}
	};
	final String         TABLE         = "ib_table", TOKEN = "ib_token", DATA = "hello world";
	final Map<String, String> reportMap = new HashMap<String, String>() {{
		put(ReportData.DATA, DATA);
		put(ReportData.TOKEN, TOKEN);
		put(ReportData.TABLE, TABLE);
	}};
	final Table               mTable    = new Table(TABLE, TOKEN) {
		@Override
		public boolean equals(Object obj) {
			Table table = (Table) obj;
			return this.name.equals(table.name) && this.token.equals(table.token);
		}
	};

	@Before
	public void startClear() {
		// reset mocks
		reset(storage, client, config);
		// add default configuration
		when(config.getNumOfRetries()).thenReturn(1);
		when(config.getAllowedNetworkTypes()).thenReturn(-1);
		when(netManager.getNetworkAtomType()).thenReturn(-1);
		when(netManager.isOnline()).thenReturn(true);

		reportService.init(context);
	}

	// When you tracking an event.
	@Test
	public void trackOnly() throws
			Exception {
		config.setBulkSize(Integer.MAX_VALUE);
		Intent intent = newReport(SdkEvent.ENQUEUE, reportMap);
		reportService.handleReport(intent);
		verify(storage, times(1)).addEvent(mTable, DATA);
		verify(client, never()).post(anyString(), anyString());
	}

	// When reportService get a post-event and everything goes well(connection available, and IronSourceAtomFactory responds OK).
	// Should call "isOnline()" and "post()" (with the given event), NOT add the event to the
	// persistence data storage, and returns true
	@Test
	public void postSuccess() throws
			Exception {
		String url = "http://host.com/post";
		when(client.post(anyString(), anyString())).thenReturn(ok);
		Intent intent = newReport(SdkEvent.POST_SYNC, reportMap);
		when(config.getAtomEndPoint(anyString())).thenReturn(url);
		assertTrue(reportService.handleReport(intent) == ReportService.HandleStatus.HANDLED);
		verify(netManager, times(1)).isOnline();
		verify(client, times(1)).post(anyString(), eq(url));
		verify(storage, never()).addEvent(mTable, DATA);
	}

	// When reportService get a post-event and we get an authentication error(40X) from Poster
	// Should discard the event, NOT add it to thr storage and returns true.
	@Test
	public void postAuthFailed() throws
			Exception {
		String url = "http://host.com";
		when(config.getNumOfRetries()).thenReturn(10);
		when(config.getAtomEndPoint(TOKEN)).thenReturn(url);
		when(client.post(anyString(), anyString())).thenReturn(new Response() {{
			code = 401;
			body = "Unauthorized";
		}});
		Intent intent = newReport(SdkEvent.POST_SYNC, reportMap);
		assertEquals(reportService.handleReport(intent), ReportService.HandleStatus.HANDLED);
		verify(netManager, times(1)).isOnline();
		verify(client, times(1)).post(anyString(), eq(url));
		verify(storage, never()).addEvent(mTable, DATA);
	}

	// When reportService get a post-event(or flush), but the device not connected to internet.
	// Should try to post "n" times, add it to storage if it's failed, and returns false.
	@Test
	public void postWithoutNetwork() throws
			Exception {
		when(netManager.isOnline()).thenReturn(false);
		Intent intent = newReport(SdkEvent.POST_SYNC, reportMap);
		// no idle time, but should try it out 10 times
		when(config.getNumOfRetries()).thenReturn(10);
		assertEquals(reportService.handleReport(intent), HandleStatus.RETRY);
		verify(netManager, times(1)).isOnline();
		verify(client, never()).post(anyString(), anyString());
		verify(storage, times(1)).addEvent(mTable, DATA);
	}

	// When reportService get a post-event(or flush), and the device is on ROAMING_MODE.
	// It should try to send only if its has a permission to it.
	@Test
	public void postOnRoaming() throws
			Exception {
		Intent intent = newReport(SdkEvent.POST_SYNC, reportMap);
		when(config.isAllowedOverRoaming()).thenReturn(false, false, true);
		when(netManager.isDataRoamingEnabled()).thenReturn(false, true, true);
		when(client.post(anyString(), anyString())).thenReturn(ok);
		assertEquals(reportService.handleReport(intent), HandleStatus.HANDLED);
		assertEquals(reportService.handleReport(intent), HandleStatus.RETRY);
		assertEquals(reportService.handleReport(intent), HandleStatus.HANDLED);
		verify(client, times(2)).post(anyString(), anyString());
	}

	// When reportService get a post-event(or flush), should test if the
	// network type allowing it to make a network transaction before trying to make it.
	@Test
	public void isNetworkTypeAllowed() throws
			Exception {
		Intent intent = newReport(SdkEvent.POST_SYNC, reportMap);
		int WIFI = IronSourceAtomFactory.NETWORK_WIFI, MOBILE = IronSourceAtomFactory.NETWORK_MOBILE;
		// List of scenarios, each member contains:
		// configResult, networkTypeResult and the expected behavior.
		List<TestScenario> scenarios = new ArrayList<>();
		scenarios.add(new TestScenario(~0, MOBILE, HandleStatus.HANDLED));
		scenarios.add(new TestScenario(WIFI | MOBILE, MOBILE, HandleStatus.HANDLED));
		scenarios.add(new TestScenario(WIFI | MOBILE, WIFI, HandleStatus.HANDLED));
		scenarios.add(new TestScenario(WIFI, WIFI, HandleStatus.HANDLED));
		scenarios.add(new TestScenario(MOBILE, MOBILE, HandleStatus.HANDLED));
		scenarios.add(new TestScenario(WIFI, MOBILE, HandleStatus.RETRY));
		scenarios.add(new TestScenario(MOBILE, WIFI, HandleStatus.RETRY));
		when(client.post(anyString(), anyString())).thenReturn(ok);
		for (TestScenario test : scenarios) {
			when(config.getAllowedNetworkTypes()).thenReturn(test.configStatus);
			when(netManager.getNetworkAtomType()).thenReturn(test.networkStatus);
			assertEquals(reportService.handleReport(intent), test.expected);
		}
	}

	// When reportService get a flush-event and there's no items in the queue.
	// Should do nothing and return true.
	@Test
	public void flushNothing() {
		Intent intent = newReport(SdkEvent.FLUSH_QUEUE, new HashMap<String, String>());
		assertEquals(HandleStatus.HANDLED, reportService.handleReport(intent));
		verify(storage, times(1)).getTables();
	}

	// When reportService get a flush-event, it should ask for the all tables with `getTables`,
	// and then call `getEvents` for each of them with `maximumBulkSize`.
	// If everything goes well, it should drain the table, and then delete it.
	@Test
	public void flushSuccess() throws
			Exception {
		// Config this situation
		when(config.getBulkSize()).thenReturn(2);
		when(config.getMaximumRequestLimit()).thenReturn((long) (1024));
		// Another table to test
		final Table mTable1 = new Table("a8m", "a8m_token") {
			@Override
			public boolean equals(Object obj) {
				Table table = (Table) obj;
				return this.name.equals(table.name) && this.token.equals(table.token);
			}
		};
		List<Table> tables = new ArrayList<Table>() {{
			add(mTable);
			add(mTable1);
		}};
		when(storage.getTables()).thenReturn(tables);
		// table batch result
		when(storage.getEvents(mTable, config.getBulkSize())).thenReturn(new Batch("2", new ArrayList<String>() {{
			add("foo");
			add("bar");
		}}), new Batch("3", new ArrayList<String>() {{
			add("foo");
		}}));
		// mTable1 batch result
		when(storage.getEvents(mTable1, config.getBulkSize())).thenReturn(new Batch("4", new ArrayList<String>() {{
			add("foo");
		}}));
		when(storage.deleteEvents(mTable, "2")).thenReturn(2);
		when(storage.deleteEvents(mTable1, "4")).thenReturn(1);
		when(storage.count(mTable)).thenReturn(1);
		Intent intent = newReport(SdkEvent.FLUSH_QUEUE, new HashMap<String, String>());
		// All success
		when(client.post(anyString(), anyString())).thenReturn(ok, ok, ok);
		assertEquals(HandleStatus.HANDLED, reportService.handleReport(intent));
		verify(storage, times(2)).getEvents(mTable, config.getBulkSize());
		verify(storage, times(1)).deleteEvents(mTable, "2");
		verify(storage, times(1)).deleteEvents(mTable, "3");
		// In the second and the third time, it assume that the table is empty
		// because NUMBER_OF_DELETES < NUMBER_OF_DESIRED
		verify(storage, times(1)).count(mTable);
		verify(storage, times(1)).deleteTable(mTable);
		verify(storage, times(1)).getEvents(mTable1, config.getBulkSize());
		verify(storage, times(1)).deleteEvents(mTable1, "4");
		verify(storage, times(1)).deleteTable(mTable1);
	}

	// When reportService get a flush-event, and there's no tables to drain(i.e: no event)
	// Should do-nothing, and return true
	@Test
	public void flushNoItems() throws
			Exception {
		Intent intent = newReport(SdkEvent.FLUSH_QUEUE, new HashMap<String, String>());
		assertEquals(HandleStatus.HANDLED, reportService.handleReport(intent));
		verify(storage, times(1)).getTables();
		verify(storage, never()).getEvents(any(Table.class), anyInt());
	}

	// When reportService try to flush a batch, and it encounter an error(e.g: connectivity)
	// should stop-flushing, and return false
	@Test
	public void flushFailed() throws
			Exception {
		Intent intent = newReport(SdkEvent.FLUSH_QUEUE, new HashMap<String, String>());
		// Batch result
		when(storage.getEvents(mTable, config.getBulkSize())).thenReturn(new Batch("2", new ArrayList<String>() {{
			add("foo");
			add("bar");
		}}));
		when(storage.getTables()).thenReturn(new ArrayList<Table>() {{
			add(mTable);
		}});
		when(client.post(anyString(), anyString())).thenReturn(fail);
		assertEquals(HandleStatus.RETRY, reportService.handleReport(intent));
		verify(storage, times(1)).getEvents(mTable, config.getBulkSize());
		verify(storage, never()).deleteEvents(mTable, "2");
		verify(storage, never()).deleteTable(mTable);
	}

	// When tracking an event(record) to some table and the count number
	// is greater or equal to bulk-size, should flush the queue.
	@Test
	public void trackCauseFlush() {
		config.setBulkSize(2);
		when(storage.addEvent(mTable, DATA)).thenReturn(2);
		Intent intent = newReport(SdkEvent.ENQUEUE, reportMap);
		reportService.handleReport(intent);
		verify(storage, times(1)).addEvent(mTable, DATA);
		verify(storage, times(1)).getEvents(mTable, config.getBulkSize());
	}

	// ByteSize limits logic
	// The scenario goes like this:
	// Ask for events with limit of 2 and the batch is too large.
	// reportService decrease the bulkSize(limit) and ask for limit of 1.
	// in this situation it doesn't have another choice except sending this batch(of length 1).
	@Test
	public void maxRequestLimit() throws
			Exception {
		when(config.getBulkSize()).thenReturn(2);
		when(config.getMaximumRequestLimit()).thenReturn((long) (1024 * 1024 + 1));
		final String chunk = new String(new char[1024 * 1024]).replace('\0', 'b');
		when(storage.getTables()).thenReturn(new ArrayList<Table>() {{
			add(mTable);
		}});
		when(storage.getEvents(eq(mTable), anyInt())).thenReturn(new Batch("2", new ArrayList<String>() {{
			add(chunk);
			add(chunk);
		}}), new Batch("1", new ArrayList<String>() {{
			add(chunk);
		}}), new Batch("2", new ArrayList<String>() {{
			add(chunk);
		}}));
		when(storage.deleteEvents(eq(mTable), anyString())).thenReturn(1);
		when(storage.count(mTable)).thenReturn(1, 0);
		when(client.post(anyString(), anyString())).thenReturn(ok, ok);
		Intent intent = newReport(SdkEvent.FLUSH_QUEUE, new HashMap<String, String>());
		reportService.handleReport(intent);
		verify(storage, times(2)).getEvents(mTable, 2);
		verify(storage, times(1)).getEvents(mTable, 1);
		verify(storage, times(1)).deleteTable(mTable);
	}

	// Test data format
	// Should omit the "token" field and add "auth"
	@Test
	public void dataFormat() throws
			Exception {
		when(client.post(any(String.class), any(String.class))).thenReturn(ok);
		Intent intent = newReport(SdkEvent.POST_SYNC, reportMap);
		assertEquals(reportService.handleReport(intent), HandleStatus.HANDLED);
		JSONObject report = new JSONObject(reportMap);
		String token = reportMap.get(ReportData.TOKEN);
		report.put(ReportData.AUTH, Utils.auth(report.getString(ReportData.DATA), report.getString(ReportData.TOKEN)))
		      .remove(ReportData.TOKEN);
		verify(client, times(1)).post(eq(report.toString()), anyString());
	}

	// Constant report arguments for testing


	// Helper class, used inside "isNetworkAllowed" test case.
	class TestScenario {

		int          configStatus;
		int          networkStatus;
		HandleStatus expected;

		TestScenario(int config, int network, HandleStatus exp) {
			configStatus = config;
			networkStatus = network;
			expected = exp;
		}
	}
}
