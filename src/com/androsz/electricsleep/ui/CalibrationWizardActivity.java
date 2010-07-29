package com.androsz.electricsleep.ui;

import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.androsz.electricsleep.R;
import com.androsz.electricsleep.service.SleepAccelerometerService;

public class CalibrationWizardActivity extends CustomTitlebarActivity implements
		OnInitListener {
	private ViewFlipper viewFlipper;

	private int minCalibration;
	private int maxCalibration;
	private int alarmTriggerCalibration;

	private TextToSpeech textToSpeech;
	private boolean ttsAvailable = false;

	private static final int TEST_TTS_INSTALLED = 0x1337;

	private boolean doWizardActivity() {
		boolean didActivity = false;
		final int currentChildId = viewFlipper.getCurrentView().getId();
		final Intent i = new Intent(this, SleepAccelerometerService.class);
		switch (currentChildId) {
		case R.id.minTest:
			stopService(i);
			i.putExtra("interval", 10000);
			i.putExtra("min", 0);
			i.putExtra("max", 100);

			startService(i);
			startActivityForResult(new Intent(this,
					CalibrateForResultActivity.class), R.id.minTest);
			didActivity = true;
			break;
		case R.id.maxTest:
			stopService(i);
			i.putExtra("interval", 5000);
			i.putExtra("min", minCalibration);
			i.putExtra("max", 100);

			startService(i);
			startActivityForResult(new Intent(this,
					CalibrateForResultActivity.class), R.id.maxTest);
			didActivity = true;
			break;
		case R.id.alarmTest:
			stopService(i);
			i.putExtra("interval", 2500);
			i.putExtra("min", minCalibration);
			i.putExtra("max", maxCalibration);

			startService(i);
			startActivityForResult(new Intent(this,
					CalibrateForResultActivity.class), R.id.alarmTest);
			didActivity = true;
			break;
		}
		return didActivity;
	}

	private void checkTextToSpeechInstalled() {
		Intent checkIntent = new Intent();
		checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
		startActivityForResult(checkIntent, TEST_TTS_INSTALLED);
	}

	private void notifyUser(String message) {
		if (ttsAvailable) {
			textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null);
		}
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

	@Override
	protected int getContentAreaLayoutId() {
		return R.layout.activity_calibrate;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == -0x1337) {
			notifyUser("Calibration failed. Try again.");
			return;
		}
		switch (requestCode) {
		case R.id.minTest:
			minCalibration = resultCode;
			notifyUser("Calibration succeeded with result: " + minCalibration);
			stopService(new Intent(this, SleepAccelerometerService.class));
			break;
		case R.id.maxTest:
			maxCalibration = resultCode;
			notifyUser("Calibration succeeded with result: " + maxCalibration);
			stopService(new Intent(this, SleepAccelerometerService.class));
			break;
		case R.id.alarmTest:
			alarmTriggerCalibration = resultCode;
			notifyUser("Calibration succeeded with result: ");
			stopService(new Intent(this, SleepAccelerometerService.class));
			break;
		case TEST_TTS_INSTALLED:
			if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
				// success, create the TTS instance
				if (textToSpeech == null) {
					textToSpeech = new TextToSpeech(this, this);
				}
			} else {
				// missing data, install it
				Intent installIntent = new Intent();
				installIntent
						.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
				startActivity(installIntent);
			}
			return;
		}
		viewFlipper.showNext();
		setupNavigationButtons();
	}

	@Override
	public void onBackPressed() {
		onLeftButtonClick(null);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		viewFlipper = (ViewFlipper) findViewById(R.id.wizardViewFlipper);
		setupNavigationButtons();
	}

	public void onLeftButtonClick(View v) {

		viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this,
				R.anim.slide_left_in));
		viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this,
				R.anim.slide_left_out));

		if (viewFlipper.getDisplayedChild() != 0) {
			viewFlipper.showPrevious();
		} else {
			super.onBackPressed();
		}

		setupNavigationButtons();
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedState) {

		super.onRestoreInstanceState(savedState);

		viewFlipper.setDisplayedChild(savedState.getInt("child"));

		minCalibration = savedState.getInt("min");
		maxCalibration = savedState.getInt("max");
		alarmTriggerCalibration = savedState.getInt("alarm");

		if (textToSpeech == null) {
			textToSpeech = new TextToSpeech(this, this);
		}

		setupNavigationButtons();
	}

	public void onRightButtonClick(View v) {
		viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this,
				R.anim.slide_right_in));
		viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this,
				R.anim.slide_right_out));

		final int lastChildIndex = viewFlipper.getChildCount() - 1;
		final int displayedChildIndex = viewFlipper.getDisplayedChild();

		if (displayedChildIndex == lastChildIndex) {
			final SharedPreferences.Editor ed = PreferenceManager
					.getDefaultSharedPreferences(getBaseContext()).edit();
			ed.putInt(getString(R.string.pref_minimum_sensitivity),
					minCalibration);
			ed.putInt(getString(R.string.pref_maximum_sensitivity),
					maxCalibration);
			ed.putInt(getString(R.string.pref_alarm_trigger_sensitivity),
					alarmTriggerCalibration);
			ed.commit();

			final SharedPreferences.Editor ed2 = getSharedPreferences(
					getString(R.string.prefs_version), Context.MODE_PRIVATE)
					.edit();
			ed2.putInt(getString(R.string.prefs_version), getResources()
					.getInteger(R.integer.prefs_version));
			ed2.commit();
			ed.commit();
			finish();
		} else {
			if (!doWizardActivity()) {
				viewFlipper.showNext();
				setupNavigationButtons();
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putInt("child", viewFlipper.getDisplayedChild());

		outState.putInt("min", minCalibration);
		outState.putInt("max", maxCalibration);
		outState.putInt("alarm", alarmTriggerCalibration);
	}

	private void setupNavigationButtons() {
		final Button leftButton = (Button) findViewById(R.id.leftButton);
		final Button rightButton = (Button) findViewById(R.id.rightButton);
		final int lastChildIndex = viewFlipper.getChildCount() - 1;
		final int displayedChildIndex = viewFlipper.getDisplayedChild();
		if (displayedChildIndex == 0) {
			leftButton.setText(R.string.exit);
			rightButton.setText(R.string.next);
			checkTextToSpeechInstalled();
		} else if (displayedChildIndex == lastChildIndex) {
			leftButton.setText(R.string.previous);
			rightButton.setText(R.string.finish);

			final TextView textViewMin = (TextView) findViewById(R.id.minResult);
			textViewMin.setText("" + minCalibration);
			final TextView textViewMax = (TextView) findViewById(R.id.maxResult);
			textViewMax.setText("" + maxCalibration);
			final TextView textViewAlarm = (TextView) findViewById(R.id.alarmResult);
			textViewAlarm.setText("" + alarmTriggerCalibration);

		} else if (displayedChildIndex > 0
				&& displayedChildIndex < lastChildIndex) {
			leftButton.setText(R.string.previous);
			rightButton.setText(R.string.next);
		}
	}

	@Override
	public void onInit(int arg0) {
		if (arg0 == TextToSpeech.SUCCESS) {
			if (textToSpeech.isLanguageAvailable(Locale.ENGLISH) == TextToSpeech.LANG_AVAILABLE) {
				textToSpeech.setLanguage(Locale.US);
				ttsAvailable = true;
				return;
			}
		}
		ttsAvailable = true;
	}
}