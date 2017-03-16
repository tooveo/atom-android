package io.ironsourceatom.sdk;

import android.content.Context;
import android.test.mock.MockContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Basic HttpClientTest test cases
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultHttpClientTest {

	// Test post behavior
	@Test
	public void postTest() throws
			Exception {
		DataOutputStream outMock = mock(DataOutputStream.class);
		InputStream inMock = mock(InputStream.class);
		when(mMockConn.getOutputStream()).thenReturn(outMock);
		when(mMockConn.getInputStream()).thenReturn(inMock);
		when(inMock.read(any(byte[].class), anyInt(), anyInt())).thenReturn(-1);
		// #1
		when(mMockConn.getResponseCode()).thenReturn(200);
		assertEquals(mClient.post("foo", "localhost").code, 200);
		// #2
		when(mMockConn.getResponseCode()).thenReturn(500);
		assertEquals(mClient.post("bar", "localhost").code, 500);
		// #3
		when(mMockConn.getResponseCode()).thenReturn(501);
		when(mMockConn.getInputStream()).thenThrow(new IOException());
		assertEquals(mClient.post("bar", "localhost").code, 501);
		// Connection settings assertions
		verify(mMockConn, times(3)).setRequestMethod("POST");
		// Close streams and disconnect
		verify(mMockConn, times(3)).disconnect();
		verify(outMock, times(3)).close();
		verify(inMock, times(2)).close();
	}

	// Mocking
	final HttpURLConnection mMockConn = mock(HttpURLConnection.class);
	final HttpClient        mClient   = new DefaultHttpClient() {
		@Override
		protected HttpURLConnection createConnection(String url) throws
				IOException {
			return mMockConn;
		}
	};
}