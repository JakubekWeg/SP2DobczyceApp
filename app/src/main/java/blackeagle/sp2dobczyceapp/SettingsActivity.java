package blackeagle.sp2dobczyceapp;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.widget.Toast;


public class SettingsActivity extends PreferenceActivity implements Settings.OnSettingsChangeListener {


    String lastClass;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Settings.loadSettings(this);
        if (!Settings.isReady) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }
        lastClass = Settings.className;

        final MyPreferenceFragment fragment = new MyPreferenceFragment();
        fragment.context = this;
        fragment.changeListener = this;
        getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

    }

    Intent returnData = new Intent();
    @Override
    public void onClassChange() {
        LessonFinishService.stopService();
        LessonFinishService.startService(this);
        LessonPlanWidget.refreshWidgets(getApplicationContext());
        returnData.putExtra("changedClass", true);
        setResult(RESULT_OK, returnData);
    }

    private boolean isDarkTheme;
    @Override
    public void onThemeChange() {
        returnData.putExtra("changedTheme", true);
        LessonPlanWidget.refreshWidgets(getApplicationContext());
        setResult(RESULT_OK, returnData);
        if (isDarkTheme != Settings.applyNowDarkTheme()) {
            startActivity(getIntent());
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Settings.loadSettings(this, true);
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, @StyleRes int resId, boolean first) {
        isDarkTheme = Settings.applyNowDarkTheme();
        theme.applyStyle(isDarkTheme ? R.style.DarkThemeSettings : R.style.AppThemeSettings, true);
    }

    public static class MyPreferenceFragment extends PreferenceFragment{

        Context context = null;
        Settings.OnSettingsChangeListener changeListener = null;

        boolean isDarkTheme;
        @ColorInt int blackIconColor;
        @ColorInt int whiteIconColor;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            isDarkTheme = Settings.applyNowDarkTheme();
            blackIconColor = Settings.getColor(context, R.color.white);
            whiteIconColor = Settings.getColor(context, R.color.black);

            getPreferenceManager().setSharedPreferencesName("settings");

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

            PreferenceCategory category = new PreferenceCategory(context);
            category.setTitle(R.string.main_category);
            screen.addPreference(category);

            final ListPreference listPreference = new ListPreference(context);
            final EditTextPreference luckyNumberPreference = new EditTextPreference(context);
            final CheckBoxPreference teacherCheckBox = new CheckBoxPreference(context);
            final CharSequence[] classes = LessonPlanManager.classesList
                    .toArray(new CharSequence[LessonPlanManager.classesList.size()]);
            final CharSequence[] teachers = LessonPlanManager.teacherList
                    .toArray(new CharSequence[LessonPlanManager.teacherList.size()]);

            final Preference.OnPreferenceChangeListener preferenceListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue instanceof String)
                        Settings.className = (String)newValue;
                    else {
                        Settings.isTeacher = (boolean) newValue;
                        Settings.className = Settings.isTeacher ?
                                LessonPlanManager.teacherList.get(0) : LessonPlanManager.classesList.get(0);
                    }

                    if (Settings.isTeacher) {
                        listPreference.setTitle(context.getString(R.string.your_surname, Settings.className));
                        listPreference.setEntries(teachers);
                        listPreference.setEntryValues(teachers);
                        listPreference.setValue(Settings.className);
                        luckyNumberPreference.setEnabled(false);
                    } else {
                        listPreference.setTitle(context.getString(R.string.your_class, Settings.className));
                        listPreference.setEntries(classes);
                        listPreference.setEntryValues(classes);
                        listPreference.setValue(Settings.className);
                        luckyNumberPreference.setEnabled(true);
                    }

                    if (changeListener != null)
                        changeListener.onClassChange();
                    return true;
                }
            };

            teacherCheckBox.setTitle(R.string.i_am_teacher);
            teacherCheckBox.setKey("isTeacher");
            teacherCheckBox.setOnPreferenceChangeListener(preferenceListener);
            category.addPreference(teacherCheckBox);

            listPreference.setTitle(context.getString(teacherCheckBox.isChecked() ? R.string.your_surname : R.string.your_class,
                    Settings.className));
            listPreference.setEntries(teacherCheckBox.isChecked() ? teachers : classes);
            listPreference.setEntryValues(teacherCheckBox.isChecked() ? teachers : classes);
            listPreference.setKey("className");
            listPreference.setValue(Settings.className);
            listPreference.setSummary(R.string.choose_your_class_or_surname);
            listPreference.setOnPreferenceChangeListener(preferenceListener);
            listPreference.setIcon(getDyedDrawable(R.drawable.ic_people));
            category.addPreference(listPreference);

            luckyNumberPreference.setKey("usersNumber");
            luckyNumberPreference.setDefaultValue("0");
            luckyNumberPreference.setEnabled(!Settings.isTeacher);
            luckyNumberPreference.setIcon(getDyedDrawable(R.drawable.ic_filter_7));
            luckyNumberPreference.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
            luckyNumberPreference.setTitle("Szczęśliwy numerek");
            luckyNumberPreference.setSummary("Dostaniesz powiadomienie gdy zostaniesz szczęśliwcem");
            luckyNumberPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    try {
                        int number = Integer.valueOf((String)newValue);
                        if (number == 0){
                            Toast.makeText(context, "Usunięto szczęśliwy numerek", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        if (number <= 30 )
                            return true;
                    } catch (Exception e){
                        //empty
                    }
                    Toast.makeText(context, "Nieprawidłowa liczba", Toast.LENGTH_SHORT).show();
                    return false;
                }
            });
            category.addPreference(luckyNumberPreference);

            category = new PreferenceCategory(context);
            category.setTitle(R.string.appearance);
            screen.addPreference(category);

            ListPreference darkThemeList = new ListPreference(context);
            CharSequence[] darkThemeEntries = new CharSequence[] { "nigdy", "tylko w nocy", "zawsze"};
            CharSequence[] darkThemeValues = new CharSequence[] { "0", "1", "2"};
            darkThemeList.setEntries(darkThemeEntries);
            darkThemeList.setEntryValues(darkThemeValues);
            darkThemeList.setIcon(getDyedDrawable(R.drawable.ic_style));
            darkThemeList.setTitle(R.string.dark_theme);
            darkThemeList.setSummary(R.string.choose_dark_theme);
            darkThemeList.setKey("darkTheme");
            darkThemeList.setDefaultValue("0");
            darkThemeList.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    try {
                        Settings.darkModeState = Integer.valueOf((String) newValue);
                        if (changeListener != null)
                            changeListener.onThemeChange();
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    return true;
                }
            });
            category.addPreference(darkThemeList);

            category = new PreferenceCategory(context);
            category.setTitle(R.string.working_in_background);
            screen.addPreference(category);

            CheckBoxPreference allowWorkInBgCheckBox = new CheckBoxPreference(context);
            allowWorkInBgCheckBox.setDefaultValue(true);
            allowWorkInBgCheckBox.setKey("canWorkInBackground");
            allowWorkInBgCheckBox.setTitle("Zazwalaj na działanie w tle");
            allowWorkInBgCheckBox.setSummaryOff("Nie dostaniesz powiadomień");
            allowWorkInBgCheckBox.setSummaryOn("Będziesz dostawać powiadomienia");
            allowWorkInBgCheckBox.setIcon(getDyedDrawable(R.drawable.ic_sync));
            allowWorkInBgCheckBox.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (Settings.canWorkInBackground = (boolean)newValue)
                        UpdateService.startService(context);
                    else
                        UpdateService.stopService();
                    return true;
                }
            });
            category.addPreference(allowWorkInBgCheckBox);

            CheckBoxPreference notifyCheckBox = new CheckBoxPreference(context);
            notifyCheckBox.setTitle(R.string.notify_with_sound);
            notifyCheckBox.setKey("canNotify");
            //notifyCheckBox.setSummaryOn("Otrzymasz powiadomienia z dźwiękiem");
            //notifyCheckBox.setSummaryOff("Powiadomienie nie zabrzmi");
            notifyCheckBox.setIcon(getDyedDrawable(R.drawable.ic_notifications));
            notifyCheckBox.setDefaultValue(true);
            category.addPreference(notifyCheckBox);

            category = new PreferenceCategory(context);
            category.setTitle("Czas do końca lekcji");
            screen.addPreference(category);

            CheckBoxPreference showFinishTimeCheckBox = new CheckBoxPreference(context);
            showFinishTimeCheckBox.setIcon(getDyedDrawable(R.drawable.ic_access_time));
            showFinishTimeCheckBox.setKey("showFinishTimeNotification");
            showFinishTimeCheckBox.setDefaultValue(false);
            showFinishTimeCheckBox.setTitle("Pokazuj powiadomienie");
            showFinishTimeCheckBox.setSummary("Powiadomienie które odlicza czas do końca");
            showFinishTimeCheckBox.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (Settings.showFinishTimeNotification = (boolean)newValue)
                        LessonFinishService.startService(context);
                    else
                        LessonFinishService.stopService();
                    return true;
                }
            });
            category.addPreference(showFinishTimeCheckBox);

            EditTextPreference delayEditTextPreference = new EditTextPreference(context);
            delayEditTextPreference.setKey("finishTimeDelay");
            delayEditTextPreference.setDefaultValue("0");
            delayEditTextPreference.getEditText().setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_CLASS_NUMBER);
            delayEditTextPreference.setTitle("Opóźnienie względem dzwonka");
            delayEditTextPreference.setSummary("Dzwonek szkolny nie idzie równo z czasem w twoim telefonie. Ustaw różnicę w sekundach");
            delayEditTextPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    try {
                        int number = Integer.valueOf((String)newValue);
                        if (number <= 60 && number >= -60)
                            return true;
                    } catch (Exception e){
                        //empty
                    }
                    Toast.makeText(context, "Nieprawidłowa liczba", Toast.LENGTH_SHORT).show();
                    return false;
                }
            });

            category.addPreference(delayEditTextPreference);

            setPreferenceScreen(screen);
        }

        private Drawable getDyedDrawable(@DrawableRes int id){
            Drawable drawable = ContextCompat.getDrawable(context, id);
            drawable.setColorFilter(new PorterDuffColorFilter(isDarkTheme ? blackIconColor : whiteIconColor,
                    PorterDuff.Mode.SRC_IN));
            return drawable;
        }
    }
}