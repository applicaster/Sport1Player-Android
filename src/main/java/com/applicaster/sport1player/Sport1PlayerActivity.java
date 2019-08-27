package com.applicaster.sport1player;

import android.content.Intent;
import android.support.annotation.Nullable;

import com.applicaster.jwplayerplugin.JWPlayerActivity;

import static com.applicaster.pluginpresenter.PluginPresenter.PLUGIN_PRESENTER_REQUEST_CODE;

public class Sport1PlayerActivity extends JWPlayerActivity {
    private PresentPluginResultI mPresenterListener;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == PLUGIN_PRESENTER_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                mPresenterListener.onPresentPluginSuccess();
            } else {
                mPresenterListener.onPresentPluginFailure();
            }
        }
    }

    protected void setPresentPluginListener(PresentPluginResultI listener) {
        mPresenterListener = listener;
    }
}
