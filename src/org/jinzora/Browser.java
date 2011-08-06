package org.jinzora;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jinzora.android.R;
import org.jinzora.download.DownloadService;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import mobisocial.nfc.NdefFactory;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

public class Browser extends ListActivity {
	private JzMediaAdapter allEntriesAdapter = null;
	private JzMediaAdapter visibleEntriesAdapter = null;
	
	protected String browsing;
	protected LayoutInflater mInflater = null;
	private String curQuery = "";
	private boolean mContentLoaded = false;
	private ListView mListView;
	private boolean mButtonNav = false;

	Handler mAddEntriesHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			allEntriesAdapter.add(msg.getData());
			if (allEntriesAdapter != visibleEntriesAdapter) {
				if (matchesFilter(msg.getData().getString("name"))) {
					visibleEntriesAdapter.add(msg.getData());
				}
			}
		}
	};
	
	Handler mEntriesCompleteHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			allEntriesAdapter.finalize();
		}
	};
	
	class PopulateListAsyncTask extends AsyncTask<Void, String, Void> {
		private ProgressDialog mDialog=null;
		private InputStream inStream=null;
		private String mEncoding;
		private boolean waitingForConn=true;
		
		public PopulateListAsyncTask() {
			
		}
		
		@Override
		protected void onPreExecute() {
			if (mDialog == null) {
				mDialog = new ProgressDialog(Browser.this);
				//mDialog.setTitle("Connecting to media server");
				mDialog.setMessage(Browser.this.getResources().getText(R.string.loading));
				mDialog.setIndeterminate(true);
				mDialog.setCancelable(true);
				mDialog.setOnCancelListener(
						new DialogInterface.OnCancelListener() {
							@Override
							public void onCancel(DialogInterface arg0) {
								
							}
						});
			}	
			
			// wait a bit before showing dialog
			new Thread() {
				public void run() {
					try {
						Thread.sleep(2000);
					} catch (Exception e){}
					
					if (waitingForConn) {
						Browser.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								mDialog.show();
							}
						});
					}
				};
			}.start();
		}
		
		/**
		 * Our background task is to connect to the remote host.
		 * Populating the list occurs during postExecute.
		 */
		@Override
		protected Void doInBackground(Void... arg0) {
			try {
				URL url = new URL(browsing);
    			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    			conn.setConnectTimeout(20000);
    			inStream = conn.getInputStream();
    			conn.connect();
    			mEncoding = conn.getContentEncoding();
    			
    			mContentLoaded=true;
    			waitingForConn=false;
    			return null;
    		} catch (Exception e) {
    			waitingForConn=false;
    			return null;
    		}
		}
		
		@Override
		protected void onPostExecute(Void result) {
			mDialog.hide();
			if (inStream == null) {
				try {
	    			Log.w("jinzora","could not connect to server");
	    			
	    			JzMediaAdapter adapter = new JzMediaAdapter(Browser.this, new ArrayList<Bundle>());
	        		setContentView(R.layout.browse);
	        		setListAdapter(adapter);
	        		
	        		((TextView)findViewById(R.id.browse_notice)).setText(R.string.connection_failed);
	        		findViewById(R.id.browse_notice).setVisibility(View.VISIBLE);
    			} catch (Exception e2) {
    				Log.e("jinzora","error clearing view",e2);
    			}
			}

			mListView = getListView();
			mListView.setVisibility(View.VISIBLE);
    		setListAdapter(allEntriesAdapter);

    		mListView.setOnItemClickListener(mListClickListener);
    		mListView.setOnItemLongClickListener(mListLongClickListener);
			mListView.setOnKeyListener(mListKeyListener);

    		try {
	    		// Asynchronous, but no ProgressDialog.
	    		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
				factory.setNamespaceAware(true);
				XmlPullParser xpp;
	    		xpp = factory.newPullParser();
				xpp.setInput(inStream, mEncoding);
				
				populateList(xpp, inStream);
    		} catch  (Exception e) {
    			Log.e("jinzora","could not populate list",e);
    		}
		}
	}

    @Override
    public void onResume() {
        super.onResume();

        Intent inbound = getIntent();

        if (inbound.hasExtra("playlink")) {
            Jinzora.mNfc.share(NdefFactory.fromUri(Uri.parse(inbound.getStringExtra("playlink"))));
        }

        String newBrowsing = null;
        if (null != inbound.getStringExtra(getPackageName() + ".browse")) {
            // todo: get rid of this static.
            newBrowsing = inbound.getStringExtra(getPackageName() + ".browse");
        } else {
            newBrowsing = getHomeURL();
            if (null == newBrowsing) {
                startActivity(new Intent(this, Preferences.class));
                return;
            }
        }

        if (browsing == null || !browsing.equals(newBrowsing) || !mContentLoaded) {
            browsing = newBrowsing;
            doBrowsing();
        }
    }
	
	@Override
	protected void onPause() {
		super.onPause();
	}

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Jinzora.initContext(this);
        
        allEntriesAdapter = new JzMediaAdapter(Browser.this);
        visibleEntriesAdapter = allEntriesAdapter;
    }

    private String getHomeURL() {
    	String baseurl = Jinzora.getBaseURL();
   		if (baseurl == null) {
   			return null;
   		} else {
   			return baseurl + "&request=home";
   		}
    }

    private void doBrowsing() {
    	try {
    		allEntriesAdapter.clear();
    		setContentView(R.layout.browse);
    		PopulateListAsyncTask connect = new PopulateListAsyncTask();
    		connect.execute();
    		
    	} catch (Exception e) {
    		Log.e("jinzora", "error", e);
    	}
    }

    public void populateList(final XmlPullParser xpp, final InputStream inStream) {
    	new Thread() {
    		@Override
			public void run() {
				try {
					int eventType = xpp.getEventType();
					while (eventType != XmlPullParser.END_DOCUMENT) {
						if(eventType == XmlPullParser.START_DOCUMENT) {
			
						} else if(eventType == XmlPullParser.END_DOCUMENT) {
			
						} else if(eventType == XmlPullParser.START_TAG && 
								(xpp.getName().equals("login"))) {
							
							
			        		((TextView)findViewById(R.id.browse_notice)).setText(R.string.bad_login);
			        		findViewById(R.id.browse_notice).setVisibility(View.VISIBLE);
			        		
							return;
						} else if(eventType == XmlPullParser.START_TAG && 
								(xpp.getName().equals("nodes") || xpp.getName().equals("browse") || xpp.getName().equals("tracks"))) {
			
							int depth = xpp.getDepth();
							xpp.next();
			
							Bundle item = null;
							while (!(depth == xpp.getDepth() && eventType == XmlPullParser.END_TAG)) {	            	  
								if (depth+1 == xpp.getDepth() && eventType == XmlPullParser.START_TAG) {
									item = new Bundle();
								} else if (depth+1 == xpp.getDepth() && eventType == XmlPullParser.END_TAG) {
									/*if (item.containsKey("album")) {
			            			  item.put("subfield1", item.get("album"));
			            			  if (item.containsKey("artist")) {
			            				  item.put("subfield2",item.get("artist"));
			            			  }
			            		  } else*/ if (item.containsKey("artist")){
			            			  item.putString("subfield1", item.getString("artist"));
			            		  }
			
			            		  /*
			            		  if (isActivityPaused) {
			            			  // A fairly gross hack to fix the bug
			            			  // when a user presses 'back'
			            			  // while the page is still loading.
			            			  // Probably a better way to handle it.
			            			  inStream.close();
			            			  Log.d("jinzora","killed it");
			            			  mContentLoaded=false; // force refresh on resume
			            			  return;
			            		  }
			            		  */
			            		  
			            		  Message m = mAddEntriesHandler.obtainMessage();
			            		  m.setData(item);
			            		  mAddEntriesHandler.sendMessage(m);
								}
			
								if (eventType == XmlPullParser.START_TAG && xpp.getName() != null && xpp.getName().equals("name")) {
									eventType = xpp.next();
									item.putString("name", xpp.getText());	           			  
								}
			
								if (eventType == XmlPullParser.START_TAG && xpp.getName() != null && xpp.getName().equals("artist")) {
									eventType = xpp.next();
									item.putString("artist", xpp.getText());
								}
			
								if (eventType == XmlPullParser.START_TAG && xpp.getName() != null && xpp.getName().equals("album")) {
									eventType = xpp.next();
									item.putString("album", xpp.getText());
								}
			
								if (eventType == XmlPullParser.START_TAG && xpp.getName() != null && xpp.getName().equals("playlink")) {
									eventType = xpp.next();
									item.putString("playlink", xpp.getText());
								}
			
								if (eventType == XmlPullParser.START_TAG && xpp.getName() != null && xpp.getName().equals("browse")) {
									eventType = xpp.next();
									item.putString("browse",xpp.getText());
								}
			
								eventType = xpp.next();
							}
						} else if(eventType == XmlPullParser.END_TAG) {
			
						} else if(eventType == XmlPullParser.TEXT) {

						}
						eventType = xpp.next();
					}
				} catch (Exception e) {
					Log.e("jinzora","Error processing XML",e);
				} finally {
					try {
						inStream.close();
					} catch (Exception e) {
						Log.w("jinzora","Error closing stream",e);
					}
				}
				
				mEntriesCompleteHandler.sendEmptyMessage(0);
    		}
    	}.start();
    }

    private boolean matchesFilter(String entry) {
    	return curQuery.length() == 0 || entry.toUpperCase().contains(curQuery);
    }
    
    @Override
    public synchronized boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
    	JzMediaAdapter workingEntries;
    	char c;
    	if ('\0' != (c = event.getMatch("ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890 ".toCharArray()))) {
    		curQuery = curQuery + c;
    		workingEntries = visibleEntriesAdapter;
    	} else if (keyCode == KeyEvent.KEYCODE_DEL) {
    		if (curQuery.length() > 0) {
    			curQuery = curQuery.substring(0, curQuery.length()-1);
    			if (curQuery.length() == 0) {
    				visibleEntriesAdapter = allEntriesAdapter;
        	        setListAdapter(allEntriesAdapter);
        	    	return super.onKeyUp(keyCode,event);
        		}
    			workingEntries = allEntriesAdapter;
    		} else {
    			return super.onKeyUp(keyCode,event);
    		}
    	} else {
    		return Jinzora.doKeyUp(this, keyCode, event);
    	}
    	
    	//TODO: support caching in the case of deletions?
    	// (as long as it doesn't use too much memory)
    	int count = workingEntries.getCount();
    	ArrayList<Bundle> newList = new ArrayList<Bundle>();
    	for (int i = 0; i < count; i++) {
    		if (matchesFilter(workingEntries.getItem(i).getString("name"))) {
    			newList.add(workingEntries.getItem(i));
    		}
    	}
    	
    	visibleEntriesAdapter = new JzMediaAdapter(this, newList);
        setListAdapter(visibleEntriesAdapter);
    	return super.onKeyUp(keyCode,event);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	return Jinzora.createMenu(menu);
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	Jinzora.menuItemSelected(featureId,item,this);
    	return super.onMenuItemSelected(featureId, item);
    }
    
    private List<Character> mSections = new ArrayList<Character>();
    int[] sectionHeaders = new int[26];
    int[] sectionPositions = new int[26];
    
    class JzMediaAdapter extends ArrayAdapter<Bundle> implements SectionIndexer {
    	private boolean isAlphabetical = true;
    	private boolean isFinishedLoading = false;
    	
    	Browser context;
    	public JzMediaAdapter(Browser context) {
    		super(context, R.layout.media_element);
    		this.context=context;
    	}
    	
    	public JzMediaAdapter(Browser context, List<Bundle>data) {
    		super(context,R.layout.media_element,data);
    	}

    	@Override
    	public View getView(final int position, View convertView, ViewGroup parent) {
    		View row;
    		if (convertView == null) {
    			if (Browser.this.mInflater == null) {
    				Browser.this.mInflater = LayoutInflater.from(context);
    			}
    			row = Browser.this.mInflater.inflate(R.layout.media_element, null);
    		} else {
    			row = convertView;
    		}

    		final Bundle item = (Bundle)this.getItem(position);
    		TextView label = (TextView)row.findViewById(R.id.media_el_name);
    		label.setText(item.getString("name"));

    		if (item.containsKey("subfield1")) {
    			label = (TextView)row.findViewById(R.id.media_el_subfield1);
    			label.setText(item.getString("subfield1"));
    		} else {
    			label = (TextView)row.findViewById(R.id.media_el_subfield1);
    			label.setText("");
    		}
    		if (item.containsKey("subfield2")) {
    			label = (TextView)row.findViewById(R.id.media_el_subfield2);
    			label.setText(item.getString("subfield2"));
    		} else {
    			label = (TextView)row.findViewById(R.id.media_el_subfield2);
    			label.setText("");
    		}

    		if (!item.containsKey("playlink")) {
    			row.findViewById(R.id.media_el_play).setVisibility(View.INVISIBLE);
    		} else {
               Button button = (Button)row.findViewById(R.id.media_el_play);
               button.setTag(R.id.media_el_play, item.getString("playlink"));
               button.setTag(R.id.media_el_subfield1, position);
               button.setOnClickListener(mPlayButtonClicked);
               button.setOnKeyListener(mPlayButtonKeyListener);
               if (mButtonNav) {
                   button.setFocusable(true);
               }
            }
    		return row;  
    	}

    	public Bundle getEntry(int pos) {
    		if (this.getCount() <= pos ) return null;
    		return (Bundle)this.getItem(pos);
    	}
    	
    	public String getEntryTitle(int pos) {
    		if (this.getCount() <= pos ) return null;
    		Bundle item = (Bundle)this.getItem(pos);
    		return item.getString("name");
    	}
    	
    	public boolean isPlayable(int pos) {
    		if (this.getCount() <= pos ) return false;
    		Bundle item = (Bundle)this.getItem(pos);
    		return item.containsKey("playlink");
    	}

    	// SectionIndexer
    	
    	@Override 
    	public void clear() {
    		super.clear();
    		
    		mSections.clear();
    		sectionHeaders = new int[26];
    	};
    	
    	@Override
    	public void add(Bundle object) {
    		super.add(object);
    		
    		int len = this.getCount();
    		String entry2 = getEntryTitle(len-1).toUpperCase();
    		if (len == 1) {
    			char c2 = entry2.charAt(0);
    			if (c2 <= 'A')
    				mSections.add('A');
    			else if (c2 >= 'Z')
    				mSections.add('Z');
    			else
    				mSections.add(c2);
    			return;
    		}
    		
    		String entry1 = getEntryTitle(len-2).toUpperCase();
    		if (entry1.length() == 0 || entry2.length() == 0) {
    			// what happened?
    			isAlphabetical = false;
    			return;
    		}
    		
    		if (entry1.compareTo(entry2) > 0) {
    			isAlphabetical = false;
    		} else if (isAlphabetical) {
    				char c = entry2.charAt(0);
    				if (c < 'A') c = 'A';
					else if (c > 'Z') c = 'Z';
    				
    				if (mSections.get(mSections.size()-1) != c) {
    					mSections.add(c);
    					sectionHeaders[mSections.size()-1]=len-1;
    					sectionPositions[c-'A']=mSections.size()-1;
    				}
    		}
    	}
    	
		@Override
		public int getPositionForSection(int sec) {
			return sectionHeaders[sec];
		}

		@Override
		public int getSectionForPosition(int pos) {
			String entry = getEntryTitle(pos);
			if (entry != null && entry.length() > 0) {
				char c = Character.toUpperCase(entry.charAt(0));
				if (c <= 'A') {
					return 0;
				}
				if (c >= 'Z') {
					return sectionHeaders.length-1;
				}
				
				return sectionPositions[c-'A'];
			}
			return 0;
		}

		@Override
		public Object[] getSections() {
			if (isAlphabetical && isFinishedLoading) {
				return mSections.toArray();
			}
			return null;
		}
		
		// Called when all data has been loaded
		public void finalize() {
			isFinishedLoading = true;
			((ListView)findViewById(android.R.id.list)).setFastScrollEnabled(true);
		}
    }

    private AdapterView.OnItemClickListener mListClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, final int position, long id) {
            try {
                String browse = null;
                if (null == (browse = visibleEntriesAdapter.getItem(position).getString("browse"))) {
                    if (visibleEntriesAdapter.getItem(position).containsKey("playlink")) {
                        new Thread() {
                            public void run() {
                                try {
                                    Jinzora.doPlaylist(visibleEntriesAdapter.getItem(position).getString("playlink"),
                                                  Jinzora.getAddType());
                                } catch (Exception e) {
                                    Log.e("jinzora","Error playing media",e);
                                }
                            }
                        }.start();
                    }
                    return;
                }

                Intent outbound = new Intent(Browser.this, Jinzora.class);
                if (visibleEntriesAdapter.getItem(position).containsKey("playlink")) {
                    outbound.putExtra("playlink", visibleEntriesAdapter.getItem(position).getString("playlink"));
                }
                outbound.putExtra(getPackageName()+".browse", browse);
                startActivity(outbound);
            } catch (Exception e) {
                Log.e("jinzora","Error during listItemClick",e);
            }
        }
    };

    private AdapterView.OnItemLongClickListener mListLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent,
                View view, final int listPosition, long id) {

            final CharSequence[] entryOptions = {"Share", "Replace current playlist", "Queue to end of list", "Queue next", "Download to device" };
            if (!visibleEntriesAdapter.isPlayable(listPosition)) return false;
            new AlertDialog.Builder(Browser.this)
                .setTitle(visibleEntriesAdapter.getEntryTitle(listPosition))
                .setItems(entryOptions, 
                        new AlertDialog.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int entryPos) {
                                final Bundle item = visibleEntriesAdapter.getEntry(listPosition);
                                switch (entryPos) {
                                case 0:
                                    // Share
                                    Intent share = new Intent("android.intent.action.SEND");
                                    share.setType("audio/x-mpegurl")
                                        .putExtra(Intent.EXTRA_TEXT, item.getString("playlink"));
                                    Browser.this
                                        .startActivity(Intent.createChooser(share, "Share playlist..."));
                                    break;
                                case 1:
                                case 2:
                                case 3:
                                    // Play, Queue
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            try {
                                                Jinzora.doPlaylist(item.getString("playlink"), entryPos-1);
                                            } catch (Exception e) {
                                                Log.e("jinzora","Error in longpress event",e);
                                            } finally {
                                                dialog.dismiss();
                                            }
                                        }
                                    }.start();
                                    break;
                                case 4:
                                    // Download to device
                                    try {
                                        Intent dlIntent = new Intent(DownloadService.Intents.ACTION_DOWNLOAD_PLAYLIST);
                                        dlIntent.putExtra("playlist", item.getString("playlink"));
                                        dlIntent.setClass(Browser.this, DownloadService.class);
                                        startService(dlIntent);
                                        /*Jinzora
                                          .sDlConnection
                                          .getBinding()
                                          .downloadPlaylist(item.getString("playlink"));*/
                                    } catch (Exception e) {
                                        Log.d("jinzora","Error downloading playlist",e);
                                    }
                                    // Add menu entry
                                }
                            }
                        }
                )
                .create().show();
            
            return true;
        }  
    };

    private View.OnKeyListener mListKeyListener = new View.OnKeyListener() {
        @Override
        public synchronized boolean onKey(View view, int keyCode, KeyEvent event) {
            if (KeyEvent.KEYCODE_DPAD_RIGHT == keyCode && event.getAction() == KeyEvent.ACTION_UP) {
                View v = mListView.getSelectedView();
                if (v == null) return false;
                v = v.findViewById(R.id.media_el_play);
                if (v.getVisibility() == View.VISIBLE) {
                    mListView.setItemsCanFocus(true);
                    int c = mListView.getChildCount();
                    for (int i = 0; i < c; i++) {
                        View u = mListView.getChildAt(i).findViewById(R.id.media_el_play);
                        u.setFocusable(true);
                    }
                    v.requestFocus(View.FOCUS_RIGHT);
                    mButtonNav = true;
                }
                return true;   
            }
            return false;
        }
    };

    private View.OnClickListener mPlayButtonClicked = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            new Thread() {
                public void run() {
                    try {
                        String item = (String)v.getTag(R.id.media_el_play);
                        Jinzora.doPlaylist(item, Jinzora.getAddType());
                    } catch (Exception e) {
                        Log.e("jinzora","Error playing media",e);
                    }
                }
            }.start();
        }
    };

    private View.OnKeyListener mPlayButtonKeyListener = new View.OnKeyListener() {
        @Override
        public synchronized boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                int c = mListView.getChildCount();
                for (int i = 0; i < c; i++) {
                    View u = mListView.getChildAt(i);
                    u.findViewById(R.id.media_el_play).setFocusable(false);
                }
                mListView.setItemsCanFocus(false);
                mListView.requestFocus(View.FOCUS_LEFT);
                mListView.setSelection((Integer)v.getTag(R.id.media_el_subfield1));
                mButtonNav = false;
                return true;
            }
            return false;
        }
    };
}