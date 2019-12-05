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

    //region public methods

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
        LinkedTreeMap<Object, Object> currentJSONProgram = getCurrentJSONProgram(json);
        if (currentJSONProgram == null)
            return false;

        String fsk = currentJSONProgram.containsKey(FSK_KEY) ? (String) currentJSONProgram.get(FSK_KEY) : "";
        return getFsk(fsk) >= Sport1PlayerUtils.VALIDATION_AGE;
    }

    /**
     * Returns a timeout of when the next program is about to start, that is, the number of seconds for the next program.
     *
     * @param json the json of the teaser URL
     */
    static long getNextProgramTimeout(String json) {
        LinkedTreeMap<Object, Object> currentJSONProgram = getCurrentJSONProgram(json);
        if (currentJSONProgram == null)
            return 0;

        String end = (String) currentJSONProgram.get(END_KEY);
        long endTime = Sport1PlayerUtils.dateToTimestamp(end);
        long now = Sport1PlayerUtils.getCurrentTime(json);
        return (endTime - now) * 1000;
    }

    //endregion

    //region private methods

    private static long getCurrentTime(String json) {
        return Calendar.getInstance().getTimeInMillis() / 1000;
    }

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

    private static double getFsk(String fsk) {
        double fskAge = 0;
        Matcher matcher = Pattern.compile(Sport1PlayerUtils.FSK_PATTERN).matcher(fsk);
        if (matcher.matches())
            fskAge = Double.parseDouble(matcher.group(1));
        return fskAge;
    }

    private static LinkedTreeMap<Object, Object> getCurrentJSONProgram(String json) {
        if (json.isEmpty())
            return null;

        Type mapType = new TypeToken<LinkedTreeMap<Object, Object>>(){}.getType();
        Map<Object, Object> data = new Gson().fromJson(json, mapType);

        long now = Sport1PlayerUtils.getCurrentTime(json);

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
                    if (startTime < now && now <= endTime) {
                        return stream;
                    }
                }
            }
        }

        return null;
    }
}
