package com.wade.manager;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.anthonycr.grant.PermissionsManager;
import com.anthonycr.grant.PermissionsResultAction;
import com.wade.manager.adapters.BookmarksAdapter;
import com.wade.manager.adapters.BrowserTabsAdapter;
import com.wade.manager.adapters.DrawerListAdapter;
import com.wade.manager.adapters.MergeAdapter;
import com.wade.manager.fragments.AbstractBrowserFragment;
import com.wade.manager.fragments.BrowserFragment;
import com.wade.manager.preview.IconPreview;
import com.wade.manager.settings.Settings;
import com.wade.manager.settings.SettingsActivity;
import com.wade.manager.ui.DirectoryNavigationView;
import com.wade.manager.ui.PageIndicator;
import com.wade.webserver.MyServer;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public abstract class MyBrowserActivity extends ThemableActivity implements
        DirectoryNavigationView.OnNavigateListener, BrowserFragment.onUpdatePathListener {

    public static final String EXTRA_SHORTCUT = "shortcut_path";
    public static final String TAG_DIALOG = "dialog";

    private static MergeAdapter mMergeAdapter;
    private static BookmarksAdapter mBookmarksAdapter;
    private static DrawerListAdapter mMenuAdapter;
    private static DirectoryNavigationView mNavigation;

    private static ListView mDrawer;
    private static DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private Toolbar toolbar;

    private FragmentManager fm;

    public abstract AbstractBrowserFragment getCurrentBrowserFragment();

    private BroadcastReceiver broadcastReceiverNetworkState;
    public boolean isHttpdStarted = false;
    private MyServer mHttpdServer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        checkPermissions();
        initRequiredComponents();
        initToolbar();
        initDrawer();
        initViewPager();
        initBroadcastReceiverNetworkStateChanged();

        try {
            mHttpdServer = new MyServer(new Integer(Settings.getDefaultPort()), Settings.getDefaultDir());
        } catch (IOException e) {
            e.printStackTrace();
        }

        SharedPreferences.OnSharedPreferenceChangeListener l;
        l = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("enablewebserver")) {
                    boolean enablewebserver = sharedPreferences.getBoolean(key, true);
                    if ((boolean) enablewebserver) {
                        startServer();
                    } else
                        stopServer();
                }
                else if (key.equals("defaultdir")) {
                    stopServer();
                    mHttpdServer.setRootDir(sharedPreferences.getString("defaultdir", "/storage"));
                    startServer();
                }
                else if (key.equals("defaultport")) {
                    stopServer();
                    mHttpdServer.setPort(new Integer(sharedPreferences.getString("defaultport", "8080")));
                    startServer();
                }
            }
        };
        SharedPreferences prefs = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(l);
    }

    public void startServer() {
        System.out.println("StartServer at "+Settings.getDefaultDir()
            + " with port " + Settings.getDefaultPort());
        if (!isHttpdStarted) try {
            mHttpdServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        isHttpdStarted = true;
    }

    public void stopServer() {
        System.out.println("stopServer");
        if (isHttpdStarted) {
            mHttpdServer.stop();
            isHttpdStarted = false;
        }
    }

    private void initBroadcastReceiverNetworkStateChanged() {
        final IntentFilter filters = new IntentFilter();
        filters.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filters.addAction("android.net.wifi.STATE_CHANGE");
        broadcastReceiverNetworkState = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // TODO: 修改現有 Wifi Address
            }
        };
        super.registerReceiver(broadcastReceiverNetworkState, filters);
    }

    @Override
    public void onPause() {
        super.onPause();
        final Fragment f = fm.findFragmentByTag(TAG_DIALOG);

        if (f != null) {
            fm.beginTransaction().remove(f).commit();
            fm.executePendingTransactions();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (isHttpdStarted) {
            mHttpdServer.stop();
            isHttpdStarted = false;
        }
        if (mNavigation != null)
            mNavigation.removeOnNavigateListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        if (prefs.getBoolean("enablewebserver", true)) {
            System.out.println("Call startServer()");
            startServer();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        IconPreview.clearCache();
    }

    private void initRequiredComponents() {
        fm = getFragmentManager();
        mNavigation = new DirectoryNavigationView(this);

        // add listener for navigation view
        if (mNavigation.listeners.isEmpty())
            mNavigation.addonNavigateListener(this);

        // start IconPreview class to get thumbnails if BrowserListAdapter
        // request them
        new IconPreview(this);
    }

    protected void initToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    protected void initDrawer() {
        setupDrawer();
        initDrawerList();
    }

    protected void initViewPager() {
        // Instantiate a ViewPager and a PagerAdapter.
        ViewPager mPager = (ViewPager) findViewById(R.id.pager);
        BrowserTabsAdapter mPagerAdapter = new BrowserTabsAdapter(fm);
        mPager.setAdapter(mPagerAdapter);

        PageIndicator mIndicator = (PageIndicator) findViewById(R.id.indicator);
        mIndicator.setViewPager(mPager);
        mIndicator.setFades(false);
    }

    private void setupDrawer() {
        mDrawer = (ListView) findViewById(R.id.left_drawer);

        // Set shadow of navigation drawer
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow,
                GravityCompat.START);

        // Add Navigation Drawer to ActionBar
        mDrawerToggle = new android.support.v7.app.ActionBarDrawerToggle(this, mDrawerLayout,
                toolbar, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView)
            {
                supportInvalidateOptionsMenu();
                View serverView = mMenuAdapter.getView(1, null, null);
            }

            @Override
            public void onDrawerClosed(View view)
            {
                supportInvalidateOptionsMenu();
            }
        };

        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

    private void initDrawerList() {
        mBookmarksAdapter = new BookmarksAdapter(this);
        mMenuAdapter = new DrawerListAdapter(this);

        // create MergeAdapter to combine multiple adapter
        mMergeAdapter = new MergeAdapter();
        mMergeAdapter.addAdapter(mBookmarksAdapter);
        mMergeAdapter.addAdapter(mMenuAdapter);

        mDrawer.setAdapter(mMergeAdapter);
        mDrawer.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 選單有2段，這邊是第1段，快速目錄切換的 Bootmark
                if (mMergeAdapter.getAdapter(position).equals(mBookmarksAdapter)) {

                    // handle bookmark items
                    if (mDrawerLayout.isDrawerOpen(mDrawer))
                        mDrawerLayout.closeDrawer(mDrawer);

                    File file = new File(mBookmarksAdapter.getItem(position).getPath());
                    getCurrentBrowserFragment().onBookmarkClick(file);
                } else if (mMergeAdapter.getAdapter(position).equals(mMenuAdapter)) {
                    // 選單有2段，這邊是第2段，設定+結束
                    switch ((int) mMergeAdapter.getItemId(position)) {
                        case 0:
                            Intent intent2 = new Intent(MyBrowserActivity.this,
                                    SettingsActivity.class);
                            startActivity(intent2);
                            break;
                        case 1:
                            finish();
                    }
                }
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
        System.out.println("onConfigurationChanged()");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (mDrawerLayout.isDrawerOpen(mDrawer)) {
                    mDrawerLayout.closeDrawer(mDrawer);
                } else {
                    mDrawerLayout.openDrawer(mDrawer);
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static boolean isDrawerOpen()
    {
        return mDrawerLayout.isDrawerOpen(mDrawer);
    }

    public static BookmarksAdapter getBookmarksAdapter() {
        return mBookmarksAdapter;
    }

    @Override
    public boolean onKeyDown(int keycode, @NonNull KeyEvent event) {
        if (keycode != KeyEvent.KEYCODE_BACK)
            return false;

        if (isDrawerOpen()) {
            mDrawerLayout.closeDrawer(mDrawer);
            return true;
        }
        return getCurrentBrowserFragment().onBackPressed();
    }

    @Override
    public void onNavigate(String path)
    {
        if (path.startsWith("/storage"))
        getCurrentBrowserFragment().onNavigate(path);
    }

    @Override
    public void onUpdatePath(String path) {
        if (path.startsWith("/storage"))
        mNavigation.setDirectoryButtons(path);
    }

    private void checkPermissions() {
        PermissionsManager.getInstance().requestAllManifestPermissionsIfNecessary(this, new PermissionsResultAction() {
            @Override
            public void onGranted() {
            }

            @Override
            public void onDenied(String permission) {
                String message = String.format(Locale.getDefault(), getString(R.string.message_denied), permission);
                Toast.makeText(MyBrowserActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        PermissionsManager.getInstance().notifyPermissionsChange(permissions, grantResults);
    }
}
