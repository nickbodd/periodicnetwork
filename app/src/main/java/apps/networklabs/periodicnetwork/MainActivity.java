package apps.networklabs.periodicnetwork;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends Activity implements MainFragment.MainFragmentButtonsListener {

    private Intent serviceIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            MainFragment fragment = new MainFragment();
            getFragmentManager().beginTransaction()
                    .add(R.id.container, fragment)
                    .commit();
        }
    }

    public void storeButtonClicked(long repeatMs, long activeMs, boolean wifi, boolean data) {
        Intent serviceIntent = new Intent(this, MainService.class);
        stopService(serviceIntent);

        serviceIntent.putExtra(getString(R.string.REPEAT_TIME_NAME), repeatMs);
        serviceIntent.putExtra(getString(R.string.ACTIVE_TIME_NAME), activeMs);
        serviceIntent.putExtra(getString(R.string.wifi), wifi);
        serviceIntent.putExtra(getString(R.string.mobile_data), data);

        startService(serviceIntent);
    }

    public void releaseButtonClicked() {
        Intent serviceIntent = new Intent(this, MainService.class);
        stopService(serviceIntent);
        serviceIntent.putExtra(getString(R.string.NETWORK_ON), true);
        startService(serviceIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class MainService extends Service {
        private TimerTask mNetworkOnTimerTask;
        private TimerTask mNetworkOffTimerTask;
        private Timer mOnTimer;
        private Timer mOffTimer;

        private static final String LOG_TAG = "Main Service";

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.v(LOG_TAG, "Service Started..");
            super.onStartCommand(intent, flags, startId);

            boolean networkOn = intent.getBooleanExtra(getString(R.string.NETWORK_ON), false);
            if (false == networkOn) {

                long delayMs = intent.getLongExtra(getString(R.string.REPEAT_TIME_NAME), 0);
                long activeMs = intent.getLongExtra(getString(R.string.ACTIVE_TIME_NAME), 0);
                boolean wifi = intent.getBooleanExtra(getString(R.string.wifi), true);
                boolean data = intent.getBooleanExtra(getString(R.string.mobile_data), true);

                mNetworkOnTimerTask = new NetworkToggler(getApplicationContext(), true, wifi, data);
                mNetworkOffTimerTask = new NetworkToggler(getApplicationContext(), false, wifi, data);
                mNetworkOffTimerTask.run();

                Log.v(LOG_TAG, "Delay in ms: " + delayMs);
                Log.v(LOG_TAG, "Active in ms: " + activeMs);

                mOnTimer.scheduleAtFixedRate(mNetworkOnTimerTask, delayMs, delayMs);
                mOffTimer.scheduleAtFixedRate(mNetworkOffTimerTask, delayMs + activeMs, delayMs);

                return Service.START_REDELIVER_INTENT;
            } else {
                Log.v(LOG_TAG, "Turning ON before quitting..");
                new NetworkToggler(getApplicationContext(), true, true, true).run();
                stopSelf();
                return  Service.START_NOT_STICKY;
            }
        }

        @Override
        public void onCreate() {
            Log.v(LOG_TAG, "Service Created..");
            super.onCreate();
            mOnTimer = new Timer("NetworkOnToggler");
            mOffTimer = new Timer("NetworkOffToggler");
        }

        @Override
        public void onDestroy() {
            Log.v(LOG_TAG, "Service Destroy..");
            mOnTimer.cancel();
            mOffTimer.cancel();
            super.onDestroy();
        }
    }

    public static class NetworkToggler extends TimerTask {
        private boolean mMode = true;
        private boolean mWifi = true;
        private boolean mData =  true;
        private Context mContext;
        private int mId = 1;

        private static String LOG_TAG = "NetworkToggler";

        public NetworkToggler(Context context, boolean toggle, boolean wifi, boolean data) {
            mContext = context;
            mMode = toggle;
            mWifi = wifi;
            mData = data;
        }

        @Override
        public void run() {
            WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

            String verbose = "Turning Network OFF";
            if (mMode) { verbose = "Turning Network ON"; }
            Log.d(LOG_TAG, verbose);

            if (mWifi) {
                wifiManager.setWifiEnabled(mMode);
            }

            if (mData) {
                setMobileDataEnabled(mContext, mMode);
            }

            if (mMode) {
                if (mWifi && mData) {
                    sendNotification("WiFi & Data Enabled");
                } else if (mWifi) {
                    sendNotification("WiFi Enabled");
                } else {
                    sendNotification("Data Enabled");
                }
            } else  {
                if (mWifi && mData) {
                    sendNotification("WiFi & Data Disabled");
                } else if (mWifi) {
                    sendNotification("WiFi Disabled");
                } else {
                    sendNotification("Data Disabled");
                }
            }
        }

        private void setMobileDataEnabled(Context context, boolean enabled) {
            final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            try {
                final Class conmanClass = Class.forName(conman.getClass().getName());
                final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
                iConnectivityManagerField.setAccessible(true);
                final Object iConnectivityManager = iConnectivityManagerField.get(conman);
                final Class iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
                final Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
                setMobileDataEnabledMethod.setAccessible(true);

                setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }

        public void sendNotification(String text) {
            Intent intent = new Intent(mContext, MainActivity.class);
            PendingIntent pIntent = PendingIntent.getActivity(mContext, 0, intent, 0);

            Notification n  = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getString(R.string.app_name))
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentIntent(pIntent).build();

            NotificationManager notificationManager =
                    (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);

            notificationManager.notify(mId, n);
        }
    }
}
