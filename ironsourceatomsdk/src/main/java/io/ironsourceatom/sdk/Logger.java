package io.ironsourceatom.sdk;

import android.content.Context;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Logger {

	private static final String LOG_TAG = "AtomSDK";

	protected static final int     PRE_INIT        = 1;
	public static final    int     SDK_ERROR       = 2;
	public static final    int     NORMAL          = 3;
	public static final    int     SDK_DEBUG       = 4;
	private static final   boolean mIsSuperDevMode = BuildConfig.IS_SUPER_DEV_MODE;


	public static IsaConfig.LOG_TYPE logLevel = IsaConfig.LOG_TYPE.PRODUCTION;

	private static boolean PRINT_ERROR_STACK_TRACE = false;

	private static Context sContext;

	// Needed for for getting the error tracker
	static void setContext(Context context) {
		sContext = context;
	}

	/**
	 * Set Atom Logger print error stack trace
	 *
	 * @param printData is need to print data
	 */
	public static void setPrintErrorStackTrace(boolean printData) {
		PRINT_ERROR_STACK_TRACE = printData;
	}

	public static void log(String tag, String msg, int level) {
		log(String.format("[%s]: %s", tag, msg), level);
	}

	public static void log(String tag, String msg, Throwable ex, int level) {
		if (PRINT_ERROR_STACK_TRACE) {
			StringBuilder errorMessage = new StringBuilder();

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);

			errorMessage.append(sw.toString())
			            .append("\n");
			errorMessage.append(msg);

			msg = errorMessage.toString();
		}

		log(String.format("[%s]: %s", tag, msg), level);
	}

	public static void log(String logString, int log_level) {
		switch (log_level) {
			case (PRE_INIT):
				Log.w(LOG_TAG, logString);
				break;
			case (NORMAL):
				if (logLevel == IsaConfig.LOG_TYPE.DEBUG || mIsSuperDevMode) {
					Log.i(LOG_TAG, logString);
				}
				break;
			case (SDK_ERROR):
				IronSourceAtomFactory.getInstance(sContext)
				                     .getErrorTracker()
				                     .trackError(logString);
			case (SDK_DEBUG):
				if (!mIsSuperDevMode) {
					break;
				}
				Log.d(LOG_TAG, logString);
				break;
		}
	}
}