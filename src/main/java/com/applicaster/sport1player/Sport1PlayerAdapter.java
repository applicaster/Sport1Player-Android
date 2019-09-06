package com.applicaster.sport1player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import com.applicaster.app.CustomApplication;
import com.applicaster.controller.PlayerLoader;
import com.applicaster.jwplayerplugin.JWPlayerAdapter;
import com.applicaster.player.PlayerLoaderI;
import com.applicaster.plugin_manager.PluginManager;
import com.applicaster.plugin_manager.login.LoginContract;
import com.applicaster.plugin_manager.login.LoginManager;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.longtailvideo.jwplayer.events.listeners.VideoPlayerEvents;

import java.util.List;
import java.util.Map;


public class Sport1PlayerAdapter extends JWPlayerAdapter implements VideoPlayerEvents.OnFullscreenListener {
    private static final String TAG = Sport1PlayerAdapter.class.getSimpleName();
    private static final String PIN_VALIDATION_PLUGIN_ID = "pin_validation_plugin_id";

    private boolean isInline;
    private String validationPluginId;
    private PlayerLoader applicasterPlayerLoader;

    /**
     * Optional initialization for the PlayerContract - will be called in the App's onCreate
     */
    @Override
    public void init(@NonNull Context appContext) {
        super.init(appContext);
    }

    /**
     * initialization of the player instance with a playable item
     *
     * @param playable
     */
    @Override
    public void init(@NonNull Playable playable, @NonNull Context context) {
        super.init(playable, context);
    }

    /**
     * initialization of the player instance with  multiple playable items
     *
     * @param playableList
     */
    @Override
    public void init(@NonNull List<Playable> playableList, @NonNull Context context) {
        super.init(playableList, context);

    }

    @Override
    public void setPluginConfigurationParams(Map params) {
        Log.d(TAG, "set config");
        validationPluginId = (String) params.get(PIN_VALIDATION_PLUGIN_ID);
        super.setPluginConfigurationParams(params);
    }

    @Override
    protected void openLoginPluginIfNeeded(final boolean isInline) {
        Log.d(TAG, "openLogin, inline = " + isInline);
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

    /*private static Activity getActivity() throws ClassNotFoundException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {
        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
        Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
        activitiesField.setAccessible(true);

        Map<Object, Object> activities = (Map<Object, Object>) activitiesField.get(activityThread);
        if (activities == null)
            return null;

        for (Object activityRecord : activities.values()) {
            Class activityRecordClass = activityRecord.getClass();
            Field pausedField = activityRecordClass.getDeclaredField("paused");
            pausedField.setAccessible(true);
            if (!pausedField.getBoolean(activityRecord)) {
                Field activityField = activityRecordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Activity activity = (Activity) activityField.get(activityRecord);
                return activity;
            }
        }

        return null;
    }*/

    @Override
    protected void displayVideo(boolean isInline) {

    }

    protected void loadItem() {
        Intent intent = new Intent(getContext(), PresentPluginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PresentPluginActivity.PLUGIN_ID_EXTRA, validationPluginId);
        intent.putExtra(PresentPluginActivity.CALLBACK_EXTRA, new PresentPluginResultI() {
            @Override
            public void onPresentPluginSuccess() {
                Log.d(TAG, "present plugin success");

                applicasterPlayerLoader = new PlayerLoader(new ApplicaterPlayerLoaderListener(isInline));
                applicasterPlayerLoader.loadItem();
            }

            @Override
            public void onPresentPluginFailure() {
                //  TODO: process negative plugin result
                Log.d(TAG, "present plugin failed");
            }

            @Override
            public PluginManager getPluginManager() {
                return PluginManager.getInstance();
            }
        });
        getContext().startActivity(intent);

        //PlayerLoader applicasterPlayerLoader = new PlayerLoader(new ApplicaterPlayerLoaderListener(isInline));
        //applicasterPlayerLoader.loadItem();
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
            Log.d(TAG, "Item loaded!");
            init(playable, getContext());
            displayVideo(isInline);
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
