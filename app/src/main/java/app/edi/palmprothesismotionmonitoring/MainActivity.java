package app.edi.palmprothesismotionmonitoring;

import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.os.Handler;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import database.DatabaseHandler;
import database.Reading;
import lv.edi.BluetoothLib.BluetoothService;
import lv.edi.SmartWearProcessing.Sensor;

public class MainActivity extends AppCompatActivity implements ProcessingServiceEventListener {
    private PatientApplication application;
    private final int REQUEST_ENABLE_BT = 1;
    private MenuItem btConnect;
    private ToggleButton startProcessingButton;
    private long sessionTimer, timeInMilliSeconds = 0L;
    Handler handler = new Handler();

    public static int prescribedFlexion = 70;
    public static long prescribedLength = 10000L;
    public static int prescribedAmount = 10;

    // Store data after session
    public static int totalMovementAmount = 0;
    public static String totalRehabLength;

    private EditText actionTypeEditText;
    private EditText childNameEditText;
    private TextView firstAccAVGValue;
    private TextView secondAccAVGValue;
    private Button setChildNameButton;
    private Button setActionTypeButton;
    // here we will store actionType
    public static String actionType;
    // here we will store child's name
    public static String childName;

    private Vector<Sensor> sensors;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    // DB instance initialization
  DatabaseHandler db = new DatabaseHandler(this);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db.deleteDataFromDB();

//        Reading testReading = db.getReading(200);
//        long yourmilliseconds = testReading.get_timestamp();
//        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
//        Date resultDate = new Date(yourmilliseconds);
//        System.out.println(resultDate);

//        TextView firstAccAVGValue = (TextView) findViewById(R.id.firstAccAVGTextView);
//        TextView secondAccAVGValue = (TextView) findViewById(R.id.secondAccAVGTextView);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        startProcessingButton = (ToggleButton) findViewById(R.id.button_start);
        setSupportActionBar(toolbar);

