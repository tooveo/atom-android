package io.ironsourceatom.sdk;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Intent service to handle tracker functionality
 */
public class ReportService
		extends IntentService {

	private static final String TAG = "ReportService";

	protected AlarmManager  alarmManager;
	protected ReportHandler handler;
	protected BackOff       backOff;

	public ReportService() {
		super(TAG);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Context context = this.getApplicationContext();
		alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		handler = new ReportHandler(context);
		backOff = BackOff.getInstance(context);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		try {
			if (handler.handleReport(intent) == ReportHandler.HandleStatus.RETRY && backOff.hasNext()) {
				setAlarm(backOff.next());
			}
			else {
				backOff.reset();
			}
		} catch (Throwable th) {
			Logger.log(TAG, "failed to handle intent: " + th, th, Logger.SDK_ERROR);
		}

	}

	protected void setAlarm(long triggerMills) {
		Logger.log(TAG, "Setting alarm, Will send in: " + (triggerMills - backOff.currentTimeMillis()) + "ms", Logger.SDK_DEBUG);
		ReportData report = new ReportData(SdkEvent.FLUSH_QUEUE);
		final Intent reportIntent = new Intent(this, ReportService.class);
		PendingIntent intent = PendingIntent.getService(this, 0, reportIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		alarmManager.cancel(intent);
		alarmManager.set(AlarmManager.RTC, triggerMills, intent);
	}
}