package org.hackyourlife.gcn.dsp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

public class PlayerService extends Service {
	public final static String ACTION_PAUSE	= "org.hackyourlife.gcn.dsp.action.PAUSE";
	public final static String ACTION_PLAY	= "org.hackyourlife.gcn.dsp.action.PLAY";
	public final static String ACTION_STOP	= "org.hackyourlife.gcn.dsp.action.STOP";
	public final static String ACTION_RESET	= "org.hackyourlife.gcn.dsp.action.RESET";
	public final static String ACTION_INFO	= "org.hackyourlife.gcn.dsp.action.INFO";

	final static int	NOTIFICATION_ID = 1;

	RS03Player		player;

	NotificationManager	nm;
	AudioManager		audio;
	Notification		notification;

	String			filename;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent.getAction();
		if(action.equals(ACTION_PLAY))
			play(intent.getData().getPath());
		else if(action.equals(ACTION_PAUSE))
			pause();
		else if(action.equals(ACTION_RESET))
			reset();
		else if(action.equals(ACTION_STOP))
			stop();
		else if(action.equals(ACTION_INFO))
			getInfo(intent);
		return START_NOT_STICKY;	// Means we started the service, but don't want it to
						// restart in case it's killed.

	}

	public void play(String filename) {
		this.filename = null;
		if((player != null) && player.isPlaying()) {
			player.stopPlayer();
		}
		try {
			player = new RS03Player(filename);
			player.start();
			this.filename = filename;
			setUpAsForeground(getBasename(filename));
		} catch(Exception e) {
			Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
			Log.e("PlayerService", "Error: " + e.getMessage(), e);
		}
	}

	public void stop() {
		if(player == null)
			return;
		if(player.isPlaying())
			player.stopPlayer();
		stopForeground(true);
	}

	public void pause() {
		if(player == null)
			return;
		if(!player.isPlaying())
			return;
		player.pause();
	}

	public void reset() {
		if((player == null) || !player.isPlaying())
			play(filename);
		else {
			player.stopPlayer();
			play(filename);
		}
	}

	public void getInfo(Intent intent) {
		Bundle extras = intent.getExtras();
		Messenger messenger = (Messenger) extras.get("MESSENGER");
		Message msg = Message.obtain();
		msg.obj = filename;
		try {
			messenger.send(msg);
		} catch(RemoteException e) {
			Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
			Log.e("PlayerService", "Error: " + e.getMessage(), e);
		}
	}

	public final static String getBasename(String filename) {
		int last = filename.lastIndexOf('/');
		if(last == -1)
			return filename;
		if(last == (filename.length() - 1))
			return "";
		return filename.substring(last + 1);
	}

	/** Updates the notification. */
	void updateNotification(String text) {
		PendingIntent pi = PendingIntent.getActivity(
				getApplicationContext(), 0, new Intent(
						getApplicationContext(),
						RS03PlayerActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		notification.setLatestEventInfo(getApplicationContext(),
				"RS03Player", text, pi);
		nm.notify(NOTIFICATION_ID, notification);
	}

	/**
	 * Configures service as a foreground service. A foreground service is a
	 * service that's doing something the user is actively aware of (such as
	 * playing music), and must appear to the user as a notification. That's
	 * why we create the notification here.
	 */
	void setUpAsForeground(String text) {
		PendingIntent pi = PendingIntent.getActivity(
				getApplicationContext(), 0, new Intent(
						getApplicationContext(),
						RS03PlayerActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		notification = new Notification();
		notification.tickerText = text;
		notification.icon = R.drawable.ic_stat_playing;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.setLatestEventInfo(getApplicationContext(),
				"RS03Player", text, pi);
		startForeground(NOTIFICATION_ID, notification);
	}

	@Override
	public void onCreate() {
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		audio = (AudioManager) getSystemService(AUDIO_SERVICE);
	}

	@Override
	public void onDestroy() {
		Log.d("PlayerService", "Service destroyed");
		if(player != null)
			player.stopPlayer();
		stopForeground(true);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
