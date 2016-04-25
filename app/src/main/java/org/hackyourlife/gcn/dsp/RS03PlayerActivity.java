package org.hackyourlife.gcn.dsp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class RS03PlayerActivity extends Activity {
	String		filename;

	private Handler handler = new Handler() {
		public void handleMessage(Message message) {
			setInfo((String)message.obj);
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		Intent intent = getIntent();
		Uri data = intent.getData();
		if(data != null) {
			filename = data.getPath();
			//setInfo(filename);

			if(getLastNonConfigurationInstance() == null) {
				Intent i = new Intent(PlayerService.ACTION_PLAY);
				Uri uri = Uri.parse(data.toString());
				i.setData(uri);
				startService(i);
			}
		}
		Intent i = new Intent(PlayerService.ACTION_INFO);
		Messenger messenger = new Messenger(handler);
		i.putExtra("MESSENGER", messenger);
		startService(i);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	private void setInfo(String filename) {
		this.filename = filename;
		TextView currentFile = (TextView)findViewById(R.id.currentFile);
		currentFile.setText(filename);
	}

	public void onClick(View v) {
		Button button = (Button)v;
		switch(button.getId()) {
			case R.id.stop: {
				startService(new Intent(PlayerService.ACTION_STOP));
				break;
			}
			case R.id.pause: {
				startService(new Intent(PlayerService.ACTION_PAUSE));
				break;
			}
			case R.id.reset: {
				startService(new Intent(PlayerService.ACTION_RESET));
				break;
			}
		}
	}
}
