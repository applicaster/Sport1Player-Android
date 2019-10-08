package com.applicaster.sport1player;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

class Sport1PlayerUtils {
    private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss Z";

    static long dateToTimestamp(String dateString) {
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
}