        application = (PatientApplication) getApplication();
        application.btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (application.btAdapter == null) {
            Toast.makeText(this, getString(R.string.no_bt_support), Toast.LENGTH_SHORT).show();
            finish();
        }
        if (!application.btAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }
        if (application.btService == null) {
            application.btService = new BluetoothService(application.sensors, application.BATTERY_PACKET);
            application.btService.registerBluetoothEventListener(application);
        }

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        application.mainActivity = this;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        Log.d("MAIN_ACTIVITY", "CREATING MENU ");
        getMenuInflater().inflate(R.menu.menu_main, menu);
        btConnect = menu.findItem(R.id.action_bt);
        if (application.btService != null) {
            Log.d("MAIN_ACTIVITY", "BT SERVICE STATUS " + application.btService.isConnected() + " " + application.btService.isConnecting());
            if (application.btService.isConnected()) {
                btConnect.setIcon(R.drawable.check);
                return true;
            }
            if (application.btService.isConnecting()) {
                btConnect.setIcon(R.drawable.loading);
                return true;
            }
            if (!(application.btService.isConnected())) {
                btConnect.setIcon(R.drawable.not);
                return true;
            }
        }
        Log.d("MAIN_ACTIVITY", btConnect.toString());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_bt) {
            //if is connecting block this button
            if (application.btService.isConnecting()) {
                return false;
            }
            if (!(application.btService.isConnected())) {
                if (application.btDevice == null) {
                    Toast.makeText(this, getString(R.string.must_select_bt_device), Toast.LENGTH_SHORT).show();
                    return false;
                }
                application.btService.connectDevice(application.btDevice);
            } else {
                application.btService.disconnectDevice();
            }


        }

        if (id == R.id.action_calibrate) {
            Intent intent = new Intent(this, CalibrationActivity.class);
            startActivity(intent);
        }

        if (id == R.id.action_parameters) {
            Intent intent = new Intent(this, ParametersActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, getString(R.string.bt_must_be_turned_on), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    public void btConnected() {
        runOnUiThread(new Runnable() {
            public void run() {
                btConnect.setIcon(R.drawable.check);
            }
        });
    }

    public void btDisconnected() {
        runOnUiThread(new Runnable() {
            public void run() {
                btConnect.setIcon(R.drawable.not);
            }
        });
    }

    public void btConnecting() {
        runOnUiThread(new Runnable() {
            public void run() {
                btConnect.setIcon(R.drawable.loading);
            }
        });
    }

    public void onClickStartProcessing(View v) {

        if (startProcessingButton.isChecked()) {
            if (!application.btService.isConnected()) {
                Toast.makeText(this, getString(R.string.must_connect_bt), Toast.LENGTH_SHORT).show();
                startProcessingButton.setChecked(false);
                return;
            } else {
                //TODO Take parameters from DB, not hardcoded
                application.processingService = new ProcessingService(application.sensors, 40, prescribedFlexion, prescribedLength);
                application.processingService.registerProcessingServiceEventListener(this);
                application.processingService.startProcessing(20);


                // Thread is used to update TextView objects every second
                Thread t = new Thread() {

                    @Override
                    public void run() {
                        try {
                            while (!isInterrupted()) {
                                Thread.sleep(1000);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
//                                      // TO-DO: CHANGE '1' to actual ChildID
                                        db.addReading(new Reading(
                                            System.currentTimeMillis(),
                                            application.processingService.getAcc1X(), application.processingService.getAcc1Y(), application.processingService.getAcc1Z(),
                                                application.processingService.getAcc2X(), application.processingService.getAcc2Y(), application.processingService.getAcc2Z(),
                                            MainActivity.actionType ));

//                                        firstAccAVGValue.setText(String.valueOf((application.processingService.getAcc1X()+application.processingService.getAcc1Y()
//                                                +application.processingService.getAcc1Z())/3));
//                                        secondAccAVGValue.setText(String.valueOf((application.processingService.getAcc2X()+application.processingService.getAcc2Y()
//                                                +application.processingService.getAcc2Z())/3));
                                    }
                                });
                            }
                        } catch (InterruptedException e) {}
                    }
                };
                t.start();
            }
                // TODO Starting Timer of Rehabilitation

        } else {
            application.processingService.stopProcessing();

            // Starting timer for rehab session length
            sessionTimer = SystemClock.uptimeMillis();
            //handler.postDelayed(updateTimer, 0); CURRENTLY produces EXCEPTION. RUNNABLE DOESN'T get valu of sessionTime view
        }

    }

    public void onClickShowTheGraph(View v) {
        db.getAllReadings();
        if (!startProcessingButton.isChecked()) {
            Intent intent = new Intent(this, GraphActivity.class);
            startActivity(intent);
        } else
            Toast.makeText(this, getString(R.string.must_stop_processing), Toast.LENGTH_SHORT).show();
        }

    public void onClickSetActionType(View v) {
        actionTypeEditText = (EditText) findViewById(R.id.actionTypeEditText);
        actionType = actionTypeEditText.getText().toString();

        // Showing Toast notification in order to notify about activity type change
        Context context = getApplicationContext();
        CharSequence text = "Activity Set: " + actionType;
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    public void onClickSetChildName(View v) {
        childNameEditText = (EditText) findViewById(R.id.chidNameEditText);
        childName = childNameEditText.getText().toString();

        // Showing Toast notification in order to notify about child's name change
        Context context = getApplicationContext();
        CharSequence text = "Child's name set: " + childName;
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    // Updatinng timer value
    public Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            timeInMilliSeconds = SystemClock.uptimeMillis() - sessionTimer;
//            Log.e("MAIN_ACTIVITY", "sessionTIme " + sessionTime);
//            sessionTime.setText("" + ((int) (timeInMilliSeconds / 1000)) / 60 + ":" +
//                    String.format("%02d", (int) (timeInMilliSeconds / 1000)) + ":"
//                    + String.format("%03d", (int) (timeInMilliSeconds % 1000)));
//
//            sessionTime.setTextColor(Color.RED);
            handler.postDelayed(this, 0);
        }
    };

    @Override
    public void onProcessingResult(float angle) {
        final float anglef = angle;
        runOnUiThread(new Runnable() {
            public void run() {
                int progress = (int) (100 * anglef / 90);
//                flexionValue.setProgress(progress);
                long sessionTimeL = application.processingService.getSessionLength();
                String time = String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes(sessionTimeL) % 60, TimeUnit.MILLISECONDS.toSeconds(sessionTimeL) % 60);
//                sessionTime.setText(time);

//                angleView.setCurrentAngle(anglef / 90);
//                angleView.invalidate();
            }
        });
    }

    @Override
    public void onMovementCount(int count) {
        final int countf = count;
        runOnUiThread(new Runnable() {
            public void run() {
//                Log.d("MAIN_ACTIVITY", "movement count view " + movementAmount);
//                movementAmount.setText("" + countf);
            }
        });
    }

    @Override
    public void onStopProcessing() {
        runOnUiThread(new Runnable() {
            public void run() {
                startProcessingButton.setChecked(false);
                Toast.makeText(getApplicationContext(), "Processing finished!", Toast.LENGTH_SHORT).show();
                //TODO Here we push data to DB tables!
//                movementAmount.getText();
//                totalMovementAmount = Integer.parseInt((String) movementAmount.getText());
//                totalRehabLength = (String) sessionTime.getText();
            }
        });

//        Intent intent = new Intent(this, DisplayStatistics.class);
//        startActivity(intent);
    }

    public void setPrescribedFlexionValue(View v) {
        EditText prescribedFlexionValueTextView = (EditText) findViewById(R.id.prescribedFlexionValue);
        String prescribedFlexionValueText = prescribedFlexionValueTextView.getText().toString();
        if (isInteger(prescribedFlexionValueText))
            prescribedFlexion = Integer.parseInt(prescribedFlexionValueText);
    }

    public void setPrescribedRehabLengthValue(View v) {
        EditText prescribedRehabLengthValueTextView = (EditText) findViewById(R.id.prescribedRehabLengthValue);
        String prescribedRehabLengthValueText = prescribedRehabLengthValueTextView.getText().toString();
        if (isLong(prescribedRehabLengthValueText)) {
            prescribedLength = Long.parseLong(prescribedRehabLengthValueText) * 60000;
        }
    }

    public void setPrescribedMovementAmountValue(View v) {
        EditText prescribedMovementAmountValueTextView = (EditText) findViewById(R.id.prescribedMovementAmountValue);
        String setPrescribedMovementAmountValueText = prescribedMovementAmountValueTextView.getText().toString();
        if (isInteger(setPrescribedMovementAmountValueText))
            prescribedAmount = Integer.parseInt(setPrescribedMovementAmountValueText);
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        } catch (NullPointerException e) {
            return false;
        }
        // only got here if we didn't return false
        return true;
    }

    public static boolean isLong(String s) {
        try {
            Long.parseLong(s);
        } catch (NumberFormatException e) {
            return false;
        } catch (NullPointerException e) {
            return false;
        }
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://app.edi.palmprothesismotionmonitoring/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://app.edi.palmprothesismotionmonitoring/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }
}
