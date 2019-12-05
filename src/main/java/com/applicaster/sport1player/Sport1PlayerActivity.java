package com.applicaster.sport1player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
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
    static final int RETRY_TIME = 5000; //In miliseconds

    private Playable playable;
    private String validationPluginId;
    static volatile String liveConfig;
    private String liveUrl;
    private boolean wasPaused;
    private CountDownTimer nextProgramValidationTimer = null;
    private boolean videoValidated;

    private SpecificActivityLifecycleCallbacks activityLifecycleCallbacks = null;

    //region Activity lifecycle

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
                if (playable.isLive()) {
                    if (wasPaused) {
                        wasPaused = false;
                        requestEPGAndPresentPinIfNeeded();
                    }else {
                        setupNextProgramValidation();
                    }
                }else {
                    if (wasPaused && videoValidated) {
                        //  present PIN validation again after restore from background
                        wasPaused = false;
                        presentValidationPluginIfNeeded();
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                wasPaused = true;
                clearNextProgramValidation();
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

    //endregion

    //region private methods

    private void presentValidationPluginIfNeeded() {
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

    private void setupNextProgramValidation() {
        if (playable == null || !playable.isLive()) {
            return;
        }

        long timeout = Sport1PlayerUtils.getNextProgramTimeout(liveConfig);
        clearNextProgramValidation();
        nextProgramValidationTimer = new CountDownTimer(timeout, timeout) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                requestEPGAndPresentPinIfNeeded();
            }
        }.start();
    }

    private void clearNextProgramValidation() {
        if (nextProgramValidationTimer != null)
            nextProgramValidationTimer.cancel();
    }

    private void requestEPGAndPresentPinIfNeeded() {
        RestUtil.get(liveUrl, null, new Callback() {
            @Override
            public void onResult(String result) {
                if (result != null) {
                    liveConfig = result;
                    setupNextProgramValidation();
                    presentValidationPluginIfNeeded();
                } else {
                    retryRequestEPG();
                }
            }

            @Override
            public void onError(Throwable error) {
                retryRequestEPG();
            }
        });
    }

    //In case of error we retry after a period of time
    private void retryRequestEPG() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                requestEPGAndPresentPinIfNeeded();
            }
        }, RETRY_TIME);
    }

    //endregion
}
