package apps.networklabs.periodicnetwork;

/**
 * Created by Nick on 11/27/2014.
 */

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Toast;

public class MainFragment extends Fragment {

    private static final String[] REPEAT_TIMES = {
            "1 Minute", "2 Minutes", "5 Minutes", "10 Minutes", "15 Minutes", "20 Minutes",
            "30 Minutes", "1 Hour"
    };
    private Spinner mRepeatSpinner;
    private Spinner mActiveSpinner;
    private CheckBox mWifiBox;
    private CheckBox mDataBox;

    private static enum REPEAT_TIME_ENUM {
        ALARM_REPEAT_1_MINUTES ,
        ALARM_REPEAT_2_MINUTES ,
        ALARM_REPEAT_5_MINUTES ,
        ALARM_REPEAT_10_MINUTES,
        ALARM_REPEAT_15_MINUTES,
        ALARM_REPEAT_20_MINUTES,
        ALARM_REPEAT_30_MINUTES,
        ALARM_REPEAT_60_MINUTES
    };

    private static final String[] ACTIVE_TIMES = {
            "30 Seconds", "1 Minute", "2 Minutes", "5 Minutes"
    };

    private static enum ACTIVE_TIME_ENUM {
        ALARM_ACTIVE_30_SECONDS,
        ALARM_ACTIVE_1_MINUTE,
        ALARM_ACTIVE_2_MINUTES,
        ALARM_ACTIVE_5_MINUTES,
    };

    private static final String LOG_TAG = "Main Fragment";

    private MainFragmentButtonsListener mListener;

    public MainFragment() {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (MainFragmentButtonsListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement MainFragmentButtonsListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_scrolling_main, container, false);

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ArrayAdapter<String> repeatTimesAdapter = new ArrayAdapter<String>(
                getActivity().getApplicationContext(),
                R.layout.spinner_dropdown_item,
                REPEAT_TIMES
        );

        ArrayAdapter<String> activeTimesAdapter = new ArrayAdapter<String>(
                getActivity().getApplicationContext(),
                R.layout.spinner_dropdown_item,
                ACTIVE_TIMES
        );

        repeatTimesAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        activeTimesAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);

        mRepeatSpinner = (Spinner) getActivity().findViewById(R.id.repeatTimeSpinner);
        mRepeatSpinner.setAdapter(repeatTimesAdapter);

        mActiveSpinner = (Spinner) getActivity().findViewById(R.id.activeTimeSpinner);
        mActiveSpinner.setAdapter(activeTimesAdapter);

        mWifiBox = (CheckBox) getActivity().findViewById(R.id.wifiBox);
        mDataBox = (CheckBox) getActivity().findViewById(R.id.dataBox);

        final SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        int repeatTimeIndex = sharedPref.getInt(getString(R.string.REPEAT_TIME_NAME), 0);
        int activeTimeIndex = sharedPref.getInt(getString(R.string.ACTIVE_TIME_NAME), 0);
        boolean wifi = sharedPref.getBoolean(getString(R.string.wifi), true);
        boolean data = sharedPref.getBoolean(getString(R.string.mobile_data), true);

        mRepeatSpinner.setSelection(repeatTimeIndex);
        mActiveSpinner.setSelection(activeTimeIndex);
        mWifiBox.setChecked(wifi);
        mDataBox.setChecked(data);

