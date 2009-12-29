package org.jinzora.playback.players;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jinzora.Jinzora;
import org.jinzora.R;
import org.jinzora.playback.PlaybackService;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class LocalDevice extends PlaybackDevice {
	protected PlaybackService mService;
	protected MediaPlayer mp = null;
	protected int pos;
	protected List<String> trackNames;
	
	protected static String UNKNOWN_TRACK = "Unknown Track";
	
	
	public LocalDevice() {
		this.mService = PlaybackService.getInstance();
		mp = new MediaPlayer();
		
		mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
			
			public void onCompletion(MediaPlayer arg0) {
				try {
					next();
				} catch (RemoteException e) {

				}
			}
		});
		
		mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer _mp) {
				_mp.start();
				mService.notifyPlaying();
			}
		});
		
		/*
		mp.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
			@Override
			public void onBufferingUpdate(MediaPlayer _mp, int percent) {
				Log.d("jinzora","buffering: " + percent);
			}
		});
		*/
		
		mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer arg0, int arg1, int arg2) {
				Log.d("jinzora","media player error " + arg1 + ", " + arg2);
				Log.d("jinzora", "error track was " + pos + ", " + playlist.get(pos));
				return true;
			}
		});
		
		playlist = new ArrayList<String>();
		trackNames = new ArrayList<String>();
		
		pos = 0;
	}

	private static LocalDevice instance = null;
	public static LocalDevice getInstance(String arg) {
		if (instance == null) {
			instance = new LocalDevice();
		}
		return instance;
	}
	
	public void onDestroy() {		
		/*mp.stop();
		mp.release();
		mp = null;
		playlist = null;*/
	}
	
	@Override
	public boolean isPlaying() {
		return (mp != null && mp.isPlaying());
	}
	
	@Override
	public void clear() throws RemoteException {
		playlist.clear();
		mp.stop();
		mService.notifyPaused();
	}

	@Override
	public synchronized void next() throws RemoteException {
		if (nextQueuedPos >= 0) {
			int i = nextQueuedPos;
			nextQueuedPos = -1;
			jumpTo(i);
		}
		else if (++pos >= playlist.size()) {
			pos = 0;
		} else {
			jumpTo(pos);
		}
	}

	@Override
	public synchronized void pause() throws RemoteException {
		if (!mp.isPlaying()) {
			mp.start();
			mService.notifyPlaying();
			
		} else {
			mp.pause();
			mService.notifyPaused();
		}
	}

	@Override
	public synchronized void prev() throws RemoteException {
		if (pos > 0) {
			pos--;
		}
		jumpTo(pos);
	}

	@Override
	public synchronized void jumpTo(final int pos) throws RemoteException {
		try {
			this.pos=pos;
			mp.reset();
			mp.setDataSource(playlist.get(pos));
			mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
			
			mp.prepareAsync();
		} catch (Exception e) {
			Log.e("jinzora","Error changing media (pos=" + pos + ", len=" + playlist.size() + ")",e);
			if (pos < playlist.size()) Log.e("jinzora", "Content: " + playlist.get(pos));
		}
	}

	public synchronized void playlist(String urlstr, int addType) {
		try {
			URL url = new URL(urlstr);
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			InputStream inStream = conn.getInputStream();
			conn.connect();
			
			if (addType == ADD_REPLACE) {
				playlist = new ArrayList<String>();
				trackNames = new ArrayList<String>();
			}
			
			List<String> endList = new ArrayList<String>();
			List<String> endListNames = new ArrayList<String>();
			if (addType == ADD_CURRENT) {
				while (playlist.size() > pos+1) {
					endList.add(playlist.remove(pos+1));
					endListNames.add(trackNames.remove(pos+1));
				}
			}

			BufferedReader br = new BufferedReader(new InputStreamReader(inStream));
			String line = null; 
			String lastLine = null;
			
			line = br.readLine();
			while (line != null) {
				if (line.charAt(0) != '#') {
					try {
						URL track = new URL(line);
					    playlist.add(track.toExternalForm());
					    if (lastLine.charAt(0) == '#') {
					    	int pos;
					    	if (-1 != (pos = lastLine.indexOf(','))) {
					    		trackNames.add(lastLine.substring(pos+1,lastLine.length()));
					    	} else {
					    		trackNames.add(UNKNOWN_TRACK);
					    	}
					    } else {
					    	trackNames.add(UNKNOWN_TRACK);
					    }
					} catch (Exception e) {
						// probably a comment line
					}
				}
				
				lastLine = line;
				line = br.readLine();
			}
			
			playlist.addAll(endList);
			trackNames.addAll(endListNames);
			
			if (addType == ADD_REPLACE || mp == null) {
				jumpTo(0);
			} else if (!mp.isPlaying()) {
				jumpTo(pos);
			}
			
		} catch (Exception e) {
			Log.e("jinzora","Error playing media",e);
		}
	}
	
	
	@Override
	public void stop() throws RemoteException {
		mp.stop();
		mService.notifyPaused();
	}

	@Override
	public IBinder asBinder() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String playbackIPC(String params) throws RemoteException {
		
		return null;
	}

	@Override
	public List<String> getPlaylist() throws RemoteException {
		return trackNames;
	}

	@Override
	public int getPlaylistPos() throws RemoteException {
		return pos;
	}

	@Override
	public String getArtistName() throws RemoteException {
		String entry = trackNames.get(pos);
		if (entry.contains(" - ")) {
			return entry.substring(0,entry.indexOf(" - "));
		} else {
			return null;
		}
	}

	@Override
	public String getTrackName() throws RemoteException {
		String entry = trackNames.get(pos);
		if (entry.contains(" - ")) {
			return entry.substring(3+entry.indexOf(" - "));
		} else {
			return entry;
		}
	}
}
