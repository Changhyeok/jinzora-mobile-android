package org.jinzora.playback;

interface PlaybackInterface {
	void playlist( in String pl, in int addtype );
	void pause();
	void stop();
	void prev();
	void next();
	void clear();
	void jumpTo( in int pos );
	void queueNext( in int pos );
	void onDestroy();
	void setBaseURL( in String url );
	void setPlaybackDevice( in String playerClass, in String arg );
	
	void onCallBegin();
	void onCallEnd();
	
	List<String> getPlaylist();
	int getPlaylistPos();
	
	String playbackIPC ( in String input );
	boolean isPlaying();
}