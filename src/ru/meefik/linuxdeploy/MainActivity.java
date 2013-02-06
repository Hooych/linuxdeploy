package ru.meefik.linuxdeploy;

import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockActivity {

	private static TextView logView;
	private static ScrollView logScroll;
	static Handler handler;
	private static boolean newLine = false;

	private static String getTimeStamp() {
		return "["
				+ new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
						.format(new Date()) + "] ";
	}

	public static void printLogMsg(String msg) {
		if (msg.length() > 0) {
			if (logView.length() == 0) {
				msg = getTimeStamp() + msg;
				newLine = false;
			}
			// add '\n' character
			if (newLine) {
				msg = "\n" + msg;
				newLine = false;
			}
			// remove all last '\n' characters
			while (msg.length() > 0 && msg.charAt(msg.length() - 1) == '\n') {
				msg = msg.substring(0, msg.length() - 1);
				newLine = true;
			}
			msg = msg.replaceAll("\\n", "\n" + getTimeStamp());
			logView.append(msg);
			// logView.scrollTo(logView.getLeft(), logView.getBottom());
			logScroll.post(new Runnable() {
				@Override
				public void run() {
					logScroll.fullScroll(View.FOCUS_DOWN);
					logScroll.clearFocus();
				}
			});
			if (PrefStore.LOGGING) {
				saveLogs(msg);
			}
		}
	}

	public static void saveLogs(String msg) {
		byte[] data = msg.getBytes();
		try {
			FileOutputStream fos = new FileOutputStream(PrefStore.LOG_FILE,
					true);
			fos.write(data);
			fos.flush();
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		PrefStore.updateTheme(this);
		super.onCreate(savedInstanceState);
		PrefStore.updateLocale(this);
		setContentView(R.layout.activity_main);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		logView = (TextView) findViewById(R.id.LogView);
		logScroll = (ScrollView) findViewById(R.id.LogScrollView);
		handler = new Handler();

		// ok we back, load the saved text
		if (savedInstanceState != null) {
			String savedText = savedInstanceState.getString("textlog");
			logView.setText(savedText);
			logScroll.post(new Runnable() {
				@Override
				public void run() {
					logScroll.fullScroll(View.FOCUS_DOWN);
				}
			});
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		PrefStore.updateLocale(getApplicationContext());
		getSupportMenuInflater().inflate(R.menu.activity_main, menu);

		boolean isLight = PrefStore.THEME.equals("light");

		menu.findItem(R.id.menu_properties).setIcon(
				isLight ? R.drawable.ic_action_properties_light
						: R.drawable.ic_action_properties_dark);
		
		int ot = getResources().getConfiguration().orientation;
		if (ot == Configuration.ORIENTATION_LANDSCAPE) {
			menu.findItem(R.id.menu_start).setIcon(
					isLight ? R.drawable.ic_action_start_light
							: R.drawable.ic_action_start_dark);
			menu.findItem(R.id.menu_stop).setIcon(
					isLight ? R.drawable.ic_action_stop_light
							: R.drawable.ic_action_stop_dark);
		}
		
		/*
		 * menu.add("info") .setIcon(isLight ?
		 * R.drawable.ic_action_properties_light :
		 * R.drawable.ic_action_properties_dark)
		 * .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		 */
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_start:
			new AlertDialog.Builder(this)
					.setTitle(R.string.confirm_start_title)
					.setMessage(R.string.confirm_start_message)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setCancelable(false)
					.setPositiveButton(android.R.string.yes,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									(new Thread() {
										@Override
										public void run() {
											new ShellEnv(
													getApplicationContext())
													.deployCmd("start");
										}
									}).start();
								}
							})
					.setNegativeButton(android.R.string.no,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							}).show();
			break;
		case R.id.menu_stop:
			new AlertDialog.Builder(this)
					.setTitle(R.string.confirm_stop_title)
					.setMessage(R.string.confirm_stop_message)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setCancelable(false)
					.setPositiveButton(android.R.string.yes,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									(new Thread() {
										@Override
										public void run() {
											new ShellEnv(
													getApplicationContext())
													.deployCmd("stop");
										}
									}).start();
								}
							})
					.setNegativeButton(android.R.string.no,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							}).show();
			break;
		case R.id.menu_status:
			(new Thread() {
				@Override
				public void run() {
					new ShellEnv(getApplicationContext()).deployCmd("status");
				}
			}).start();
			break;
		case R.id.menu_properties:
			Intent intent_properties = new Intent(this,
					DeployPrefsActivity.class);
			startActivity(intent_properties);
			break;
		case R.id.menu_settings:
			Intent intent_settings = new Intent(this, AppPrefsActivity.class);
			startActivity(intent_settings);
			break;
		case R.id.menu_about:
			Intent intent_about = new Intent(this, AboutActivity.class);
			startActivity(intent_about);
			break;
		case R.id.menu_clear:
			logView.setText("");
			break;
		case R.id.menu_exit:
			finish();
			break;
		case android.R.id.home:
			Intent intent_profiles = new Intent(this, ProfilesActivity.class);
			startActivity(intent_profiles);
			break;
		}
		return false;
	}

	@Override
	public void onResume() {
		super.onResume();

		PrefStore.get(getApplicationContext());

		String profileName = PrefStore.getCurrentProfile(getApplicationContext());
		String myIP = PrefStore.getLocalIpAddress();
		this.setTitle(profileName+"  [ "+myIP+" ]");

		// Restore text
		logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, PrefStore.FONT_SIZE);

		// Screen lock
		if (PrefStore.SCREEN_LOCK)
			this.getWindow().addFlags(
					WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			this.getWindow().clearFlags(
					WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// update configuration file
		if (PrefStore.PREF_CHANGE) {
			(new Thread() {
				@Override
				public void run() {
					new ShellEnv(getApplicationContext()).updateConfig();
				}
			}).start();
			PrefStore.PREF_CHANGE = false;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		// now, save the text if something overlaps this Activity
		savedInstanceState.putString("textlog", logView.getText().toString());
	}

}
