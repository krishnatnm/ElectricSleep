package com.androsz.electricsleepbeta.app;

import java.util.Calendar;
import java.util.List;

import org.achartengine.model.PointD;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.androsz.electricsleepbeta.R;
import com.androsz.electricsleepbeta.alarmclock.Alarm;
import com.androsz.electricsleepbeta.alarmclock.AlarmClock;
import com.androsz.electricsleepbeta.alarmclock.Alarms;
import com.androsz.electricsleepbeta.content.StartSleepReceiver;
import com.androsz.electricsleepbeta.widget.SleepChart;
import com.androsz.electricsleepbeta.widget.SleepChartData;

public class SleepActivity extends HostActivity {

    private static final String TAG = SleepActivity.class.getSimpleName();

    private class DimScreenTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(final Void... params) {
            // just wait without blocking the main thread!
            try {
                Thread.sleep(DIM_SCREEN_AFTER_MS);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Void results) {
            // after we have waited, dim the screen on the main thread!
            startActivity(new Intent(SleepActivity.this, DimSleepActivity.class));
        }

        @Override
        protected void onPreExecute() {
            // notify the user that we've received that they need a dimmed
            // screen
            setToast(R.string.screen_will_dim);
        }
    }

    private Toast currentToast;

    private void setToast(int stringId) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        currentToast = Toast.makeText(this, stringId, Toast.LENGTH_LONG);
        currentToast.show();
    }

    private static final int DIM_SCREEN_AFTER_MS = 15000;

    private static final String SLEEP_CHART = "sleepChart";

    public static final String SYNC_CHART = "com.androsz.electricsleepbeta.SYNC_CHART";

    public static final String UPDATE_CHART = "com.androsz.electricsleepbeta.UPDATE_CHART";

    private final BroadcastReceiver batteryChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            pluggedIn = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0;
            airplaneModeNagChanged();
        }
    };

    AsyncTask<Void, Void, Void> dimScreenTask;
    private SleepChart sleepChart;
    private final BroadcastReceiver sleepStoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            finish();
        }
    };
    private final BroadcastReceiver syncChartReceiver = new BroadcastReceiver() {
        @SuppressWarnings("unchecked")
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (sleepChart.mData != null) {
                List<PointD> points =
                    (List<PointD>) intent.getSerializableExtra(SleepMonitoringService.SLEEP_DATA);
                for (PointD point : points) {
                    sleepChart.mData.xySeriesMovement.add(point.x, point.y);
                }
            }

            final double alarmTriggerSensitivity = intent.getDoubleExtra(
                    StartSleepReceiver.EXTRA_ALARM,
                    SettingsActivity.DEFAULT_ALARM_SENSITIVITY);
            sleepChart.setCalibrationLevel(alarmTriggerSensitivity);
            sleepChart.reconfigure();
            sleepChart.repaint();

            final boolean useAlarm = intent.getBooleanExtra(
                    StartSleepReceiver.EXTRA_USE_ALARM, false);
            final boolean forceScreenOn = intent.getBooleanExtra(
                    StartSleepReceiver.EXTRA_FORCE_SCREEN_ON, false);

            new AsyncTask<Void, Void, String[]>() {
                @Override
                protected String[] doInBackground(Void... params) {
                    String[] result = null;
                    final Alarm alarm = Alarms.calculateNextAlert(context);
                    if (alarm != null) {
                        final Calendar alarmTime = Calendar.getInstance();
                        alarmTime.setTimeInMillis(alarm.time);

                        java.text.DateFormat df = DateFormat
                                .getDateFormat(context);
                        df = DateFormat.getTimeFormat(context);
                        final String dateTime = df.format(alarmTime.getTime());
                        final int alarmWindow = intent.getIntExtra(
                                StartSleepReceiver.EXTRA_ALARM_WINDOW, 30);
                        alarmTime.add(Calendar.MINUTE, -1 * alarmWindow);
                        final String dateTimePre = df.format(alarmTime
                                .getTime());
                        result = new String[] { dateTimePre, dateTime };
                    }
                    return result;
                }

                @Override
                protected void onPostExecute(String[] result) {
                    if (result != null) {

                        if (useAlarm) {
                            textAlarmStatus.setText(context.getString(
                                    R.string.alarm_status_range, result[0],
                                    result[1]));
                            textAlarmStatus.setCompoundDrawablesWithIntrinsicBounds(getResources()
                                    .getDrawable(R.drawable.ic_alarm_pressed),
                                    null, null, null);
                            textAlarmStatusSub.setText(R.string.attempt_to_use_smartwake_);
                        } else {
                            textAlarmStatus.setCompoundDrawablesWithIntrinsicBounds(getResources()
                                    .getDrawable(R.drawable.ic_alarm_neutral),
                                    null, null, null);
                            textAlarmStatus.setText(result[0]);
                            textAlarmStatusSub.setText(R.string.not_using_smartwake_);
                        }

                    } else {
                        textAlarmStatus.setCompoundDrawablesWithIntrinsicBounds(getResources()
                                .getDrawable(R.drawable.ic_alarm_none),
                                null, null, null);
                        textAlarmStatus.setText(R.string.no_alarm);
                        textAlarmStatusSub.setText(R.string.sleep_no_alarm);
                    }
                    // dims the screen while in this activity and
                    // forceScreenOn is
                    // enabled
                    if (forceScreenOn) {
                        buttonSleepDim.setVisibility(View.VISIBLE);

                        cancelDimScreenTask();
                        dimScreenTask = new DimScreenTask();
                        dimScreenTask.execute();

                    } else {
                        buttonSleepDim.setVisibility(View.GONE);
                    }
                }
            }.execute();
        }
    };

    private TextView buttonSleepDim;
    private TextView buttonSleepPluggedIn;
    private TextView textAlarmStatus;
    private TextView textAlarmStatusSub;

    private final BroadcastReceiver updateChartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            sleepChart.sync(intent.getDoubleExtra(
                    SleepMonitoringService.EXTRA_X, 0), intent.getDoubleExtra(
                    SleepMonitoringService.EXTRA_Y, 0), intent.getDoubleExtra(
                    StartSleepReceiver.EXTRA_ALARM,
                    SettingsActivity.DEFAULT_ALARM_SENSITIVITY));
        }
    };

    @Override
    protected int getContentAreaLayoutId() {
        return R.layout.activity_sleep;
    }

    boolean pluggedIn = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.monitoring_sleep);
        airplaneModeOn = Settings.System.getInt(getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;

        registerReceiver(sleepStoppedReceiver, new IntentFilter(
                SleepMonitoringService.SLEEP_STOPPED));

        sleepChart = (SleepChart) findViewById(R.id.sleep_movement_chart);
        sleepChart.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_monitoring_sleep, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(sleepStoppedReceiver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_item_alarms:
            openAlarms();
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openAlarms() {
        startActivity(new Intent(this, AlarmClock.class));
    }

    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.button_tracking_stop:
            sendBroadcast(new Intent(SleepMonitoringService.STOP_AND_SAVE_SLEEP));
            finish();
            break;
        case R.id.text_alarm_status:
        case R.id.text_alarm_status_sub:
        case R.id.layout_alarm_status:
            openAlarms();
            break;
        }
    }

    @Override
    protected void onPause() {
        unregisterReceiver(airplaneModeChangedReceiver);
        unregisterReceiver(updateChartReceiver);
        unregisterReceiver(syncChartReceiver);
        unregisterReceiver(batteryChangedReceiver);
        if (currentToast != null) {
            currentToast.cancel();
        }
        cancelDimScreenTask();
        super.onPause();
    }

    private void cancelDimScreenTask() {
        // cancel the dim screen task if it hasn't completed
        if (dimScreenTask != null) {
            dimScreenTask.cancel(true);
            setToast(R.string.warning_dim_sleep_mode_can_only_occur_on_the_sleep_screen_);
        }
    }

    @Override
    protected void onRestoreInstanceState(final Bundle savedState) {
        SleepChartData data = (SleepChartData) savedState.getParcelable(SLEEP_CHART);
        if (sleepChart == null) {
            Log.d(TAG, "finding sleep chart from layout by id.");
            sleepChart = (SleepChart) findViewById(R.id.sleep_movement_chart);
        }
        sleepChart.setData(data);
        super.onRestoreInstanceState(savedState);
    }

    boolean airplaneModeOn = false;
    BroadcastReceiver airplaneModeChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            airplaneModeOn = intent.getBooleanExtra("state", false);
            airplaneModeNagChanged();
        }

    };

    private void airplaneModeNagChanged() {
        final int visibility = (pluggedIn || airplaneModeOn ? View.GONE
                : View.VISIBLE);

        buttonSleepPluggedIn.setVisibility(visibility);
    }

    @Override
    protected void onResume() {
        textAlarmStatus = (TextView) findViewById(R.id.text_alarm_status);
        textAlarmStatusSub = (TextView) findViewById(R.id.text_alarm_status_sub);
        buttonSleepDim = (TextView) findViewById(R.id.text_sleep_dim);
        buttonSleepPluggedIn = (TextView) findViewById(R.id.text_sleep_plugged_in);

        registerReceiver(airplaneModeChangedReceiver, new IntentFilter(
                Intent.ACTION_AIRPLANE_MODE_CHANGED));
        registerReceiver(updateChartReceiver, new IntentFilter(UPDATE_CHART));
        registerReceiver(syncChartReceiver, new IntentFilter(SYNC_CHART));
        registerReceiver(batteryChangedReceiver, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        sendBroadcast(new Intent(SleepMonitoringService.POKE_SYNC_CHART));
        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SLEEP_CHART, sleepChart.mData);
    }
}
