package com.applicaster.sport1player;

import android.content.Context;
import android.content.Intent;

import com.applicaster.atom.model.APAtomEntry;
import com.applicaster.plugin_manager.playersmanager.Playable;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Sport1PlayerUtils {
    private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss Z";

    private static final String TRACKING_INFO_KEY = "tracking_info";
    private static final String EPG_KEY = "epg";
    private static final String START_KEY = "start";
    private static final String END_KEY = "end";
    private static final String FSK_KEY = "fsk";
    private static final String FSK_PATTERN = "FSK\\s*(\\d+)";
    private static final double VALIDATION_AGE = 16f;

    private static long dateToTimestamp(String dateString) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        Date date = new Date();
        try {
            date = dateFormat.parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date.getTime() / 1000;
    }

    static long getCurrentTime() {
        return Calendar.getInstance().getTimeInMillis() / 1000;
    }

    static boolean isValidationNeeded(Playable playable) {
        if (playable.isLive())
            return false;

        APAtomEntry entry = ((APAtomEntry.APAtomEntryPlayable) playable).getEntry();
        Map info = entry.getExtension(TRACKING_INFO_KEY, LinkedTreeMap.class);  //  validation from adapter
        if (info == null) {
            info = entry.getExtension(TRACKING_INFO_KEY, LinkedHashMap.class);  //  validation from activity
        }
        if (info == null) {
            return false;
        }

        String fsk = info.containsKey(FSK_KEY) ? (String) info.get(FSK_KEY) : "";
        return getFsk(fsk) >= VALIDATION_AGE;
    }

    static void displayValidation(Context context, String validationPluginId) {
        Intent intent = new Intent(context, PresentPluginActivity.class);
        intent.putExtra(PresentPluginActivity.PLUGIN_ID_EXTRA, validationPluginId);
        context.startActivity(intent);
    }

    static boolean isLiveValidationNeeded(String json) {
        if (json.isEmpty())
            return false;

        Type mapType = new TypeToken<LinkedTreeMap<Object, Object>>(){}.getType();
        Map<Object, Object> data = new Gson().fromJson(json, mapType);

        long now = Sport1PlayerUtils.getCurrentTime();

        //  Check for age restriction
        if (data.containsKey(EPG_KEY)) {
            List<LinkedTreeMap<Object, Object>> epg = (List<LinkedTreeMap<Object, Object>>) data.get(EPG_KEY);
            if (epg != null && epg.size() > 0) {
                for (int i = 0; i < epg.size(); i++) {
                    LinkedTreeMap<Object, Object> stream = epg.get(i);
                    if (!stream.containsKey(START_KEY) || !stream.containsKey(END_KEY))
                        continue;

                    String start = (String) stream.get(START_KEY);
                    String end = (String) stream.get(END_KEY);
                    long startTime = Sport1PlayerUtils.dateToTimestamp(start);
                    long endTime = Sport1PlayerUtils.dateToTimestamp(end);

                    if (startTime <= now && now < endTime) {
                        String fsk = stream.containsKey(FSK_KEY) ? (String) stream.get(FSK_KEY) : "";
                        return getFsk(fsk) >= Sport1PlayerUtils.VALIDATION_AGE;
                    }

                }
            }
        }

        return false;
    }

    private static double getFsk(String fsk) {
        double fskAge = 0;
        Matcher matcher = Pattern.compile(Sport1PlayerUtils.FSK_PATTERN).matcher(fsk);
        if (matcher.matches())
            fskAge = Double.parseDouble(matcher.group(1));
        return fskAge;
    }

    static long getNextValidationTime(String json) {
        if (json.isEmpty())
            return 0;

        Type mapType = new TypeToken<LinkedTreeMap<Object, Object>>(){}.getType();
        Map<Object, Object> data = new Gson().fromJson(json, mapType);

        long now = Sport1PlayerUtils.getCurrentTime();

        //  Check for age restriction
        if (data.containsKey(EPG_KEY)) {
            boolean isNow = false;
            List<LinkedTreeMap<Object, Object>> epg = (List<LinkedTreeMap<Object, Object>>) data.get(EPG_KEY);
            if (epg != null && epg.size() > 0) {
                for (int i = 0; i < epg.size(); i++) {
                    LinkedTreeMap<Object, Object> stream = epg.get(i);
                    if (!stream.containsKey(START_KEY) || !stream.containsKey(END_KEY))
                        continue;

                    String start = (String) stream.get(START_KEY);
                    String end = (String) stream.get(END_KEY);
                    long startTime = Sport1PlayerUtils.dateToTimestamp(start);
                    long endTime = Sport1PlayerUtils.dateToTimestamp(end);

                    if (isNow && stream.containsKey(FSK_KEY)) {
                        double fskAge = getFsk((String) stream.get(FSK_KEY));
                        if (fskAge >= VALIDATION_AGE) {
                            return startTime;
                        }
                    }

                    if (startTime < now && now < endTime) {
                        isNow = true;
                    }
                }
            }
        }

        return 0;
    }
}
