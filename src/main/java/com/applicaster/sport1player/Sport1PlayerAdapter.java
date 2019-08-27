package com.applicaster.sport1player;

import android.app.Activity;

import com.applicaster.controller.PlayerLoader;
import com.applicaster.jwplayerplugin.JWPlayerAdapter;
import com.applicaster.player.PlayerLoaderI;
import com.applicaster.plugin_manager.PluginManager;
import com.applicaster.plugin_manager.login.LoginContract;
import com.applicaster.plugin_manager.login.LoginManager;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.applicaster.pluginpresenter.PluginPresenter;

import java.util.Map;

public class Sport1PlayerAdapter extends JWPlayerAdapter implements PresentPluginResultI {
    private static final String PIN_VALIDATION_PLUGIN_ID = "pin_validation_plugin_id";

    private boolean isInline;
    private String validationPluginId;

    @Override
    public void setPluginConfigurationParams(Map params) {
        super.setPluginConfigurationParams(params);
        validationPluginId = (String) params.get(PIN_VALIDATION_PLUGIN_ID);
    }

    @Override
    protected void openLoginPluginIfNeeded(boolean isInline) {
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

    protected void loadItem() {
        if (validationPluginId != null) {
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
        PlayerLoader applicasterPlayerLoader = new PlayerLoader(new ApplicaterPlayerLoaderListener(isInline));
        applicasterPlayerLoader.loadItem();
    }

    @Override
    public void onPresentPluginFailure() {
        //  TODO: process negative plugin result
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
