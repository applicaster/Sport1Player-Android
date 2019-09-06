package com.applicaster.sport1player;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.applicaster.plugin_manager.PluginManager;
import com.applicaster.pluginpresenter.PluginPresenter;

import static com.applicaster.pluginpresenter.PluginPresenter.PLUGIN_PRESENTER_REQUEST_CODE;

public class PresentPluginActivity extends AppCompatActivity {
    private static final String TAG = PresentPluginActivity.class.getSimpleName();
    static final String PLUGIN_ID_EXTRA = "PLUGIN_ID_EXTRA";
    static final String CALLBACK_EXTRA = "CALLBACK_EXTRA";

    private PresentPluginResultI mPresenterListener;
    private String mPluginId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mPluginId = intent.getStringExtra(PLUGIN_ID_EXTRA);
        mPresenterListener = (PresentPluginResultI) intent.getSerializableExtra(CALLBACK_EXTRA);
    }

    @Override
    protected void onStart() {
        super.onStart();

        presentPlugin();
    }

    private void presentPlugin() {
        if (mPresenterListener.getPluginManager() == null)
            Log.e(TAG, "Plugin manager = null!");
        if (mPluginId != null && !mPluginId.isEmpty()) {
            Log.d(TAG, "Presenting plugin: " + mPluginId);
            PluginManager.InitiatedPlugin plugin = mPresenterListener.getPluginManager().getInitiatedPlugin(mPluginId);

            if (plugin != null && plugin.instance instanceof PluginPresenter) {
                ((PluginPresenter) plugin.instance).setPluginModel(plugin.plugin);
                ((PluginPresenter) plugin.instance).presentPlugin(this, null);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == PLUGIN_PRESENTER_REQUEST_CODE) {
            Log.d(TAG, "Plugin presenter result");
            if (resultCode == RESULT_OK) {
                mPresenterListener.onPresentPluginSuccess();
            } else {
                mPresenterListener.onPresentPluginFailure();
            }
            finish();
        }
    }
}
