package airjaw.butterflyandroid;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.firebase.geofire.LocationCallback;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.util.ArrayList;

import android.net.Uri;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.VideoView;

import airjaw.butterflyandroid.Camera.CamSendMeetActivity;

public class MeetActivity extends AppCompatActivity {

    private static final String TAG = "MeetActivity";

    private static ArrayList<Media_Info> mediaIntroQueueList = new ArrayList<Media_Info>();
    private static ArrayList<String> mediaIntroQueueListTitles = new ArrayList<>();
    ArrayAdapter<String> stringAdapter;
    ListView mediaList;

    GeoLocation lastLocation;
    RelativeLayout buttonOverlay;
    SimpleExoPlayerView simpleExoPlayerView;
    SimpleExoPlayer simpleExoPlayer;

    private boolean shouldAutoPlay;
    private boolean shouldShowPlaybackControls;

    private int lastVideoPlaying;
    private boolean shouldResumeVideo;

    int selectedUserAtIndexPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meet);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        mediaList = (ListView) findViewById(R.id.mediaListView);

        stringAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                mediaIntroQueueListTitles);
        mediaList.setAdapter(stringAdapter);

        mediaList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String title =  (String) parent.getItemAtPosition(position);
                Log.i(TAG, "onitemclickListener: " + title);

                // get media
                Media_Info mediaSelected = mediaIntroQueueList.get(position);
                Log.i(TAG, "title: " + mediaSelected.getTitle());
                Log.i(TAG, "mediaID: " + mediaSelected.getMediaID());

                selectedUserAtIndexPath = position;

                playVideoAtCell(position);
            }
        });

        simpleExoPlayerView = (SimpleExoPlayerView) findViewById(R.id.exoPlayerVideoView);
        buttonOverlay = (RelativeLayout) findViewById(R.id.buttonOverlay);

        shouldAutoPlay = true;
        shouldShowPlaybackControls = false;
        shouldResumeVideo = false;

    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.i(TAG, "onStart");


        if (!shouldResumeVideo) {

            getUserLocation();

//        getLocalIntroductions(); // TODO: change back for production
            getIntroductionsForAdmin(); // TODO: comment out for production
        }

        else {
            playVideoAtCell(lastVideoPlaying);
        }
    }

    private void playVideoAtCell(final int cellNumber){

        getDownloadURL(cellNumber, new MeetActivityInterface() {
            @Override
            public void downloadURLCompleted(Uri uri) {
                Log.i(TAG, "playVideo");

                simpleExoPlayerView.setVisibility(View.VISIBLE);
                buttonOverlay.setVisibility(View.VISIBLE);

                // 1. Create a default TrackSelector
                Handler mainHandler = new Handler();
                BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
                TrackSelection.Factory videoTrackSelectionFactory =
                        new AdaptiveVideoTrackSelection.Factory(bandwidthMeter);
                TrackSelector trackSelector =
                        new DefaultTrackSelector(videoTrackSelectionFactory);

                // 2. Create a default LoadControl
                LoadControl loadControl = new DefaultLoadControl();

                // 3. Create the player
                simpleExoPlayer =
                        ExoPlayerFactory.newSimpleInstance(MeetActivity.this, trackSelector, loadControl);

                // Bind the player to the view.
                simpleExoPlayerView.setPlayer(simpleExoPlayer);

                // In ExoPlayer every piece of media is represented by MediaSource.
                // To play a piece of media you must first create a corresponding MediaSource and
                // then pass this object to ExoPlayer.prepare

                // Produces DataSource instances through which media data is loaded.
                String userAgent = Util.getUserAgent(MeetActivity.this, "Butterfly");
                DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(MeetActivity.this,
                        userAgent);

                // Produces Extractor instances for parsing the media data.
                ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
                // This is the MediaSource representing the media to be played.
                MediaSource videoSource = new ExtractorMediaSource(uri,
                        dataSourceFactory, extractorsFactory, null, null);

                // Loops the video indefinitely.
                LoopingMediaSource loopingSource = new LoopingMediaSource(videoSource);

                lastVideoPlaying = cellNumber;

                // Prepare the player with the source.
//                simpleExoPlayer.prepare(videoSource); // this doesn't loop
                simpleExoPlayer.prepare(loopingSource); // this loops
                simpleExoPlayerView.setUseController(shouldShowPlaybackControls);
                simpleExoPlayer.setPlayWhenReady(shouldAutoPlay);

            }
        });
    }

    private void getDownloadURL(int cellNumber, final MeetActivityInterface completion) {
        String mediaID = mediaIntroQueueList.get(cellNumber).getMediaID();

        // firebase storage
        Constants.storageMediaRef.child(mediaID).getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>()
        {
            @Override
            public void onSuccess(Uri downloadURL)
            {
                Log.i(TAG, "downloadURLCompleted: " + downloadURL.toString());
                completion.downloadURLCompleted(downloadURL);
            }
        });
    }

    // Menu icons are inflated just as they were with actionbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.tab_home:
                Log.i("menu", "home");

                Intent homeIntent = new Intent(this, HomeActivity.class);
                startActivity(homeIntent);
                return true;
            case R.id.tab_meet:
                Log.i("menu", "meet");

                Intent meetIntent = new Intent(this, MeetActivity.class);
                startActivity(meetIntent);

                return true;
            case R.id.tab_inbox:
                Log.i("menu", "inbox");

                Intent inboxIntent = new Intent(this, InboxActivity.class);
                startActivity(inboxIntent);

                return true;
            case R.id.tab_chat:
                Log.i("menu", "chat");

                Intent chatIntent = new Intent(this, ChatActivity.class);
                startActivity(chatIntent);

                return true;
            case R.id.tab_settings:
                Log.i("menu", "settings");

                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);

                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }
    private void getLocalIntroductions() {

        final ArrayList<String> mediaLocationKeysWithinRadius = new ArrayList<String>();

        lastLocation = GeoFireGlobal.getInstance().getLastLocation();

        if (lastLocation != null) {
            GeoQuery circleQuery = Constants.geoFireMedia.queryAtLocation(lastLocation, 50);

            circleQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                @Override
                public void onKeyEntered(String key, GeoLocation location) {
                    System.out.println(String.format("Key %s entered the search area at [%f,%f]", key, location.latitude, location.longitude));
                    Log.i("Query: Key added", key.toString());

                    mediaLocationKeysWithinRadius.add(key);

                }
                @Override
                public void onKeyExited(String key) {

                }

                @Override
                public void onKeyMoved(String key, GeoLocation location) {

                }

                @Override
                public void onGeoQueryReady() {

                }

                @Override
                public void onGeoQueryError(DatabaseError error) {
                    Log.i("GeoQueryError:", error.toString());
                }
            });
        }
        else {
            // last location is null
            Log.i("LOCATION", "last location null)");
        }

        // TODO: FILTER: BLOCK LIST

        long currentTimeInMilliseconds = System.currentTimeMillis();
        System.out.println("currentTimeInMilliseconds:" + currentTimeInMilliseconds);

        long twentyFourHoursStartTime = currentTimeInMilliseconds - Constants.twentyFourHoursInMilliseconds;
        long endTime = currentTimeInMilliseconds;
        long monthStartTime = currentTimeInMilliseconds - (Constants.twentyFourHoursInMilliseconds * 31);

        // GENDER FILTER
        Context context = this;
        SharedPreferences settingsPrefs = context.getSharedPreferences(Constants.USER_SETTINGS_PREFS, MODE_PRIVATE);
        final boolean showMen = settingsPrefs.getBoolean("meetMenSwitch", false);
        final boolean showWomen = settingsPrefs.getBoolean("meetWomenSwitch", false);

        // custom query (set to one month currently)
        Query twentyFourHourqueryRef = Constants.MEDIA_INFO_REF.orderByChild("timestamp").startAt(twentyFourHoursStartTime).endAt(endTime);

        // Read from the database
        twentyFourHourqueryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.

                mediaIntroQueueList.clear();
                mediaIntroQueueListTitles.clear();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String name = (String)snapshot.child("name").getValue();
                    Log.i(TAG, "name: " + name);

                    String gender = (String)snapshot.child("gender").getValue();
                    Log.i(TAG, "gender: " + gender);

                    String mediaID = (String)snapshot.child("mediaID").getValue();
                    Log.i(TAG, "mediaID: " + mediaID);

                    String title = (String)snapshot.child("title").getValue();
                    Log.i(TAG, "title: " + title);

                    String userID = (String)snapshot.child("userID").getValue();
                    Log.i(TAG, "userID: " + userID);

                    long timestamp = (Long)snapshot.child("timestamp").getValue();
                    Log.i(TAG, "timestamp: " + timestamp);

                    long age = (Long)snapshot.child("age").getValue();

                    if (showMen && showWomen) {
                        // show all users
                    }
                    else if (!showMen && !showWomen) {
                        // show all users
                    }
                    else if (userID.equals(Constants.userID)) {
                        // always show user's own intro
                    }
                    else if (!showMen && showWomen) {
                        if (gender.equals("male")) {
                            continue; // exit loop for this child
                        }
                    }
                    else if (showMen && !showWomen) {
                        if (gender.equals("female")) {
                            continue; // exit loop for this child
                        }
                    }

                    Media_Info mediaInfoDic = new Media_Info(age, gender, mediaID, name, title, userID);
                    mediaInfoDic.setTimestamp(timestamp);
                    // continue filter list by geographical radius:
                    //  key is found in the array of local mediaID from circleQuery

                    if (mediaLocationKeysWithinRadius.contains(mediaID)) {

                        Log.i("media within radius: ", mediaID);

                        mediaIntroQueueList.add(mediaInfoDic);
                        mediaIntroQueueListTitles.add(title);

                        }
                    else {
                        Log.i("media not in radius: ", mediaID);
                    }
                }

                stringAdapter.notifyDataSetChanged();

                Log.i("ARRAY", mediaIntroQueueListTitles.toString());
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    private void getIntroductionsForAdmin() {

        // special method for debugging/testing purposes
        // extends time filter to a month (or more) and removes location filter

        final ArrayList<String> mediaLocationKeysWithinRadius = new ArrayList<String>();

        lastLocation = GeoFireGlobal.getInstance().getLastLocation();

        if (lastLocation != null) {
            GeoQuery circleQuery = Constants.geoFireMedia.queryAtLocation(lastLocation, 50);

            circleQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                @Override
                public void onKeyEntered(String key, GeoLocation location) {
                    System.out.println(String.format("Key %s entered the search area at [%f,%f]", key, location.latitude, location.longitude));
                    Log.i("Query: Key added", key.toString());

                    mediaLocationKeysWithinRadius.add(key);

                }
                @Override
                public void onKeyExited(String key) {

                }

                @Override
                public void onKeyMoved(String key, GeoLocation location) {

                }

                @Override
                public void onGeoQueryReady() {

                }

                @Override
                public void onGeoQueryError(DatabaseError error) {
                    Log.i("GeoQueryError:", error.toString());
                }
            });
        }
        else {
            // last location is null
            Log.i("LOCATION", "last location null)");
        }

        // TODO: FILTER: BLOCK LIST

        long currentTimeInMilliseconds = System.currentTimeMillis();
        System.out.println("currentTimeInMilliseconds:" + currentTimeInMilliseconds);

        long startTime = currentTimeInMilliseconds - Constants.twentyFourHoursInMilliseconds;
        long endTime = currentTimeInMilliseconds;
        long monthStartTime = currentTimeInMilliseconds - (Constants.twentyFourHoursInMilliseconds * 31);
        long twoMonthStartTime = currentTimeInMilliseconds - (Constants.twentyFourHoursInMilliseconds * 61);


        // GENDER FILTER
        Context context = this;
        SharedPreferences settingsPrefs = context.getSharedPreferences(Constants.USER_SETTINGS_PREFS, MODE_PRIVATE);
        final boolean showMen = settingsPrefs.getBoolean("meetMenSwitch", false);
        final boolean showWomen = settingsPrefs.getBoolean("meetWomenSwitch", false);

        // custom query (set to one month currently)
        Query twentyFourHourqueryRef = Constants.MEDIA_INFO_REF.orderByChild("timestamp").startAt(twoMonthStartTime).endAt(endTime);

        // Read from the database
        twentyFourHourqueryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.

                mediaIntroQueueList.clear();
                mediaIntroQueueListTitles.clear();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String name = (String)snapshot.child("name").getValue();
                    Log.i(TAG, "name: " + name);

                    String gender = (String)snapshot.child("gender").getValue();
                    Log.i(TAG, "gender: " + gender);

                    String mediaID = (String)snapshot.child("mediaID").getValue();
                    Log.i(TAG, "mediaID: " + mediaID);

                    String title = (String)snapshot.child("title").getValue();
                    Log.i(TAG, "title: " + title);

                    String userID = (String)snapshot.child("userID").getValue();
                    Log.i(TAG, "userID: " + userID);

                    long timestamp = (Long)snapshot.child("timestamp").getValue();
                    Log.i(TAG, "timestamp: " + timestamp);

                    long age = (Long)snapshot.child("age").getValue();

                    if (showMen && showWomen) {
                        // show all users
                    }
                    else if (!showMen && !showWomen) {
                        // show all users
                    }
                    else if (userID.equals(Constants.userID)) {
                        // always show user's own intro
                    }
                    else if (!showMen && showWomen) {
                        if (gender.equals("male")) {
                            continue; // exit loop for this child
                        }
                    }
                    else if (showMen && !showWomen) {
                        if (gender.equals("female")) {
                            continue; // exit loop for this child
                        }
                    }

                    Media_Info mediaInfoDic = new Media_Info(age, gender, mediaID, name, title, userID);
                    mediaInfoDic.setTimestamp(timestamp);
                    // continue filter list by geographical radius:
                    //  key is found in the array of local mediaID from circleQuery

