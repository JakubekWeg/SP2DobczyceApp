package blackeagle.sp2dobczyceapp;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int OPEN_SETTINGS_ID = 1001;

    LinearLayout mainLayout;
    SwipeRefreshLayout refreshLayout;

    UpdateManager.Result refreshResult = null;
    boolean isMeasured = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Settings.loadSettings(this);
        if (!Settings.isReady) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        if (Settings.applyNowDarkTheme())
            setTheme(R.style.DarkTheme);

        setContentView(R.layout.activity_main);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        myToolbar.setTitleTextColor(Settings.getColor(this, R.color.white));
        myToolbar.setTitle(R.string.app_name);
        setSupportActionBar(myToolbar);

        mainLayout = ((LinearLayout) findViewById(R.id.main_layout));
        mainLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mainLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                isMeasured = true;
                if (refreshResult != null)
                    createViewByResult();
            }
        });

        refreshLayout = ((SwipeRefreshLayout) findViewById(R.id.refresh_layout));
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (Settings.isOnline(MainActivity.this)) {
                    requestRefresh();
                } else {
                    stopRefreshing();
                    showSnackbarMessage(R.string.no_internet);
                }
            }
        });

        if (Settings.isOnline(this)) {
            requestRefresh();
        } else {
            refreshResult = UpdateManager.getDataFromFile(MainActivity.this);
            if (isMeasured)
                createViewByResult();
        }

        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("openSettings", false)){
            startActivityForResult(new Intent(this, SettingsActivity.class), OPEN_SETTINGS_ID);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(R.string.lesson_plan);
        item.setIcon(R.drawable.ic_event);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (Settings.isClassSelected()) {
                    Intent intent = new Intent(MainActivity.this, LessonPlanActivity.class);
                    intent.putExtra("name", Settings.getClassOrTeacherName());
                    intent.putExtra("isTeacher", Settings.isTeacher);
                    startActivity(intent);
                } else {
                    startActivity(new Intent(MainActivity.this, LessonPlanActivity.class));
                }
                return true;
            }
        });

        item = menu.add(R.string.settings);
        item.setIcon(R.drawable.ic_settings);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(intent, OPEN_SETTINGS_ID);
                return true;
            }
        });

        item = menu.add("O aplikacji");
        item.setIcon(R.drawable.ic_info);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {

                return true;
            }
        })

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case OPEN_SETTINGS_ID:
                if (data != null ){
                   if(data.getBooleanExtra("changedClass", false)) {
                       refreshResult.isFromFile = true;
                       createViewByResult();
                   }
                   if (data.getBooleanExtra("changedTheme", false)){
                       finish();
                       startActivity(getIntent());
                   }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    void requestRefresh() {
        refreshLayout.setRefreshing(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                refreshResult = UpdateManager.update(MainActivity.this);
                stopRefreshing();
                if (isMeasured)
                    createViewByResult();

            }
        }).start();
    }

    void stopRefreshing() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshLayout.setRefreshing(false);
            }
        });
    }

    void showSnackbarMessage(@StringRes final int id){
        showSnackbarMessage(getString(id));
    }

    void showSnackbarMessage(final String msg){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            Snackbar.make(mainLayout, msg, BaseTransientBottomBar.LENGTH_SHORT).show();
        else
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    void createViewByResult() {
        if (refreshResult == null)
            return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (refreshResult.success) {

                    mainLayout.removeAllViews();
                    int width = mainLayout.getWidth();
                    mainLayout.addView(Section.createSeparator(MainActivity.this));
                    refreshResult.createLuckyNumberView(MainActivity.this, mainLayout, width);
                    refreshResult.createViews(MainActivity.this, mainLayout, width);
                    if (!refreshResult.isFromFile) {
                        if (refreshResult.updated) {
                            if (refreshResult.newCount > 0) {
                                if (refreshResult.newCount == 1)
                                    showSnackbarMessage(R.string.one_new);
                                else if (refreshResult.newCount < 5)
                                    showSnackbarMessage(getString(R.string.something_is_new1, refreshResult.newCount));
                                else
                                    showSnackbarMessage(getString(R.string.something_is_new2, refreshResult.newCount));
                            } else
                                showSnackbarMessage(R.string.updated);
                        } else
                            showSnackbarMessage(R.string.nothing_is_new);
                    }
                    //noinspection ConstantConditions
                    getSupportActionBar().setTitle(refreshResult.allNewsCount > 0 ? ("Zastępstwa (" + String.valueOf(refreshResult.allNewsCount) + ")")
                    :"Zastępstwa");

                    Animation animation = new AlphaAnimation(0.f,1.f);
                    animation.setDuration(300);
                    mainLayout.startAnimation(animation);

                } else {
                    showSnackbarMessage(R.string.cannot_refresh);
                }
            }
        });
    }
}