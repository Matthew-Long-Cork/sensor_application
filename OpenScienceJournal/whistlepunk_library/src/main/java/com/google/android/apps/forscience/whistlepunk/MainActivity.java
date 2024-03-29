/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import android.Manifest;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;

import android.bluetooth.BluetoothManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.blew.BleSensorManager;
import com.google.android.apps.forscience.whistlepunk.blew.Exiter;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier;

import com.google.android.apps.forscience.whistlepunk.intro.DatabaseLinkSetup;
import com.google.android.apps.forscience.whistlepunk.project.ExperimentListFragment;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewActivity;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    public static final String ARG_SELECTED_NAV_ITEM_ID = "selected_nav_item_id";
    public static final String ARG_USE_PANES = "use_panes";
    protected static final int NO_SELECTED_ITEM = -1;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitleToRestore;
    private FeedbackProvider mFeedbackProvider;
    private NavigationView mNavigationView;
    private MultiTouchDrawerLayout mDrawerLayout;
    private int mSelectedItemId = NO_SELECTED_ITEM;
    private boolean mIsRecording = false;
    public static BluetoothAdapter BLUETOOTH_ADAPTER;

    Boolean isDatabaseLinkSetup;

    /** Receives an event every time the activity pauses */
    RxEvent mPause = new RxEvent();

    private SharedPreferences storedData;
    private BluetoothAdapter bluetoothAdapter;
    final int REQUEST_ENABLE_BT = 210;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        bindExiter();


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, 2);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, 2);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WAKE_LOCK}, 2);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, 2);



        BleSensorManager.enable();

        setContentView(R.layout.activity_main);

        super.onCreate(savedInstanceState);


        WhistlePunkApplication.getPerfTrackerProvider(this).onActivityInit();

        if (showAgeVerifierScreenIfNeeded()) {
            return;
        }

        if(showConnectionSetupScreenIfNeeded()){
            return;
        }

        // get the URL for website
        String myWebsiteAddress = storedData.getString("websiteAddress", "");
        String myWebsiteAddressType = storedData.getString("websiteAddressType", "");
        // send this to the DatabaseConnectionService.java to be used later
        DatabaseConnectionService.setMyWebsiteAddress(myWebsiteAddress);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        mDrawerLayout = (MultiTouchDrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerLayout.setStatusBarBackgroundColor(getResources().getColor(
                R.color.color_primary_dark));
        mNavigationView = (NavigationView) findViewById(R.id.navigation);
        mNavigationView.setNavigationItemSelectedListener(this);

        // Only show dev testing options for (1) user-debug devices (2) debug APK builds
        if (DevOptionsFragment.shouldHideTestingOptions(this)) {
            mNavigationView.getMenu().removeItem(R.id.dev_testing_options);
        }

        mFeedbackProvider = WhistlePunkApplication.getFeedbackProvider(this);

        Bundle extras = getIntent().getExtras();
        int selectedNavItemId = R.id.navigation_item_experiments;

        int savedItemId = getSavedItemId(savedInstanceState);
        if (savedItemId != NO_SELECTED_ITEM) {
            selectedNavItemId = savedItemId;
        } else if (extras != null) {
            selectedNavItemId =
                    extras.getInt(ARG_SELECTED_NAV_ITEM_ID, R.id.navigation_item_experiments);
        }
        MenuItem item = mNavigationView.getMenu().findItem(selectedNavItemId);
        if (item == null) {
            selectedNavItemId = R.id.navigation_item_experiments;
            item = mNavigationView.getMenu().findItem(selectedNavItemId);
        }
        mNavigationView.setCheckedItem(selectedNavItemId);
        onNavigationItemSelected(item);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        BLUETOOTH_ADAPTER = bluetoothAdapter;

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE NOT SUPPORTED", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private int getSavedItemId(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return NO_SELECTED_ITEM;
        } else {
            return savedInstanceState.getInt(ARG_SELECTED_NAV_ITEM_ID, NO_SELECTED_ITEM);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ARG_SELECTED_NAV_ITEM_ID, mSelectedItemId);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (showAgeVerifierScreenIfNeeded()) {
            return;
        }
        if (showConnectionSetupScreenIfNeeded()){
            return;
        }
        if (!isMultiWindowEnabled()) {
            updateRecorderControllerForResume();
        }
        // If we get to here, it's safe to log the mode we are in: user has completed age
        // verification.
        WhistlePunkApplication.getUsageTracker(this).trackEvent(TrackerConstants.CATEGORY_APP,
                TrackerConstants.ACTION_SET_MODE,
                AgeVerifier.isOver13(AgeVerifier.getUserAge(this)) ?
                        TrackerConstants.LABEL_MODE_NONCHILD : TrackerConstants.LABEL_MODE_CHILD,
                0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent != null) {
            setIntent(intent);
        }
        super.onNewIntent(intent);

        if (mNavigationView != null && mNavigationView.getMenu() != null) {
            int desiredItemId = NO_SELECTED_ITEM;
            if (intent.getExtras() != null) {
                desiredItemId = intent.getExtras().getInt(ARG_SELECTED_NAV_ITEM_ID,
                        NO_SELECTED_ITEM);
            }
            if (desiredItemId != NO_SELECTED_ITEM && mSelectedItemId != desiredItemId) {
                onNavigationItemSelected(mNavigationView.getMenu().findItem(desiredItemId));
            }
        }
    }

    @Override
    protected void onPause() {
        if (!isMultiWindowEnabled()) {
            updateRecorderControllerForPause();
        }
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isMultiWindowEnabled()) {
            updateRecorderControllerForResume();
        }
    }

    @Override
    protected void onStop() {
        if (isMultiWindowEnabled()) {
            updateRecorderControllerForPause();
        }
        super.onStop();
    }

    private boolean isMultiWindowEnabled() {
        return MultiWindowUtils.isMultiWindowEnabled(getApplicationContext());
    }

    private void updateRecorderControllerForResume() {
        RecorderController rc = AppSingleton.getInstance(this).getRecorderController();

        // TODO: extract and test
        rc.watchRecordingStatus().takeUntil(mPause.happens()).subscribe(status -> {
            mIsRecording = status.isRecording();
            // TODO: Add experimentId to RecordingStatus
            if (mIsRecording) {
                AppSingleton.getInstance(this).getDataController().getLastUsedUnarchivedExperiment(
                        new LoggingConsumer<Experiment>(TAG, "getting last used experiment") {
                            @Override
                            public void success(Experiment experiment) {
                                PanesActivity.launch(MainActivity.this,
                                        experiment.getExperimentId());
                            }
                        });
            }
        });
    }

    private void updateRecorderControllerForPause() {
        mPause.onHappened();
    }

    /**
     * If we haven't seen all the required screens, opens the next required activity, and finishes
     * this current activity
     *
     * @return true if the activity has been finished
     */
    private boolean showAgeVerifierScreenIfNeeded() {

        // if the user has not verified age
        if (AgeVerifier.shouldShowUserAge(this)) {

            Intent ageIntent = new Intent(this, AgeVerifier.class);
            startActivity(ageIntent);
            finish();
            return true;
        }
        return false;
    }

    private boolean showConnectionSetupScreenIfNeeded() {

        storedData = getSharedPreferences("info",MODE_PRIVATE);
        isDatabaseLinkSetup = storedData.getBoolean("CONNECTION_SETUP", false);

        // if the connection page was not filled in, that must happen now
        if(!isDatabaseLinkSetup){

            Intent SetupIntent = new Intent(this, DatabaseLinkSetup.class);
            startActivity(SetupIntent);
            finish();
            return true;
        }
        return false;
    }

    // TODO: need a more principled way of keeping the action bar current

    public void restoreActionBar() {
        if (mTitleToRestore != null) {
            getSupportActionBar().setTitle(mTitleToRestore);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        if (menuItem == null) {
            return false;
        }
        if (menuItem.getItemId() == R.id.navigation_item_experiments) {
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            Fragment fragment;
            int itemId = menuItem.getItemId();

            final String tag = String.valueOf(itemId);
            fragment = getFragmentManager().findFragmentByTag(tag);
            if (fragment == null) {
                fragment = ExperimentListFragment.newInstance(shouldUsePanes());
            }
            adjustActivityForSelectedItem(itemId);

            mTitleToRestore = getTitleToRestore(menuItem);
            transaction.replace(R.id.content_container, fragment, tag).commit();
            if (menuItem.isCheckable()) {
                menuItem.setChecked(true);
            }
            mDrawerLayout.closeDrawers();
            restoreActionBar();
            mSelectedItemId = itemId;
        } else {
            mDrawerLayout.closeDrawers();
            // Launch intents
            Intent intent = null;
            int itemId = menuItem.getItemId();

            if (itemId == R.id.navigation_item_activities) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                        getString(R.string.activities_url)));
            } else if (itemId == R.id.navigation_item_settings) {
                intent = SettingsActivity.getLaunchIntent(this, menuItem.getTitle(),
                        SettingsActivity.TYPE_SETTINGS);
            } else if (itemId == R.id.navigation_item_about) {
                intent = SettingsActivity.getLaunchIntent(this, menuItem.getTitle(),
                        SettingsActivity.TYPE_ABOUT);
            } else if (itemId == R.id.dev_testing_options) {
                intent = SettingsActivity.getLaunchIntent(this, menuItem.getTitle(),
                        SettingsActivity.TYPE_DEV_OPTIONS);
            } else if (itemId == R.id.navigation_item_feedback) {
                mFeedbackProvider.sendFeedback(new LoggingConsumer<Boolean>(TAG,
                        "Send feedback") {
                    @Override
                    public void success(Boolean value) {
                        if (!value) {
                            showFeedbackError();
                        }
                    }

                    @Override
                    public void fail(Exception e) {
                        super.fail(e);
                        showFeedbackError();
                    }
                });
            }
            if (intent != null) {
                startActivity(intent);
            }
        }
        return false;
    }

    private CharSequence getTitleToRestore(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.navigation_item_experiments) {
            return getResources().getString(R.string.app_name);
        } else {
            return menuItem.getTitle();
        }
    }

    private boolean shouldUsePanes() {
        return getIntent().getBooleanExtra(ARG_USE_PANES, true);
    }

    private void adjustActivityForSelectedItem(int itemId) {
        MenuItem menu = mNavigationView.getMenu().findItem(itemId);
        setTitle(getString(R.string.title_activity_main, menu.getTitle()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Only show items in the action bar relevant to this screen
        // if the drawer is not showing. Otherwise, let the drawer
        // decide what to show in the action bar.
        if (!mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            restoreActionBar();
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == android.R.id.home) {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawer(GravityCompat.START);
            } else {
                mDrawerLayout.openDrawer(GravityCompat.START);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFeedbackError() {
        AccessibilityUtils.makeSnackbar(findViewById(R.id.drawer_layout),
                getResources().getString(R.string.feedback_error_message),
                Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        // TODO: is this ever used?
        PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions,
                grantResults);
        return;
    }

    /**
     * Launches the main activity to the selected navigation item.
     *
     * @param id One of the navigation_item constants.
     * @param usePanes should we use panes (the drawer) when observing?
     */
    @NonNull
    public static Intent launchIntent(Context context, int id, boolean usePanes) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(ARG_SELECTED_NAV_ITEM_ID, id);
        intent.putExtra(ARG_USE_PANES, usePanes);
        return intent;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO: Do this for all possible IDs in case others have activity results.
        Fragment fragment = getFragmentManager().findFragmentByTag(
                String.valueOf(R.id.navigation_item_experiments));


        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    // TODO: this is unused now.  Does any of this need to go into PanesActivity?
    public RecordFragment.UICallbacks getRecordFragmentCallbacks() {
        return new RecordFragment.UICallbacks() {
            @Override
            public void onRecordingRequested(String experimentName, boolean userInitiated) {
                ActionBar actionBar = getSupportActionBar();
                supportInvalidateOptionsMenu();
                int toolbarColorResource = R.color.recording_toolbar_color;
                int statusBarColorResource = R.color.recording_status_bar_color;
                actionBar.setSubtitle(R.string.recording_title_label);
                updateToolbarColors(toolbarColorResource, statusBarColorResource);
            }

            @Override
            public void onRecordingStopped() {
                ActionBar actionBar = getSupportActionBar();
                supportInvalidateOptionsMenu();
                int toolbarColorResource = R.color.color_primary;
                int statusBarColorResource = R.color.color_primary_dark;
                updateToolbarColors(toolbarColorResource, statusBarColorResource);
                actionBar.setSubtitle(null);
            }

            @Override
            public void onRecordingSaved(String runId, Experiment experiment) {
                boolean fromRecord = true;
                boolean createTask = true;
                RunReviewActivity.launch(MainActivity.this, runId, experiment.getExperimentId(), 0,
                        fromRecord, createTask, null);
            }

            private void updateToolbarColors(int toolbarColorResource, int statusBarColorResource) {
                Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

                // Update the toolbar and status bar colors.
                toolbar.setBackgroundResource(toolbarColorResource);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Window window = getWindow();
                    if (statusBarColorResource == R.color.color_primary_dark) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    } else {
                        // For any color that is not the default, need to clear this flag so that
                        // we can draw the right color.
                        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    }
                    window.setStatusBarColor(getResources().getColor(statusBarColorResource));
                }
            }
        };
    }


    private boolean mShouldUnbind = false;
    private ServiceConnection sc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.e("Connected Service ", "");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e("Disconnected Service", "");
        }
    };

    private void bindExiter(){
        Intent intent = new Intent(MainActivity.this, Exiter.class);

        if (bindService(intent, sc, Context.BIND_AUTO_CREATE)) {
            ContextCompat.startForegroundService(this, intent);
            //Service.startForground();

            mShouldUnbind = true;
        } else {
            Log.e("MY_APP_TAG", "Error: The requested service doesn't " +
                    "exist, or this client isn't allowed access to it.");
        }
    }

    private  void unbindExiter(){
        if (mShouldUnbind) {
            unbindService(sc);
            mShouldUnbind = false;
        }
        //this.onStartCommand();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        unbindExiter();
    }

}
