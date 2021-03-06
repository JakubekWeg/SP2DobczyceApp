package blackeagle.sp2dobczyceapp;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import java.util.Calendar;

public class LessonFinishService extends Service {
    private final static String ACTION_STOP_SERVICE = "LessonFinishService.stop";

    private static boolean isStarting = false;
    Thread workingThread;
    Notification notification;
    NotificationCompat.Builder builder;
    int[] lessonCounts;
    boolean isStarted = false;
    private LocalBroadcastManager localBroadcastManager;
    private boolean useOldNotification = Build.VERSION.SDK_INT < Build.VERSION_CODES.O;
    private LessonTimeManager.LessonState lessonState;
    private int timeToFinishLesson = 0;
    private AlarmManager alarmMgr;
    private BroadcastReceiver stopListener;
    private BroadcastReceiver screenOnListener;

    static void startService(Context context) {
        if (isStarting)
            return;
        isStarting = true;
        try {
            Settings.loadSettings(context);
            if (!Settings.isReady)
                return;
            if (!Settings.showFinishTimeNotification)
                return;
            context.startService(new Intent(context, LessonFinishService.class));
        } finally {
            isStarting = false;
        }
    }

    static void stopService(Context context) {
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcastSync(new Intent(ACTION_STOP_SERVICE));
    }

