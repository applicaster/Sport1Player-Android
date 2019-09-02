package com.applicaster.sport1player;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.ViewGroup;

import com.applicaster.controller.PlayerLoader;
import com.applicaster.jwplayerplugin.JWPlayerActivity;
import com.applicaster.jwplayerplugin.JWPlayerAdapter;
import com.applicaster.jwplayerplugin.JWPlayerContainer;
import com.applicaster.player.PlayerLoaderI;
import com.applicaster.plugin_manager.PluginManager;
import com.applicaster.plugin_manager.login.LoginContract;
import com.applicaster.plugin_manager.login.LoginManager;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.applicaster.pluginpresenter.PluginPresenter;
import com.longtailvideo.jwplayer.JWPlayerView;
import com.longtailvideo.jwplayer.events.listeners.VideoPlayerEvents;

import java.util.Map;

public class Sport1PlayerAdapter extends JWPlayerAdapter implements PresentPluginResultI, VideoPlayerEvents.OnFullscreenListener {
    private static final String TAG = Sport1PlayerAdapter.class.getSimpleName();
    private static final String PIN_VALIDATION_PLUGIN_ID = "pin_validation_plugin_id";

    private JWPlayerContainer jwPlayerContainer;
    private JWPlayerView jwPlayerView;

    private boolean isInline;
    private String validationPluginId;

    @Override
    public void attachInline(@NonNull ViewGroup videoContainerView) {
        Log.d(TAG, "attachInline");
        jwPlayerContainer =new JWPlayerContainer(videoContainerView.getContext());
        jwPlayerView = jwPlayerContainer.getJWPlayerView();
        jwPlayerView.setFullscreenHandler(this);
        jwPlayerView.addOnFullscreenListener(this);

        ViewGroup.LayoutParams playerContainerLayoutParams
                = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
                , ViewGroup.LayoutParams.MATCH_PARENT);
        videoContainerView.addView(jwPlayerContainer, playerContainerLayoutParams);

        openLoginPluginIfNeeded(true);
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

    protected void displayVideo(boolean isInline){

        if (isInline){
            /*jwPlayerView.load( JWPlayerUtil.getPlaylistItem(getFirstPlayable(), getPluginConfigurationParams()));
            jwPlayerView.play();*/
        }else {
            Sport1PlayerActivity.startPlayerActivity(getContext(), getFirstPlayable(), getPluginConfigurationParams());
        }
    }

    protected void loadItem() {
        Playable playable = getFirstPlayable();
        Log.d(TAG, "load item: " + playable.getContentVideoURL());
        if (validationPluginId != null && !validationPluginId.isEmpty()) {
            Log.d(TAG, "validation plugin = " + validationPluginId);
            PluginManager.InitiatedPlugin plugin = PluginManager.getInstance().getInitiatedPlugin(validationPluginId);
            if (plugin != null && plugin.instance instanceof PluginPresenter) {
                Sport1PlayerActivity activity = (Sport1PlayerActivity) getContext();
                activity.setPresentPluginListener(this);
                ((PluginPresenter) plugin.instance).setPluginModel(plugin.plugin);
                ((PluginPresenter) plugin.instance).presentPlugin(activity, null);
            }
        } else {
            PlayerLoader applicasterPlayerLoader = new PlayerLoader(new ApplicaterPlayerLoaderListener(isInline));
            applicasterPlayerLoader.loadItem();
        }
    }

    @Override
    public void onPresentPluginSuccess() {
        Log.d(TAG, "present plugin success");
        PlayerLoader applicasterPlayerLoader = new PlayerLoader(new ApplicaterPlayerLoaderListener(isInline));
        applicasterPlayerLoader.loadItem();
    }

    @Override
    public void onPresentPluginFailure() {
        //  TODO: process negative plugin result
        Log.d(TAG, "present plugin failed");
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
