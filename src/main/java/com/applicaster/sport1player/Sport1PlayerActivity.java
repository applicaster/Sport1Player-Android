package com.applicaster.sport1player;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.Nullable;

import com.applicaster.jwplayerplugin.JWPlayerActivity;
import com.applicaster.plugin_manager.PluginManager;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.applicaster.pluginpresenter.PluginPresenter;

import java.util.Map;

import static com.applicaster.pluginpresenter.PluginPresenter.PLUGIN_PRESENTER_REQUEST_CODE;

public class Sport1PlayerActivity extends JWPlayerActivity {
    static final String PLAYABLE_KEY = "playable";
    static final String VALIDATION_KEY = "validation";
    static final String LIVECONFIG_KEY = "live_config";

    private Playable playable;
    private String validationPluginId;
    private String liveConfig;
    private boolean wasPaused;
    private CountDownTimer timer = null;
    private boolean videoValidated;

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
        liveConfig = getIntent().getStringExtra(LIVECONFIG_KEY);
        wasPaused = false;
        //  it is validated in adapter before starting this activity if not free
        videoValidated = !playable.isFree();
    }

    @Override
    protected void onStart() {
        if (wasPaused && videoValidated) {
            wasPaused = false;
            presentValidationPlugin();
        }
        setupNextValidation();
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasPaused = true;
        clearNextValidation();
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
        long timeout = 0;
        if (playable != null && playable.isLive()) {
            long nextValidation = Sport1PlayerUtils.getNextValidationTime(liveConfig);
            long now = Sport1PlayerUtils.getCurrentTime();
            timeout = (nextValidation - now) * 1000;

            if (timeout > 0) {
                if (timer != null) {
                    timer.cancel();
                }
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
}
