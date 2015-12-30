package com.wade.manager.fragments;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;
import com.wade.manager.MyBrowserActivity;
import com.wade.manager.R;
import com.wade.manager.SearchActivity;
import com.wade.manager.adapters.BrowserListAdapter;
import com.wade.manager.controller.ActionModeController;
import com.wade.manager.dialogs.CreateFileDialog;
import com.wade.manager.dialogs.CreateFolderDialog;
import com.wade.manager.dialogs.DirectoryInfoDialog;
import com.wade.manager.dialogs.UnpackDialog;
import com.wade.manager.fileobserver.FileObserverCache;
import com.wade.manager.fileobserver.MultiFileObserver;
import com.wade.manager.settings.Settings;
import com.wade.manager.tasks.PasteTaskExecutor;
import com.wade.manager.utils.ClipBoard;
import com.wade.manager.utils.SimpleUtils;
import com.wade.webserver.MyServer;

import java.io.File;
import java.lang.ref.WeakReference;

public abstract class AbstractBrowserFragment extends UserVisibleHintFragment implements
        MultiFileObserver.OnEventListener, PopupMenu.OnMenuItemClickListener {
    private Activity mActivity;
    private FragmentManager fm;

    private MultiFileObserver mObserver;
    private FileObserverCache mObserverCache;
    private Runnable mLastRunnable;
    private static Handler sHandler;

    private onUpdatePathListener mUpdatePathListener;
    private ActionModeController mActionController;
    private BrowserListAdapter mListAdapter;
    public String mCurrentPath;
    private AbsListView mListView;

    private boolean mUseBackKey = true;

    private static final int DEFAULT_PORT = 8080;
    private String rootDir = null;

    // INSTANCE OF ANDROID WEB SERVER
    private MyServer androidWebServer;
    private static boolean isStarted = false;

    public interface onUpdatePathListener {
        void onUpdatePath(String path);
    }

    @Override
    public void onCreate(Bundle state) {
        setRetainInstance(true);
        setHasOptionsMenu(true);
        super.onCreate(state);
        rootDir = Settings.getDefaultDir();
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        mObserverCache = FileObserverCache.getInstance();
        mActionController = new ActionModeController(activity);

        if (sHandler == null) {
            sHandler = new Handler(activity.getMainLooper());
        }

        try {
            mUpdatePathListener = (onUpdatePathListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement mUpdatePathListener");
        }
    }

    @Override
    public void onActivityCreated(Bundle state) {
        super.onActivityCreated(state);
        mActivity = getActivity();
        Intent intent = mActivity.getIntent();
        fm = getFragmentManager();
        mActionController.setListView(mListView);

        initDirectory(state, intent);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_browser, container, false);

        initList(inflater, rootView);
        initFab(rootView);
        return rootView;
    }

    @Override
    protected void onVisible() {
        final MyBrowserActivity activity = (MyBrowserActivity) getActivity();
        // check for root
        Settings.rootAccess();

        navigateTo(mCurrentPath);

        // this is only needed if you select "move/copy files" in SearchActivity and come back
        if (!ClipBoard.isEmpty())
            activity.supportInvalidateOptionsMenu();
    }

    @Override
    protected void onInvisible() {
        mObserver.stopWatching();

        if (mActionController != null) {
            mActionController.finishActionMode();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("location", mCurrentPath);
    }

    public void initList(LayoutInflater inflater, View rootView) {
        final MyBrowserActivity context = (MyBrowserActivity) getActivity();
        mListAdapter = new BrowserListAdapter(context, inflater);

        mListView = (ListView) rootView.findViewById(android.R.id.list);
        mListView.setEmptyView(rootView.findViewById(android.R.id.empty));
        mListView.setAdapter(mListAdapter);
        mListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                final File file = new File((mListView.getAdapter()
                        .getItem(position)).toString());

                if (file.isDirectory()) {
                    navigateTo(file.getAbsolutePath());

                    // go to the top of the ListView
                    mListView.setSelection(0);
                } else {
                    listItemAction(file);
                }
            }
        });
    }

    protected void initFab(View rootView) {
        FloatingActionButton fab = (FloatingActionButton) rootView.findViewById(R.id.fabbutton);
        fab.attachToListView(mListView);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showMenu(view);
            }
        });
    }

    private void showMenu(View v) {
        PopupMenu popup = new PopupMenu(mActivity, v);

        // This activity implements OnMenuItemClickListener
        popup.setOnMenuItemClickListener(this);
        popup.inflate(R.menu.fab_menu);
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.createfile:
                final DialogFragment dialog1 = new CreateFileDialog();
                dialog1.show(fm, MyBrowserActivity.TAG_DIALOG);
                return true;
            case R.id.createfolder:
                final DialogFragment dialog2 = new CreateFolderDialog();
                dialog2.show(fm, MyBrowserActivity.TAG_DIALOG);
                return true;
            default:
                return false;
        }
    }

    // this will be overwritten in picker fragment
    public void listItemAction(File file) {
        if (SimpleUtils.isSupportedArchive(file)) {
            final DialogFragment dialog = UnpackDialog.instantiate(file);
            dialog.show(fm, MyBrowserActivity.TAG_DIALOG);
        } else {
            SimpleUtils.openFile(mActivity, file);
        }
    }

    public void navigateTo(String path) {
        if (!path.startsWith("/storage")) return;
        mCurrentPath = path;

        if (!mUseBackKey)
            mUseBackKey = true;

        if (mObserver != null) {
            mObserver.stopWatching();
            mObserver.removeOnEventListener(this);
        }

        mListAdapter.addFiles(path);

        mObserver = mObserverCache.getOrCreate(path);

        // add listener for FileObserver and start watching
        if (mObserver.listeners.isEmpty())
            mObserver.addOnEventListener(this);
        mObserver.startWatching();

        mUpdatePathListener.onUpdatePath(path);
    }

    @Override
    public void onEvent(int event, String path) {
        // this will automatically update the directory when an action like this
        // will be performed
        switch (event & FileObserver.ALL_EVENTS) {
            case FileObserver.CREATE:
            case FileObserver.CLOSE_WRITE:
            case FileObserver.MOVE_SELF:
            case FileObserver.MOVED_TO:
            case FileObserver.MOVED_FROM:
            case FileObserver.ATTRIB:
            case FileObserver.DELETE:
            case FileObserver.DELETE_SELF:
                sHandler.removeCallbacks(mLastRunnable);
                sHandler.post(mLastRunnable =
                        new NavigateRunnable((MyBrowserActivity) getActivity(), path));
                break;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);

        if (MyBrowserActivity.isDrawerOpen()) {
            menu.findItem(R.id.paste).setVisible(false);
            menu.findItem(R.id.folderinfo).setVisible(false);
            menu.findItem(R.id.search).setVisible(false);
        } else {
            menu.findItem(R.id.paste).setVisible(!ClipBoard.isEmpty());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final MyBrowserActivity activity = (MyBrowserActivity) getActivity();
        final FragmentManager fm = getFragmentManager();

        switch (item.getItemId()) {
            case R.id.folderinfo:
                final DialogFragment dirInfo = new DirectoryInfoDialog();
                dirInfo.show(fm, MyBrowserActivity.TAG_DIALOG);
                return true;
            case R.id.search:
                Intent sintent = new Intent(activity, SearchActivity.class);
                startActivity(sintent);
                return true;
            case R.id.paste:
                final PasteTaskExecutor ptc = new PasteTaskExecutor(activity,
                        mCurrentPath);
                ptc.start();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onNavigate(String path) {
        // navigate to path when Navigation button is clicked
        if (mActionController.isActionMode()) {
            mActionController.finishActionMode();
        }
        navigateTo(path);
        // go to the top of the ListView
        mListView.setSelection(0);
    }

    private void initDirectory(Bundle savedInstanceState, Intent intent) {
        String defaultdir;

        if (savedInstanceState != null) {
            // get directory when you rotate your phone
            defaultdir = savedInstanceState.getString("location");
        } else {
            try {
                File dir = new File(intent.getStringExtra(MyBrowserActivity.EXTRA_SHORTCUT));

                if (dir.exists() && dir.isDirectory()) {
                    defaultdir = dir.getAbsolutePath();
                } else {
                    if (dir.exists() && dir.isFile())
                        listItemAction(dir);
                    // you need to call it when shortcut-dir not exists
                    defaultdir = Settings.getDefaultDir();
                }
            } catch (Exception e) {
                defaultdir = Settings.getDefaultDir();
            }
        }

        File dir = new File(defaultdir);

        if (dir.exists() && dir.isDirectory())
            navigateTo(dir.getAbsolutePath());
    }

    private static final class NavigateRunnable implements Runnable {
        private final WeakReference<MyBrowserActivity> abActivityWeakRef;
        private final String target;

        NavigateRunnable(final MyBrowserActivity abActivity, final String path) {
            this.abActivityWeakRef = new WeakReference<>(abActivity);
            this.target = path;
        }

        @Override
        public void run() {
            // TODO: Ensure WeakReference approach is free of both bugs and leaks
            //BrowserTabsAdapter.getCurrentBrowserFragment().navigateTo(target);
            MyBrowserActivity abActivity = abActivityWeakRef.get();
            if (abActivity != null) {
                abActivity.getCurrentBrowserFragment().navigateTo(target);
            } else {
                Log.w(this.getClass().getName(),
                        "NavigateRunnable: activity weakref returned null, can't navigate");
            }
        }
    }

    public void onBookmarkClick(File file) {
        if (!file.exists()) {
            Toast.makeText(mActivity, getString(R.string.cantopenfile),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (file.isDirectory()) {
            navigateTo(file.getAbsolutePath());

            // go to the top of the ListView
            mListView.setSelection(0);
        } else {
            listItemAction(file);
        }
    }

    public boolean onBackPressed() {
        if (mUseBackKey && mActionController.isActionMode()) {
            mActionController.finishActionMode();
            return true;
        } else if (mUseBackKey && !mCurrentPath.equals("/storage")) {
            File file = new File(mCurrentPath);
            navigateTo(file.getParent());

            // get position of the previous folder in ListView
            mListView.setSelection(mListAdapter.getPosition(file.getPath()));
            return true;
        } else if (mUseBackKey && mCurrentPath.equals("/storage")) {
            Toast.makeText(mActivity, getString(R.string.pressbackagaintoquit),
                    Toast.LENGTH_SHORT).show();

            mUseBackKey = false;
            return false;
        } else if (!mUseBackKey && mCurrentPath.equals("/storage")) {
            mActivity.finish();
            return false;
        }

        return true;
    }

}
