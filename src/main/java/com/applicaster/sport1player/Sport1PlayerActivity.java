package com.applicaster.sport1player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.Nullable;

import com.applicaster.app.SpecificActivityLifecycleCallbacks;
import com.applicaster.jwplayerplugin.JWPlayerActivity;
import com.applicaster.jwplayerplugin.JWPlayerContainer;
import com.applicaster.plugin_manager.PluginManager;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.applicaster.pluginpresenter.PluginPresenter;

import java.util.Map;

import static com.applicaster.pluginpresenter.PluginPresenter.PLUGIN_PRESENTER_REQUEST_CODE;

public class Sport1PlayerActivity extends JWPlayerActivity {
    static final String PLAYABLE_KEY = "playable";
    static final String VALIDATION_KEY = "validation";
    static final String LIVEURL_KEY = "live_url";

    private Playable playable;
    private String validationPluginId;
    static volatile String liveConfig;
    private String liveUrl;
    private boolean wasPaused;
    private CountDownTimer timer = null;
    private CountDownTimer refreshTimer = null;
    private boolean videoValidated;

    private SpecificActivityLifecycleCallbacks activityLifecycleCallbacks = null;

    public static void startPlayerActivity(Context context, Bundle bundle, Map<String, String> params) {
        Intent intent = new Intent(context, Sport1PlayerActivity.class);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playable = (Playable) getIntent().getSerializableExtra(PLAYABLE_KEY);
        validationPluginId = getIntent().getStringExtra(VALIDATION_KEY);
        liveUrl = getIntent().getStringExtra(LIVEURL_KEY);
        wasPaused = false;
        //  it is validated in adapter before starting this activity if not free
        videoValidated = !playable.isFree();

        activityLifecycleCallbacks = new SpecificActivityLifecycleCallbacks(this, new NavigationLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                if (wasPaused && videoValidated) {
                    //  present PIN validation again after restore from background
                    wasPaused = false;
                    presentValidationPlugin();
                }
                setupNextRefresh();
                setupNextValidation();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                wasPaused = true;
                clearNextValidation();
                clearNextRefresh();
            }

            @Override
            public void onActivityStopped(Activity activity) {
                //  pause player while PIN screen is displayed
                JWPlayerContainer container = findViewById(R.id.playerView);
                if (container != null)
                    container.getJWPlayerView().pause();
            }
        });
        getApplication().registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
    }

    @Override
    protected void onDestroy() {
        getApplication().unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
        activityLifecycleCallbacks = null;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == PLUGIN_PRESENTER_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                finish();
            }
            videoValidated = resultCode == RESULT_OK;
            wasPaused = false;
        }
    }

    private void setupNextValidation() {
        if (playable != null && playable.isLive()) {
            long nextValidation = Sport1PlayerUtils.getNextValidationTime(liveConfig);
            long now = Sport1PlayerUtils.getCurrentTime();
            long timeout = (nextValidation - now) * 1000;

            if (timeout > 0) {
                clearNextValidation();
                timer = new CountDownTimer(timeout, timeout) {

                    @Override
                    public void onTick(long millisUntilFinished) {
                    }

                    @Override
                    public void onFinish() {
                        presentValidationPlugin();
                    }
                }.start();
            }
        }
    }

    private void presentValidationPlugin() {
        if (playable != null &&
                ((playable.isLive() && Sport1PlayerUtils.isLiveValidationNeeded(liveConfig))
                || Sport1PlayerUtils.isValidationNeeded(playable))) {
            PluginManager manager = PluginManager.getInstance();
            if (manager != null) {
                if (validationPluginId != null && !validationPluginId.isEmpty()) {
                    PluginManager.InitiatedPlugin plugin = manager.getInitiatedPlugin(validationPluginId);
                    if (plugin != null && plugin.instance instanceof PluginPresenter) {
                        ((PluginPresenter) plugin.instance).setPluginModel(plugin.plugin);
                        ((PluginPresenter) plugin.instance).presentPlugin(this, null);
                    }
                }
            }
        }
    }

    private void clearNextValidation() {
        if (timer != null)
            timer.cancel();
    }

    private void setupNextRefresh() {
        if (playable == null || !playable.isLive())
            return;

        long nextUpdate = Sport1PlayerUtils.getProgramFinishTime(liveConfig);
        long now = Sport1PlayerUtils.getCurrentTime();
        long timeout = (nextUpdate - now) * 1000;

        if (timeout > 0) {
            clearNextRefresh();
            refreshTimer = new CountDownTimer(timeout, timeout) {
                @Override
                public void onTick(long millisUntilFinished) {
                }

                @Override
                public void onFinish() {
                    refreshLiveConfig();
                }
            }.start();
        }
    }

    private void refreshLiveConfig() {
        if (playable == null || !playable.isLive())
            return;

        RestUtil.get(liveUrl, null, new Callback() {
            @Override
            public void onResult(String result) {
                if (result != null) {
                    liveConfig = result;
                    setupNextRefresh();
                } else {
                    //  request config again
                    refreshLiveConfig();
                }
            }

            @Override
            public void onError(Throwable error) {
                //  request config again
                refreshLiveConfig();
            }
        });
    }

    private void clearNextRefresh() {
        if (refreshTimer != null)
            refreshTimer.cancel();
    }
}