    private static boolean isWeekend() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY;
    }

    // @return true if "jest 5 zastepstw", false if "sa 3 zastepstwa"
    private static boolean shouldUseSingleNoun(int number) {
        return !((number > 20 && number % 10 > 1 && number % 10 < 5)
                || (number < 10 && number % 10 < 5));
    }

    private PendingIntent getAlarmIntent() {
        Intent intent = new Intent(this, LessonFinishService.class);
        return PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressLint("SwitchIntDef")
    private void restartServiceWhenNeeded() {
        Calendar calendar = Calendar.getInstance();
        int currentMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        int day = calendar.get(Calendar.DAY_OF_WEEK);

        switch (day) {
            case Calendar.SATURDAY:
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                //no break!
            case Calendar.SUNDAY:
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                break;
            default:
                if (currentMinute >= 7 * 60 + 16) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                }
                break;
        }

        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 15);
        calendar.set(Calendar.SECOND, 0);

        alarmMgr.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), getAlarmIntent());
        stopService();
    }

    @Override
    public int onStartCommand(Intent serviceIntent, int flags, int startId) {
        if (isStarted)
            return START_STICKY;
        isStarted = true;
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        Settings.loadSettings(this);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification notification = new NotificationCompat.Builder(this, Settings.CHANNEL_ID_LESSON_TIME)
                    .setContentText("Odliczanie do końca lekcji")
                    .build();
            //manager.notify(Settings.NOTIFICATION_ID_LESSON_FINISH, notification);
            startForeground(Settings.NOTIFICATION_ID_LESSON_FINISH, notification);
        }

        if (!Settings.showFinishTimeNotification) {
            stopService();
            return START_NOT_STICKY;
        }
        stopListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopService();
            }
        };
        localBroadcastManager.registerReceiver(stopListener, new IntentFilter(ACTION_STOP_SERVICE));

        Settings.createNotificationChannels(getApplicationContext());
        alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        runNotification();

        return START_STICKY;
    }

    private boolean sleepToLessons() {
        try {
            if (isWeekend()) {
                restartServiceWhenNeeded();
                return false;
            }

            LessonTimeManager.LessonState currentLesson = LessonTimeManager.getCurrentLesson(lessonCounts);

            switch (currentLesson.thisState) {
                case LessonTimeManager.LESSON:
                case LessonTimeManager.BREAK:
                case LessonTimeManager.ABOUT_TO_START_LESSON:
                    return true;
                default:
                    restartServiceWhenNeeded();
                    return false;
            }
        } catch (Exception e) {
            //empty
        }
        return false;
    }

    private void runNotification() {
        workingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    screenOnListener = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            updateNotification();
                        }
                    };
                    registerReceiver(screenOnListener, new IntentFilter(Intent.ACTION_SCREEN_ON));
                    lessonCounts = LessonPlanManager.getLessonsCount(LessonFinishService.this);

                    if (sleepToLessons())
                        runSchoolLooper();

                    restartServiceWhenNeeded();
                } catch (InterruptedException e) {
                    if (Settings.showFinishTimeNotification)
                        restartServiceWhenNeeded();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                restartServiceWhenNeeded();
            }
        });
        workingThread.start();
    }

    private void runSchoolLooper() throws InterruptedException {
        lessonState = LessonTimeManager.getCurrentLesson(lessonCounts);
        while ((lessonState.isInSchool() ||
                lessonState.thisState == LessonTimeManager.ABOUT_TO_START_LESSON)
                && Settings.showFinishTimeNotification) {

            timeToFinishLesson = lessonState.getSecondsToEnd();
            updateNotification();

            if (timeToFinishLesson > 180)
                Thread.sleep(60 * 1000);
            else
                Thread.sleep(1000);

            lessonState = LessonTimeManager.getCurrentLesson(lessonCounts);
        }
        cancelNotification();
    }

    private void cancelNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null)
            manager.cancel(Settings.NOTIFICATION_ID_LESSON_FINISH);
    }

    private void updateNotification() {
        if (notification == null) {
            if (useOldNotification) {
                //noinspection deprecation
                builder = new NotificationCompat.Builder(this);
                builder.setPriority(Notification.PRIORITY_HIGH);
            } else
                builder = new NotificationCompat.Builder(this, Settings.CHANNEL_ID_LESSON_TIME);
            builder.setSmallIcon(R.drawable.ic_school_png);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && useOldNotification) {
                builder.setColor(Settings.getColor(this, R.color.colorPrimary));
                builder.setCategory(Notification.CATEGORY_ALARM);
                builder.setVisibility(Notification.VISIBILITY_PUBLIC);
            }
        }


        switch (lessonState.thisState) {
            case LessonTimeManager.LESSON:
                builder.setContentTitle("Aktualnie trwa " + String.valueOf(lessonState.lessonNumber + 1) + " lekcja");
                builder.setContentText(getLeftTimeString());
                break;
            case LessonTimeManager.BREAK:
                builder.setContentTitle("Aktualnie trwa " + String.valueOf(lessonState.lessonNumber + 1) + " przerwa");
                builder.setContentText(getLeftTimeString());
                break;
            case LessonTimeManager.ABOUT_TO_START_LESSON:
                builder.setContentTitle("Wkrótce rozpoczną się lekcje");
                builder.setContentText(getLeftTimeString());
                break;
        }

        builder.setPublicVersion(builder.build());
        notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        //noinspection ConstantConditions
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(Settings.NOTIFICATION_ID_LESSON_FINISH, notification);
    }

    private String getLeftTimeString() {
        int minute = timeToFinishLesson / 60;
        int second = timeToFinishLesson % 60;

        if (minute == 1)
            return "Pozostała jedna minuta";
        if (minute > 1)
            return shouldUseSingleNoun(minute) ?
                    "Pozostało " + minute + " minut" :
                    "Pozostały " + minute + " minuty";
        else
            return shouldUseSingleNoun(second) ?
                    "Pozostało " + second + " sekund" :
                    "Pozostały " + second + " sekundy";
    }

    private void stopService() {
        stopForeground(true);
        stopSelf();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (workingThread != null)
            workingThread.interrupt();
        workingThread = null;
        if (stopListener != null)
            localBroadcastManager.unregisterReceiver(stopListener);
        stopListener = null;
        localBroadcastManager = null;
        if (screenOnListener != null)
            unregisterReceiver(screenOnListener);
        screenOnListener = null;
        cancelNotification();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
