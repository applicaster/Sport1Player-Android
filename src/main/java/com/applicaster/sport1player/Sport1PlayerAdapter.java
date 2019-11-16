package com.applicaster.sport1player;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import com.applicaster.atom.model.APAtomEntry;
import com.applicaster.controller.PlayerLoader;
import com.applicaster.jwplayerplugin.JWPlayerAdapter;
import com.applicaster.player.PlayerLoaderI;
import com.applicaster.plugin_manager.login.LoginContract;
import com.applicaster.plugin_manager.login.LoginManager;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.longtailvideo.jwplayer.events.listeners.VideoPlayerEvents;

import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class Sport1PlayerAdapter extends JWPlayerAdapter implements VideoPlayerEvents.OnFullscreenListener {
    private static final String TAG = Sport1PlayerAdapter.class.getSimpleName();
    private static final String PIN_VALIDATION_PLUGIN_ID = "pin_validation_plugin_id";
    private static final String LIVESTREAM_URL = "livestream_url";

    private static final String STREAM_TOKEN_PARAM_NAME = "access_token";
    private static final String AUTH_ID_EXT_KEY = "auth_id";
    private static final String IN_PLAYER_API_BASE_URL = "https://services.inplayer.com";

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
    private StreamTokenService streamTokenService;

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
        if (loginPlugin != null) {
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
            bundle.putString(Sport1PlayerActivity.LIVEURL_KEY, livestreamUrl);
            //  can't be passed in bundle, due to its size
            Sport1PlayerActivity.liveConfig = livestreamConfig;
            Sport1PlayerActivity.startPlayerActivity(getContext(), bundle, getPluginConfigurationParams());
        }
    }

    private void loadItem() {
        PlayerLoader applicasterPlayerLoader = new PlayerLoader(new ApplicaterPlayerLoaderListener(isInline));
        applicasterPlayerLoader.loadItem();
    }

    /************************** PlayerLoaderI ********************/
    class ApplicaterPlayerLoaderListener implements PlayerLoaderI {
        private boolean isInline;

        ApplicaterPlayerLoaderListener(boolean isInline) {
            this.isInline = isInline;
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
            // Append refreshToken for livestream video.
            Uri contentVideoUri = Uri.parse(playable.getContentVideoURL());
            if (playable.isLive() && contentVideoUri.getQueryParameter(STREAM_TOKEN_PARAM_NAME) == null) {
                APAtomEntry entry = ((APAtomEntry.APAtomEntryPlayable) playable).getEntry();
                String authId = entry.getExtension(AUTH_ID_EXT_KEY, String.class).split("_")[0];
                String accessToken = LoginManager.getLoginPlugin().getToken();
                if (streamTokenService == null) {
                    streamTokenService = new StreamTokenService(IN_PLAYER_API_BASE_URL);
                }
                streamTokenService.getStreamToken(authId, accessToken, new Callback() {
                    @Override
                    public void onResult(String refreshToken) {
                        String tokenedUrl = contentVideoUri.buildUpon()
                                .appendQueryParameter(STREAM_TOKEN_PARAM_NAME, refreshToken)
                                .build()
                                .toString();
                        playable.setContentVideoUrl(tokenedUrl);
                        tryDisplayVideo(playable);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (error instanceof StreamTokenService.UnautorizedException) {
                            LoginContract loginPlugin = LoginManager.getLoginPlugin();
                            if (loginPlugin != null) {
                                loginPlugin.login(getContext(), playable, null, loginResult -> onItemLoaded(playable));
                            }
                        } else {
                            Timber.e(error, "Stream token retrieve failed");
                        }
                    }
                });
            } else {
                tryDisplayVideo(playable);
            }
        }

        private void tryDisplayVideo(Playable playable) {
            if (playable.isLive()) {
                RestUtil.get(livestreamUrl, null, new Callback() {

                    @Override
                    public void onResult(String result) {
                        if (result != null) {
                            livestreamConfig = result;
                            if (Sport1PlayerUtils.isLiveValidationNeeded(result) && !playable.isFree()) {
                                Sport1PlayerUtils.displayValidation(getContext(), validationPluginId);
                            } else
                                displayVideo(isInline);
                        } else {
                            //  No config - request it again
                            tryDisplayVideo(playable);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        //  Failed to retrieve config - request it again
                        tryDisplayVideo(playable);
                    }
                });
            } else {
                if (Sport1PlayerUtils.isValidationNeeded(playable) && !playable.isFree()) {
                    Sport1PlayerUtils.displayValidation(getContext(), validationPluginId);
                } else {
                    displayVideo(isInline);
                }
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
}