        Button storeButton = (Button) getActivity().findViewById(R.id.store_button);
        storeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "Clicked Store Button, starting service..");
                //startService
                long delayMs = calculateRepeatMs(mRepeatSpinner.getSelectedItemPosition());
                long activeMs = calculateActiveMs(mActiveSpinner.getSelectedItemPosition());

                if ((delayMs - activeMs ) > 0) {
                    String verbose = "WiFi will Toggle every " + REPEAT_TIMES[mRepeatSpinner.getSelectedItemPosition()] +
                            " for " + ACTIVE_TIMES[mActiveSpinner.getSelectedItemPosition()];
                    Toast.makeText(getActivity().getApplicationContext(), verbose, Toast.LENGTH_LONG).show();
                    Log.v(LOG_TAG, verbose);

                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putInt(getString(R.string.REPEAT_TIME_NAME), mRepeatSpinner.getSelectedItemPosition());
                    editor.putInt(getString(R.string.ACTIVE_TIME_NAME), mActiveSpinner.getSelectedItemPosition());
                    editor.putBoolean(getString(R.string.wifi), mWifiBox.isChecked());
                    editor.putBoolean(getString(R.string.mobile_data), mDataBox.isChecked());
                    editor.commit();
                    Log.d(LOG_TAG, "Wifi & Data: " + mWifiBox.isSelected() + " " +
                                                     mDataBox.isSelected());

                    if (mWifiBox.isChecked() || mDataBox.isChecked())  {
                        mListener.storeButtonClicked(delayMs, activeMs,
                                mWifiBox.isChecked(), mDataBox.isChecked());
                    }
                } else {
                    Log.d(LOG_TAG, "Invalid arguments to service.. Quitting!");
                    Toast.makeText(getActivity().getApplicationContext(), "Invalid Entry!", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        });

        Button releaseButton = (Button) getActivity().findViewById(R.id.release_button);
        releaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_TAG, "Clicked Reset Button, stopping service..");
                String verbose = "Cleared WiFi Toggle Settings";
                Toast.makeText(getActivity().getApplicationContext(), verbose, Toast.LENGTH_LONG).show();
                Log.v(LOG_TAG, verbose);
                mListener.releaseButtonClicked();
            }
        });
    }

    public interface MainFragmentButtonsListener {
        public void storeButtonClicked(long repeatMs, long activeMs, boolean wifi, boolean data);
        public void releaseButtonClicked();
    }

    public long calculateRepeatMs(int pos) {
        REPEAT_TIME_ENUM repeatTime = REPEAT_TIME_ENUM.values()[pos];
        long MINUTE_TO_MS = 60 * 1000;
        long ms = 0;
        switch (repeatTime) {
            case ALARM_REPEAT_1_MINUTES:
                ms = 1 * MINUTE_TO_MS; break;
            case ALARM_REPEAT_2_MINUTES:
                ms = 2 * MINUTE_TO_MS; break;
            case ALARM_REPEAT_5_MINUTES:
                ms = 5 * MINUTE_TO_MS; break;
            case ALARM_REPEAT_10_MINUTES:
                ms = 10 * MINUTE_TO_MS; break;
            case ALARM_REPEAT_15_MINUTES:
                ms = 15 * MINUTE_TO_MS; break;
            case ALARM_REPEAT_20_MINUTES:
                ms = 20 * MINUTE_TO_MS; break;
            case ALARM_REPEAT_30_MINUTES:
                ms = 30 * MINUTE_TO_MS; break;
            case ALARM_REPEAT_60_MINUTES:
                ms = 60 * MINUTE_TO_MS; break;
            default:
                ms = 0;
        }
        return ms;
    }

    public long calculateActiveMs(int pos) {
        ACTIVE_TIME_ENUM activeTime = ACTIVE_TIME_ENUM.values()[pos];
        long MINUTE_TO_MS = 60 * 1000;
        long ms = 0;
        switch (activeTime) {
            case ALARM_ACTIVE_30_SECONDS:
                ms = 1 * MINUTE_TO_MS / 2; break;
            case ALARM_ACTIVE_1_MINUTE:
                ms = 1 * MINUTE_TO_MS; break;
            case ALARM_ACTIVE_2_MINUTES:
                ms = 2 * MINUTE_TO_MS; break;
            case ALARM_ACTIVE_5_MINUTES:
                ms = 5 * MINUTE_TO_MS; break;
            default:
                ms = 0;
        }
        return ms;
    }
}
