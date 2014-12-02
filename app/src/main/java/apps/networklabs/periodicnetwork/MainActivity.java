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

    public void storeButtonClicked(long repeatMs, long activeMs) {
        Intent serviceIntent = new Intent(this, MainService.class);
        serviceIntent.putExtra(getString(R.string.REPEAT_TIME_NAME), repeatMs);
        serviceIntent.putExtra(getString(R.string.ACTIVE_TIME_NAME), activeMs);

        startService(serviceIntent);
    }

    public void releaseButtonClicked() {
        Intent serviceIntent = new Intent(this, MainService.class);
        stopService(serviceIntent);
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

            long delayMs  = intent.getLongExtra(getString(R.string.REPEAT_TIME_NAME), 0);
            long activeMs = intent.getLongExtra(getString(R.string.ACTIVE_TIME_NAME), 0);

            Log.v(LOG_TAG, "Delay in ms: " + delayMs);
            Log.v(LOG_TAG, "Active in ms: " + activeMs);

            if ((delayMs - activeMs ) > 0) {
                mOnTimer.scheduleAtFixedRate(mNetworkOnTimerTask, delayMs, delayMs);
                mOffTimer.scheduleAtFixedRate(mNetworkOffTimerTask, delayMs + activeMs, delayMs);
            } else {
                Log.d(LOG_TAG, "Invalid arguments to service.. Quitting!");
                Toast.makeText(getApplicationContext(), "Invalid Entry!", Toast.LENGTH_LONG).show();
                stopSelf(startId);
            }

            return Service.START_REDELIVER_INTENT;
        }

        @Override
        public void onCreate() {
            Log.v(LOG_TAG, "Service Created..");
            super.onCreate();
            mNetworkOnTimerTask = new NetworkToggler(getApplicationContext(), true);
            mNetworkOffTimerTask = new NetworkToggler(getApplicationContext(), false);
            mOnTimer = new Timer("NetworkOnToggler");
            mOffTimer = new Timer("NetworkOffToggler");
            mNetworkOffTimerTask.run();
        }

        @Override
        public void onDestroy() {
            Log.v(LOG_TAG, "Service Destroy..");
            super.onDestroy();
            mNetworkOnTimerTask.cancel();
            mNetworkOffTimerTask.cancel();
            mOnTimer.cancel();
            mOffTimer.cancel();
        }
    }

    public static class NetworkToggler extends TimerTask {
        private boolean mMode = true;
        private Context mContext;
        private int mId = 1;

        private static String LOG_TAG = "WiFiToggler";

        public NetworkToggler(Context context, boolean toggle) {
            mContext = context;
            mMode = toggle;
        }

        @Override
        public void run() {
            WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

            if (mMode) {
                //Turn WiFi On
                Log.d(LOG_TAG, "Turning WiFi ON..");
                wifiManager.setWifiEnabled(true);
                setMobileDataEnabled(mContext, true);
                sendNotification("WiFi Enabled");
            } else {
                Log.d(LOG_TAG, "Turning WiFi OFF..");
                //Turn WiFi Off
                wifiManager.setWifiEnabled(false);
                setMobileDataEnabled(mContext, false);
                sendNotification("WiFi Disabled");
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
                    .setContentTitle("PeriodicWiFi")
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentIntent(pIntent).build();

            NotificationManager notificationManager =
                    (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);

            notificationManager.notify(mId, n);
        }
    }
}