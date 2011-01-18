package org.jinzora.playback;

interface PlaybackInterface {
	void updatePlaylist( in String pl, in int addtype );
	void pause();
	void play();
	void playpause();
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
	
	void registerRemoteControl();
	void unregisterRemoteControl();
	
	List<String> getPlaylistNames();
	List<String> getPlaylistURLs();
	int getPlaylistPos();
	int getSeekPos();
	
	// TODO: make Playable parcel and return it here and in getPlaylist()
	String getArtistName();
	String getTrackName();
	
	String playbackIPC ( in String input );
	boolean isPlaying();
}