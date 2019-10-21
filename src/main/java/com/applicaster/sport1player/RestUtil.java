package com.applicaster.sport1player;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import javax.annotation.Nullable;

public class RestUtil {

    public static void get(final String requestUrl, @Nullable Map<String, String> headers, final Callback callback) {
        //  outer class isn't an Activity or Fragment, so no leak produced here
        @SuppressLint("StaticFieldLeak")
        class Get extends AsyncTask<Void, Void, ResultHolder> {
            @Override
            protected ResultHolder doInBackground(Void... voids) {
                BufferedReader bufferedReader = null;
                try {
                    URL url = new URL(requestUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    if (headers != null) {
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            connection.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        StringBuilder builder = new StringBuilder();

                        bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String json;
                        while ((json = bufferedReader.readLine()) != null) {
                            builder.append(json).append("\n");
                        }
                        return new ResultHolder(builder.toString().trim(), null);
                    } else if (connection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED){
                        return new ResultHolder(null, new UnautorizedException());
                    } else {
                        return new ResultHolder(null, new NetworkException());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (bufferedReader != null) {
                        try {
                            bufferedReader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(ResultHolder resultHolder) {
                if (resultHolder.error != null) {
                    callback.onError(resultHolder.error);
                } else {
                    callback.onResult(resultHolder.result);
                }
            }
        }
        new Get().execute();
    }

    static class ResultHolder {
        Throwable error;
        String result;

        public ResultHolder(String result, Throwable error) {
            this.error = error;
            this.result = result;
        }
    }

    public static class UnautorizedException extends RuntimeException {
    }

    static class NetworkException extends RuntimeException {
        public NetworkException() {
            super("Network error occurred");
        }
    }
}
