package com.applicaster.sport1player;

public interface Callback {
    void onResult(String result);

    void onError(Throwable error);
}
