package org.hackyourlife.gcn.dsp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.RandomAccessFile;
import java.io.File;

public class RS03Player extends Thread {
	private Stream		stream;
	private boolean		stop;
	private boolean		playing;
	private AudioTrack	output;

	public RS03Player(String filename) throws Exception {
		init(filename);

		int channelConfig;
		switch(stream.getChannels()) {
			case 1:
				channelConfig = AudioFormat.CHANNEL_OUT_MONO;
				break;
			case 2:
				channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
				break;
			case 4:
				channelConfig = AudioFormat.CHANNEL_OUT_QUAD;
				break;
			default:
				throw new Exception(
						stream.getChannels()
						+ " channels not supported!");
		}

		int bufferSize = AudioTrack.getMinBufferSize(
				(int) stream.getSampleRate(),
				channelConfig,
				AudioFormat.ENCODING_PCM_16BIT) * 16;
		int preferredSize = (int) stream
				.getPreferredBufferSize();
		if(bufferSize < preferredSize)
			bufferSize = preferredSize;

		output = new AudioTrack(
				AudioManager.STREAM_MUSIC,
				(int) stream.getSampleRate(),
				channelConfig,
				AudioFormat.ENCODING_PCM_16BIT,
				bufferSize, AudioTrack.MODE_STREAM);

		playing = false;
	}

	private void init(String filename) throws Exception {
		try {
			String filenameLeft = null;
			String filenameRight = null;
			int lext = filename.lastIndexOf('.');
			if(lext > 1) {
				char[] data = filename.toCharArray();
				char c = data[lext - 1];
				if(c == 'L') {
					data[lext - 1] = 'R';
					filenameLeft = filename;
					filenameRight = new String(data);
				} else if(c == 'R') {
					data[lext - 1] = 'L';
					filenameLeft = new String(data);
					filenameRight = filename;
				}
			}
			RandomAccessFile file = new RandomAccessFile(filename, "r");
			stream = null;
			try {
				stream = new BRSTM(file);
			} catch(FileFormatException e) {
				try {
					stream = new RS03(file);
				} catch(FileFormatException ex) {
					if(filenameLeft != null
							&& new File(filenameLeft).exists()
							&& new File(filenameRight).exists()) {
						file.close();
						RandomAccessFile left = new RandomAccessFile(filenameLeft, "r");
						RandomAccessFile right = new RandomAccessFile(filenameRight, "r");
						try {
							stream = new DSP(left, right);
						} catch(FileFormatException exc) {
							left.close();
							right.close();
							file = new RandomAccessFile(filename, "r");
							stream = new DSP(file);
						}
					} else
						stream = new DSP(file);
				}
			}
			System.out.printf("%d Channels, %d Hz\n", stream.getChannels(), stream.getSampleRate());
		} catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void run() {
		stop = false;
		playing = true;
		setPriority(Thread.MAX_PRIORITY);
		try {
			short[] buffer;
			output.play();

			while(stream.hasMoreData() && !stop) {
				if(output.getPlayState() == AudioTrack.PLAYSTATE_PAUSED) {
					try {
						Thread.sleep(100);
					} catch(InterruptedException e) { }
					continue;
				}
				synchronized(stream) {
					buffer = stream.decode16();
				}
				output.write(buffer, 0, buffer.length);
			}

			playing = false;

			output.stop();
			output.release();
			stream.close();
		} catch(Exception e) {
			//Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG);
			Log.e("Player", "Error: " + e.getMessage(), e);
		}
	}

	public void stopPlayer() {
		stop = true;
	}

	public void reset() {
		synchronized(stream) {
			try {
				stream.reset();
			} catch(Exception e) {
				Log.e("Player", "Error: " + e.getMessage(), e);
			}
		}
	}

	public void pause() {
		if(output == null)
			return;
		if(output.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
			output.pause();
		else if(output.getPlayState() == AudioTrack.PLAYSTATE_PAUSED)
			output.play();
	}

	public boolean isPlaying() {
		return playing;
	}
}
