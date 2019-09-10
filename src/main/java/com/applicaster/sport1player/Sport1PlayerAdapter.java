package com.applicaster.sport1player;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import com.applicaster.atom.model.APAtomEntry;
import com.applicaster.controller.PlayerLoader;
import com.applicaster.jwplayerplugin.JWPlayerAdapter;
import com.applicaster.player.PlayerLoaderI;
import com.applicaster.plugin_manager.login.LoginContract;
import com.applicaster.plugin_manager.login.LoginManager;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.google.gson.internal.LinkedTreeMap;
import com.longtailvideo.jwplayer.events.listeners.VideoPlayerEvents;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Sport1PlayerAdapter extends JWPlayerAdapter implements VideoPlayerEvents.OnFullscreenListener {
    private static final String TAG = Sport1PlayerAdapter.class.getSimpleName();
    private static final String PIN_VALIDATION_PLUGIN_ID = "pin_validation_plugin_id";
    private static final String TRACKING_INFO_KEY = "tracking_info";
    private static final String FSK_KEY = "fsk";
    private static final String FSK_PATTERN = "FSK (\\d+)";
    private static final String AGE_RESTRICTION_START_KEY = "ageRestrictionStart";
    private static final String AGE_RESTRICTION_END_KEY = "ageRestrictionEnd";

    private boolean isInline;
    private String validationPluginId;
    private double validationAge = 16f;
    private boolean isReceiverRegistered = false;

    private BroadcastReceiver validationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean validated = intent.getBooleanExtra(PresentPluginActivity.VALIDATED_EXTRA, false);
            if (validated) {
                Sport1PlayerAdapter.super.displayVideo(isInline);
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

    @Override
    protected void displayVideo(boolean isInline) {
        Intent intent = new Intent(getContext(), PresentPluginActivity.class);
        intent.putExtra(PresentPluginActivity.PLUGIN_ID_EXTRA, validationPluginId);
        getContext().startActivity(intent);
    }

    protected void loadItem() {
        PlayerLoader applicasterPlayerLoader = new PlayerLoader(new ApplicaterPlayerLoaderListener(isInline));
        applicasterPlayerLoader.loadItem();
    }

    private boolean validatePlayable(Playable playable) {
        if (playable.isLive()) {
            //  TODO: implement livestream validation
            return false;
        }
        else {
            APAtomEntry entry = ((APAtomEntry.APAtomEntryPlayable)playable).getEntry();
            LinkedTreeMap<Object, Object> info = entry.getExtension(TRACKING_INFO_KEY, LinkedTreeMap.class);
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
            if (validatePlayable(playable))
                displayVideo(isInline);
            else
                Sport1PlayerAdapter.super.displayVideo(isInline);
        }

        @Override
        public boolean isFinishing() {
            return ((Activity) getContext()).isFinishing();
        }

        @Override
        public void showMediaErroDialog() {
        }
    }
}
