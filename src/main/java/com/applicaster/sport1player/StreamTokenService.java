package com.applicaster.sport1player;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

public class StreamTokenService {
    private final Gson gson;
    private final String baseUrl;

    public StreamTokenService(String baseUrl) {
        gson = new Gson();
        this.baseUrl = baseUrl;
    }

    private class PlayableAccess {
        @SerializedName("item")
        Item item;
    }

    private class Item {
        @SerializedName("content")
        String content;
    }

    private class Content {
        @SerializedName("token")
        String token;
    }

    public void getStreamToken(String id, String accessToken, Callback callback) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
        RestUtil.get(baseUrl + "/items/" + id + "/access", headers, new Callback() {
            @Override
            public void onResult(String result) {
                try {
                    PlayableAccess playableAccess = gson.fromJson(result, PlayableAccess.class);
                    callback.onResult(gson.fromJson(playableAccess.item.content, Content.class).token);
                } catch (Throwable error) {
                    callback.onError(error);
                }
            }

            @Override
            public void onError(Throwable error) {
                if (error instanceof RestUtil.NetworkException) {
                    int responseCode = ((RestUtil.NetworkException) error).getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        callback.onError(new UnautorizedException());
                        return;
                    }
                }
                callback.onError(error);
            }
        });
    }

    public static class UnautorizedException extends RuntimeException {
    }
}

