package com.applicaster.sport1player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import com.applicaster.controller.PlayerLoader;
import com.applicaster.jwplayerplugin.JWPlayerAdapter;
import com.applicaster.player.PlayerLoaderI;
import com.applicaster.plugin_manager.login.LoginContract;
import com.applicaster.plugin_manager.login.LoginManager;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.applicaster.session.SessionStorageUtil;
import com.longtailvideo.jwplayer.events.listeners.VideoPlayerEvents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class Sport1PlayerAdapter extends JWPlayerAdapter implements VideoPlayerEvents.OnFullscreenListener {
    private static final String TAG = Sport1PlayerAdapter.class.getSimpleName();
    private static final String PIN_VALIDATION_PLUGIN_ID = "pin_validation_plugin_id";
    private static final String LIVESTREAM_URL = "livestream_url";

    private static final String TOKEN_KEY = "stream_token";
    private static final String NAMESPACE = "InPlayer.v1";

    private boolean isInline;
    private String validationPluginId;
    private String livestreamUrl;
    private String livestreamConfig = "";
    private boolean isReceiverRegistered = false;

    private BroadcastReceiver validationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean validated = intent.getBooleanExtra(PresentPluginActivity.VALIDATED_EXTRA, false);
            if (validated) {
                displayVideo(isInline);
            }
        }
    };

    /**
     * Optional initialization for the PlayerContract - will be called in the App's onCreate
     */
    @Override
    public void init(@NonNull Context appContext) {
        super.init(appContext);
        if (!isReceiverRegistered) {
            LocalBroadcastManager.getInstance(appContext).registerReceiver(validationReceiver,
                    new IntentFilter(PresentPluginActivity.VALIDATION_EVENT));
            isReceiverRegistered = true;
        }
    }

    /**
     * initialization of the player instance with a playable item
     *
     * @param playable Playable to load
     */
    @Override
    public void init(@NonNull Playable playable, @NonNull Context context) {
        super.init(playable, context);
        if (!isReceiverRegistered) {
            LocalBroadcastManager.getInstance(context).registerReceiver(validationReceiver,
                    new IntentFilter(PresentPluginActivity.VALIDATION_EVENT));
            isReceiverRegistered = true;
        }
    }

    /**
     * initialization of the player instance with  multiple playable items
     *
     * @param playableList List of playables to load
     */
    @Override
    public void init(@NonNull List<Playable> playableList, @NonNull Context context) {
        super.init(playableList, context);
        if (!isReceiverRegistered) {
            LocalBroadcastManager.getInstance(context).registerReceiver(validationReceiver,
                    new IntentFilter(PresentPluginActivity.VALIDATION_EVENT));
            isReceiverRegistered = true;
        }
    }

    @Override
    public void onDestroy() {
        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(validationReceiver);
        }
        super.onDestroy();
    }

    @Override
    public void setPluginConfigurationParams(Map params) {
        validationPluginId = (String) params.get(PIN_VALIDATION_PLUGIN_ID);
        livestreamUrl = (String) params.get(LIVESTREAM_URL);
        super.setPluginConfigurationParams(params);
    }

    @Override
    protected void openLoginPluginIfNeeded(final boolean isInline) {
        /*
          if item is not locked continue to play, otherwise call login with playable item.
         */
        final LoginContract loginPlugin = LoginManager.getLoginPlugin();
        this.isInline = isInline;
        if (loginPlugin != null ){
            loginPlugin.isItemLocked(getContext(), getFirstPlayable(), result -> {
                if (result) {
                    loginPlugin.login(getContext(), getFirstPlayable(), null, loginResult -> {
                        if (loginResult) {
                            loadItem();
                        }
                    });
                } else {
                    loadItem();
                }
            });
        } else {
            loadItem();
        }
    }

    @Override
    protected void displayVideo(boolean isInline) {
        if (isInline) {
            super.displayVideo(isInline);
        } else {
            Bundle bundle = new Bundle();
            bundle.putSerializable(Sport1PlayerActivity.PLAYABLE_KEY, getFirstPlayable());
            bundle.putString(Sport1PlayerActivity.VALIDATION_KEY, validationPluginId);
            bundle.putString(Sport1PlayerActivity.LIVECONFIG_KEY, livestreamConfig);
            Sport1PlayerActivity.startPlayerActivity(getContext(), bundle, getPluginConfigurationParams());
        }
    }

    private void loadItem() {
        PlayerLoader applicasterPlayerLoader = new PlayerLoader(new ApplicaterPlayerLoaderListener(isInline));
        applicasterPlayerLoader.loadItem();
    }

    private boolean validatePlayable(Playable playable) {
        if (playable.isLive()) {
            getJSON(livestreamUrl);
            //  wait for JSON data to call processLivestreamData
            return true;
        } else {
            return Sport1PlayerUtils.isValidationNeeded(playable);
        }
    }

    /************************** PlayerLoaderI ********************/
    class ApplicaterPlayerLoaderListener implements PlayerLoaderI {
        private boolean isInline;

        ApplicaterPlayerLoaderListener(boolean isInline) {
            this.isInline=isInline;
        }

        @Override
        public String getItemId() {
            return getPlayable().getPlayableId();
        }


        @Override
        public Playable getPlayable() {
            return getFirstPlayable();
        }

        @Override
        public void onItemLoaded(Playable playable) {
            init(playable, getContext());
            if (playable.isLive()) {
                String token = SessionStorageUtil.INSTANCE.get(TOKEN_KEY, NAMESPACE);
                String tokenedUrl = playable.getContentVideoURL() + "?access_token=" + token;
                playable.setContentVideoUrl(tokenedUrl);
            }
            if (validatePlayable(playable)) {
                if (!playable.isLive()) {
                    //  live stream will wait for JSON check in processLivestreamData
                    Sport1PlayerUtils.displayValidation(getContext(), validationPluginId);
                }
            } else {
                displayVideo(isInline);
            }
        }

        @Override
        public boolean isFinishing() {
            return ((Activity) getContext()).isFinishing();
        }

        @Override
        public void showMediaErroDialog() {
        }
    }

    private void getJSON(final String webService) {
        //  outer class isn't an Activity or Fragment, so no leak produced here
        @SuppressLint("StaticFieldLeak")
        class GetJSON extends AsyncTask<Void, Void, String> {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    URL url = new URL(webService);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    StringBuilder builder = new StringBuilder();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String json;
                    while ((json = bufferedReader.readLine()) != null) {
                        builder.append(json).append("\n");
                    }
                    return builder.toString().trim();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String json) {
                if (json != null) {
                    //  There is some live stream config
                    livestreamConfig = json;
                    if (Sport1PlayerUtils.isLiveValidationNeeded(json))
                        Sport1PlayerUtils.displayValidation(getContext(), validationPluginId);
                    else
                        displayVideo(isInline);
                } else {
                    //  No config - no need in validation
                    displayVideo(isInline);
                }
            }
        }

        GetJSON getJSON = new GetJSON();
        getJSON.execute();
    }
}
