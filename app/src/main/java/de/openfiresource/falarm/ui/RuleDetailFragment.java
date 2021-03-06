package de.openfiresource.falarm.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;

import com.orhanobut.logger.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import de.openfiresource.falarm.R;
import de.openfiresource.falarm.models.Notification;
import de.openfiresource.falarm.models.OperationRule;

/**
 * A fragment representing a single Rule detail screen.
 * This fragment is either contained in a {@link RuleListActivity}
 * in two-pane mode (on tablets) or a {@link RuleDetailActivity}
 * on handsets.
 */
public class RuleDetailFragment extends PreferenceFragment {
    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_ITEM_ID = "item_id";

    /**
     * The actual notification rule.
     */
    private OperationRule mItem;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public RuleDetailFragment() {
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value and save the preference to the database.
     */
    private Preference.OnPreferenceChangeListener sBindPreferenceToDatabaseListener = (preference, value) -> {
        if (value == null)
            return true;
        String methodName = getMethodFromPrefKey(preference, "set");
        String stringValue = value.toString();
        Method setter;

        try {
            if (preference instanceof CheckBoxPreference) {
                setter = mItem.getClass().getMethod(methodName, boolean.class);
                setter.invoke(mItem, Boolean.parseBoolean(stringValue));
            } else {
                setter = mItem.getClass().getMethod(methodName, String.class);
                setter.invoke(mItem, stringValue);
                preference.setSummary(stringValue);
            }
        } catch (SecurityException e) {
            Logger.e(e, "Security Exception on relfection");
        } catch (NoSuchMethodException e) {
            Logger.e(e, "Getter not found");
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        mItem.save();
        //Send Broadcast (title or time changed)
        Intent brIntent = new Intent();
        brIntent.setAction(RuleListActivity.INTENT_RULE_CHANGED);
        getActivity().sendBroadcast(brIntent);

        return true;
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItem = OperationRule.findById(OperationRule.class, getArguments().getLong(ARG_ITEM_ID));

            final Activity activity = this.getActivity();
            Toolbar appBarLayout = (Toolbar) activity.findViewById(R.id.toolbar);
            if (appBarLayout != null) {
                appBarLayout.setTitle(mItem.getTitle());
            }

            addPreferencesFromResource(R.xml.rule_pref);

            int preferences = getPreferenceScreen().getPreferenceCount();
            for (int i = 0; i < preferences; i++) {
                Preference preference = getPreferenceScreen().getPreference(i);
                bindPreferenceToDatabase(preference);
            }

            Preference notification = new Preference(getActivity());
            this.getPreferenceScreen().addPreference(notification);
            notification.setTitle(getString(R.string.pref_title_notification));
            notification.setSummary(getString(R.string.pref_desc_notification));
            notification.setDependency("rule_ownNotification");
            notification.setOnPreferenceClickListener(preference -> {
                Bundle bundle = new Bundle();
                bundle.putLong(SettingsActivity.NotificationPreferenceFragment.ARG_RULE_ID, mItem.getId());
                SettingsActivity.NotificationPreferenceFragment fragment
                        = new SettingsActivity.NotificationPreferenceFragment();
                fragment.setArguments(bundle);

                getFragmentManager().beginTransaction()
                        .replace(R.id.rule_detail_container, fragment)
                        .addToBackStack(null)
                        .commit();

                return true;
            });

            Preference delete = new Preference(getActivity());
            delete.setTitle(getString(R.string.pref_title_delete));
            delete.setSummary(getString(R.string.pref_desc_delete));
            delete.setOnPreferenceClickListener(preference -> {
                // User clicked delete button.
                // Confirm that's what they want.
                new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.pref_title_delete))
                        .setMessage(getString(R.string.pref_desc_delete))
                        .setPositiveButton(getString(R.string.delete),
                                (dialog, whichButton) -> {
                                    new Notification(mItem.getId(), getActivity()).delete();
                                    mItem.delete();
                                    if (activity.getClass().equals(RuleDetailActivity.class))
                                        activity.navigateUpTo(new Intent(activity, RuleListActivity.class));
                                    else
                                        startActivity(new Intent(activity, RuleListActivity.class));
                                })
                        .setNegativeButton(getString(R.string.cancel),
                                (dialog, whichButton) -> {
                                    // No need to take any action.
                                }).show();
                return true;
            });
            this.getPreferenceScreen().addPreference(delete);
        }
    }

    private String getMethodFromPrefKey(Preference preference, String type) {
        String methodName = preference.getKey().substring(5); //Cut rule_
        methodName = type + methodName.substring(0, 1).toUpperCase() + methodName.substring(1); //GetX
        return methodName;
    }

    private void bindPreferenceToDatabase(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceToDatabaseListener);

        // Trigger the listener immediately with the preference's
        // current value.
        Method getter;
        String type = "get";
        if (preference instanceof CheckBoxPreference) type = "is";
        String methodName = getMethodFromPrefKey(preference, type);

        try {
            getter = mItem.getClass().getMethod(methodName);
            Object value = getter.invoke(mItem);

            if (preference instanceof CheckBoxPreference)
                ((CheckBoxPreference) preference).setChecked((Boolean) value);

            sBindPreferenceToDatabaseListener.onPreferenceChange(preference, value);
        } catch (SecurityException e) {
            Logger.e(e, "Security Exception on relfection");
        } catch (NoSuchMethodException e) {
            Logger.e(e, "Getter not found");
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