//                    if (mediaLocationKeysWithinRadius.contains(mediaID)) {
//
//                        Log.i("media within radius: ", mediaID);

                        mediaIntroQueueList.add(mediaInfoDic);
                        mediaIntroQueueListTitles.add(title);

                  //  }
//                    else {
//                        Log.i("media not in radius: ", mediaID);
//                    }
                }

                stringAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    private void getUserLocation() {

        // get user location
        Constants.geoFireUsers.getLocation(Constants.userID, new LocationCallback() {
            @Override
            public void onLocationResult(String key, GeoLocation location) {
                if (location != null) {
                    System.out.println(String.format("The location for key %s is [%f,%f]", key, location.latitude, location.longitude));
                    lastLocation = location;
                } else {
                    System.out.println(String.format("There is no location for key %s in GeoFire", key));
                    // TODO: request location permission
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.err.println("There was an error getting the GeoFire location: " + databaseError);
            }
        });
    }

    public void showReportAction(View view) {
        Log.i(TAG, "Report Button Clicked");
    }

    public void closeVideo(View view) {
        Log.i(TAG, "Pass Button Clicked");
        buttonOverlay = (RelativeLayout) findViewById(R.id.buttonOverlay);

        simpleExoPlayer.stop();
        simpleExoPlayer.release();
        simpleExoPlayerView.setVisibility(View.INVISIBLE);
        buttonOverlay.setVisibility(View.INVISIBLE);

        shouldResumeVideo = false;
    }

    public void sendMeet(View view) {

        String toUserID = mediaIntroQueueList.get(selectedUserAtIndexPath).getUserID();
        Log.i(TAG, "Meet Button Clicked: trying to meet: " +  toUserID);


        if (!toUserID.equals(Constants.userID)){
            // currentlyPlayingVideo = false;

            simpleExoPlayer.stop();
            simpleExoPlayer.release();

            shouldResumeVideo = true;
            // open CamSendMeetActivity
            Intent camIntent = new Intent(this, CamSendMeetActivity.class);
            camIntent.putExtra("toUserID", toUserID);
            startActivity(camIntent);
//
//            simpleExoPlayerView.setVisibility(View.INVISIBLE);
//            buttonOverlay.setVisibility(View.INVISIBLE);
        }
        else {
            String reason = "Self Meet";
            showMeetErrorAlert(reason);
        }

    }
    private void showMeetErrorAlert(String reason) {
        String title = "Error";
        String message = "We ran into an error";
        if (reason.equals("Self Meet")){
            message = "You can't meet yourself!";
        }
        AlertDialog alertDialog = new AlertDialog.Builder(MeetActivity.this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Aww, OK...",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }
}
