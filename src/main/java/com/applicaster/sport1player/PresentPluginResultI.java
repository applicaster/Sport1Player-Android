package com.applicaster.sport1player;

import com.applicaster.plugin_manager.PluginManager;

import java.io.Serializable;

interface PresentPluginResultI extends Serializable {
    void onPresentPluginSuccess();
    void onPresentPluginFailure();
    PluginManager getPluginManager();
}