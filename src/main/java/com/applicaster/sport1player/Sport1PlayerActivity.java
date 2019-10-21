package com.applicaster.sport1player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.content.LocalBroadcastManager;

import com.applicaster.jwplayerplugin.JWPlayerActivity;
import com.applicaster.plugin_manager.playersmanager.Playable;

import java.util.Map;

public class Sport1PlayerActivity extends JWPlayerActivity {
    static final String PLAYABLE_KEY = "playable";
    static final String VALIDATION_KEY = "validation";
    static final String LIVECONFIG_KEY = "live_config";

    private Playable playable;
    private String validationPluginId;
    private String liveConfig;
    private boolean wasPaused;
    private CountDownTimer timer = null;

    private boolean isReceiverRegistered = false;
    private BroadcastReceiver validationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean validated = intent.getBooleanExtra(PresentPluginActivity.VALIDATED_EXTRA, false);
            if (!validated) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playable = (Playable) getIntent().getSerializableExtra(PLAYABLE_KEY);
        validationPluginId = getIntent().getStringExtra(VALIDATION_KEY);
        liveConfig = getIntent().getStringExtra(LIVECONFIG_KEY);
        wasPaused = false;

        if (!isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).registerReceiver(validationReceiver,
                    new IntentFilter(PresentPluginActivity.VALIDATION_EVENT));
            isReceiverRegistered = true;
        }
    }

    @Override
    protected void onResume() {
        if (wasPaused) {
            wasPaused = false;
            if (playable != null) {
                if ((playable.isLive() && Sport1PlayerUtils.isLiveValidationNeeded(liveConfig))
                        || Sport1PlayerUtils.isValidationNeeded(playable)) {
                    Sport1PlayerUtils.displayValidation(this, validationPluginId);
                }
            }
        }
        setupNextValidation();
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasPaused = true;
        clearNextValidation();
    }

    @Override
    protected void onDestroy() {
        if (isReceiverRegistered)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(validationReceiver);
        super.onDestroy();
    }

    public static void startPlayerActivity(Context context, Bundle bundle, Map<String, String> params) {
        Intent intent = new Intent(context, Sport1PlayerActivity.class);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    private void setupNextValidation() {
        long nextValidation = Sport1PlayerUtils.getNextValidationTime(liveConfig);

        long now = Sport1PlayerUtils.getCurrentTime();
        long timeout = (nextValidation - now - 1) * 1000;
        if (timer != null)
            timer.cancel();
        timer = new CountDownTimer(timeout, timeout) {

            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                Sport1PlayerUtils.displayValidation(Sport1PlayerActivity.this, validationPluginId);
            }
        }.start();
    }

    private void clearNextValidation() {
        if (timer != null)
            timer.cancel();
    }
}
