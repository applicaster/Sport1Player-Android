package com.applicaster.sport1player;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import com.applicaster.atom.model.APAtomEntry;
import com.applicaster.controller.PlayerLoader;
import com.applicaster.jwplayerplugin.JWPlayerAdapter;
import com.applicaster.player.PlayerLoaderI;
import com.applicaster.plugin_manager.login.LoginContract;
import com.applicaster.plugin_manager.login.LoginManager;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.longtailvideo.jwplayer.events.listeners.VideoPlayerEvents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Sport1PlayerAdapter extends JWPlayerAdapter implements VideoPlayerEvents.OnFullscreenListener {
    private static final String TAG = Sport1PlayerAdapter.class.getSimpleName();
    private static final String PIN_VALIDATION_PLUGIN_ID = "pin_validation_plugin_id";
    private static final String LIVESTREAM_URL = "livestream_url";
    private static final String TRACKING_INFO_KEY = "tracking_info";
    private static final String FSK_KEY = "fsk";
    private static final String FSK_PATTERN = "FSK (\\d+)";
    private static final String AGE_RESTRICTION_START_KEY = "ageRestrictionStart";
    private static final String AGE_RESTRICTION_END_KEY = "ageRestrictionEnd";
    private static final String EPG_KEY = "epg";
    private static final String END_KEY = "end";

    private boolean isInline;
    private String validationPluginId;
    private String livestreamUrl;
    private double validationAge = 16f;
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
     * @param playable
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
     * @param playableList
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
        /**
         * if item is not locked continue to play, otherwise call login with playable item.
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

    private void displayValidation() {
        Intent intent = new Intent(getContext(), PresentPluginActivity.class);
        intent.putExtra(PresentPluginActivity.PLUGIN_ID_EXTRA, validationPluginId);
        getContext().startActivity(intent);
    }

    protected void loadItem() {
        PlayerLoader applicasterPlayerLoader = new PlayerLoader(new ApplicaterPlayerLoaderListener(isInline));
        applicasterPlayerLoader.loadItem();
    }

    private void processLivestreamData(String json) {
        Type mapType = new TypeToken<LinkedTreeMap<Object, Object>>(){}.getType();
        Map<Object, Object> data = new Gson().fromJson(json, mapType);

        long now = Sport1PlayerUtils.getCurrentTime();

        //  Schedule next live stream check
        if (data.containsKey(EPG_KEY)) {
            List<LinkedTreeMap<Object, Object>> epg = (List<LinkedTreeMap<Object, Object>>) data.get(EPG_KEY);
            if (epg != null) {
                Map<Object, Object> lastProgram = epg.get(epg.size() - 1);
                if (lastProgram.containsKey(END_KEY)) {
                    String endDate = (String) lastProgram.get(END_KEY);
                    long endTime = Sport1PlayerUtils.dateToTimestamp(endDate);
                    long timeout = (endTime - now - 10) * 1000;
                    if (timeout > 0) {
                        //  set countdown to next live stream data check
                        new CountDownTimer(timeout, timeout) {

                            @Override
                            public void onTick(long millisUntilFinished) {
                            }

                            @Override
                            public void onFinish() {
                                getJSON(livestreamUrl);
                            }
                        }.start();
                    }
                }
            }
        }

        //  Check for age restriction
        if (data.containsKey(AGE_RESTRICTION_START_KEY) && data.containsKey(AGE_RESTRICTION_END_KEY)) {
            long ageRestrictionStart = (long)(double)data.get(AGE_RESTRICTION_START_KEY);
            long ageRestrictionEnd = (long)(double)data.get(AGE_RESTRICTION_END_KEY);

            if (ageRestrictionStart <= now && now <= ageRestrictionEnd) {
                displayValidation();
            } else {
                long timeout = (ageRestrictionStart - now - 1) * 1000;
                if (timeout > 0) {
                    //  set countdown to validation
                    new CountDownTimer(timeout, timeout) {

                        @Override
                        public void onTick(long millisUntilFinished) {
                        }

                        @Override
                        public void onFinish() {
                            //  time to validate!
                            displayValidation();
                        }
                    }.start();
                }
                //  display video since it's not the time for validation
                displayVideo(isInline);
            }
        } else {
            //  no validation requirement
            displayVideo(isInline);
        }
    }

    private boolean validatePlayable(Playable playable) {
        if (playable.isLive()) {
            getJSON(livestreamUrl);
            //  wait for JSON data to call processLivestreamData
            return true;
        }
        else {
            APAtomEntry entry = ((APAtomEntry.APAtomEntryPlayable)playable).getEntry();
            LinkedTreeMap info = entry.getExtension(TRACKING_INFO_KEY, LinkedTreeMap.class);
            if (info == null)
                return false;

            String fsk = info.containsKey(FSK_KEY) ? (String) info.get(FSK_KEY) : "";
            double fskAge = 0;
            Matcher matcher = Pattern.compile(FSK_PATTERN).matcher(fsk);
            if (matcher.matches())
                fskAge = Double.parseDouble(matcher.group(1));

            return fskAge >= validationAge;
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
            final LoginContract loginPlugin = LoginManager.getLoginPlugin();
            if (playable.isLive() && loginPlugin != null) {
                String tokenedUrl = playable.getContentVideoURL() + "?access_token=" + loginPlugin.getToken();
                playable.setContentVideoUrl(tokenedUrl);
            }
            if (validatePlayable(playable)) {
                if (!playable.isLive()) {
                    //  live stream will wait for JSON check in processLivestreamData
                    displayValidation();
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
                    processLivestreamData(json);
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
