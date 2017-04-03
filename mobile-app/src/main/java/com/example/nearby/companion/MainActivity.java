package com.example.nearby.companion;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.Connections;

import java.nio.charset.Charset;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Nearby";
    private static final long TIMEOUT_DISCOVER_MS = 5_000;

    private GoogleApiClient mGoogleApiClient;
    private String mServiceId;
    private String mRemoteHostEndpoint;
    private boolean mIsConnected;
    private TextView mLogs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initLayout();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        log("onConnected: start discovering hosts to send connection requests");
                        startDiscovery();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        log("onConnectionSuspended: " + i);
                        // Try to re-connect
                        mGoogleApiClient.reconnect();
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        log("onConnectionFailed: " + connectionResult.getErrorCode());
                    }
                })
                .addApi(Nearby.CONNECTIONS_API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        log("onStart: connect");
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        log("onStop: disconnect");

        if (mGoogleApiClient.isConnected()) {
            if (!mIsConnected || TextUtils.isEmpty(mRemoteHostEndpoint)) {
                Nearby.Connections.stopDiscovery(mGoogleApiClient, mServiceId);
                return;
            }
            sendMessage("Client disconnecting");
            Nearby.Connections.disconnectFromEndpoint(mGoogleApiClient, mRemoteHostEndpoint);
            mRemoteHostEndpoint = null;
            mIsConnected = false;

            mGoogleApiClient.disconnect();
        }
    }

    private void startDiscovery() {
        log("startDiscovery");
        if (!isConnectedToWiFi()) {
            log("Device is not connected to Wi-Fi");
            return;
        }

        // Discover nearby apps that are advertising with the required service ID.
        Nearby.Connections
                .startDiscovery(mGoogleApiClient, mServiceId, TIMEOUT_DISCOVER_MS, new Connections.EndpointDiscoveryListener() {
                    @Override
                    public void onEndpointFound(String endpointId, String serviceId, String endpointName) {
                        log("onEndpointFound:" + endpointId + ":" + endpointName);

                        log("Send connection request");
                        String name = null; // Human readable name. If null or empty, name will be generated based on the device name or model.
                        byte[] payload = null;
                        Nearby.Connections.sendConnectionRequest(mGoogleApiClient, name, endpointId, payload, new Connections.ConnectionResponseCallback() {
                            @Override
                            public void onConnectionResponse(String endpointId, Status status, byte[] bytes) {
                                Log.i(TAG, "onConnectionResponse:" + endpointId + ":" + status);
                                if (status.isSuccess()) {
                                    Log.i(TAG, "Connected successfully");
                                    Nearby.Connections.stopDiscovery(mGoogleApiClient, mServiceId);
                                    mRemoteHostEndpoint = endpointId;
                                    mIsConnected = true;
                                } else {
                                    Log.w(TAG, "Connection to " + endpointId + " failed");
                                    mIsConnected = false;
                                }
                            }
                        }, new Connections.MessageListener() {
                            @Override
                            public void onMessageReceived(String endpointId, byte[] payload, boolean isReliable) {
                                Log.d(TAG, "onMessageReceived: " + new String(payload));
                            }

                            @Override
                            public void onDisconnected(String endpointId) {
                                log("onDisconnected: " + endpointId);
                            }
                        });
                    }

                    @Override
                    public void onEndpointLost(String endpointId) {
                        log("onEndpointLost:" + endpointId);
                        // An endpoint that was previously available for connection is no longer.
                        // It may have stopped advertising, gone out of range, or lost connectivity.
                    }
                })
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            log("Discovering...");
                        } else {
                            log("Discovering failed: " + status.getStatusMessage() + "(" + status.getStatusCode() + ")");
                        }
                    }
                });
    }

    private void sendMessage(String message) {
        Log.d(TAG, "About to send message: " + message);
        Nearby.Connections.sendReliableMessage(mGoogleApiClient, mRemoteHostEndpoint, message.getBytes(Charset.forName("UTF-8")));
    }

    private boolean isConnectedToWiFi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private void initLayout() {
        setContentView(R.layout.activity_main);
        mServiceId = getString(R.string.nearby_service_id);
        mLogs = (TextView) findViewById(R.id.nearby_logs);

        findViewById(R.id.nearby_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mGoogleApiClient.isConnected()) {
                    log("Not connected");
                    return;
                }

                sendMessage("Hello, Things!");
            }
        });
    }

    private void log(String message) {
        Log.i(TAG, message);
        mLogs.setText(message + "\n" + mLogs.getText());
    }
}
