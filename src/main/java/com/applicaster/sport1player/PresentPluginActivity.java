package com.applicaster.sport1player;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;

import com.applicaster.plugin_manager.PluginManager;
import com.applicaster.pluginpresenter.PluginPresenter;

import timber.log.Timber;

import static com.applicaster.pluginpresenter.PluginPresenter.PLUGIN_PRESENTER_REQUEST_CODE;

public class PresentPluginActivity extends AppCompatActivity {
    private static final String TAG = PresentPluginActivity.class.getSimpleName();
    static final String PLUGIN_ID_EXTRA = "PLUGIN_ID_EXTRA";
    static final String VALIDATED_EXTRA = "VALIDATED_EXTRA";
    static final String VALIDATION_EVENT = "VALIDATION_EVENT";

    private String mPluginId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.d("PresentPluginActivity onCreate");

        Intent intent = getIntent();
        mPluginId = intent.getStringExtra(PLUGIN_ID_EXTRA);
    }

    @Override
    protected void onStart() {
        super.onStart();

        presentPlugin();
    }

    private void presentPlugin() {
        PluginManager manager = PluginManager.getInstance();
        if (manager != null) {
            if (mPluginId != null && !mPluginId.isEmpty()) {
                Timber.d("PresentPluginActivity present pin plugin");
                PluginManager.InitiatedPlugin plugin = manager.getInitiatedPlugin(mPluginId);

                if (plugin != null && plugin.instance instanceof PluginPresenter) {
                    ((PluginPresenter) plugin.instance).setPluginModel(plugin.plugin);
                    ((PluginPresenter) plugin.instance).presentPlugin(this, null);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == PLUGIN_PRESENTER_REQUEST_CODE) {
            Intent intent = new Intent(VALIDATION_EVENT);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(VALIDATED_EXTRA, resultCode == RESULT_OK);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            finish();
        }
    }
}
