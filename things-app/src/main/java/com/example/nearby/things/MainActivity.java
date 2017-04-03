package com.example.nearby.things;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.Connections;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "Nearby";

    private GoogleApiClient mGoogleApiClient;
    private List<String> mRemotePeerEndpoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Android Things project is ready!");

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        Log.d(TAG, "onConnected: advertises on the network as the host");
                        startAdvertising();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.d(TAG, "onConnectionSuspended: " + i);
                        mGoogleApiClient.reconnect();
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Log.d(TAG, "onConnectionFailed: " + connectionResult);
                    }
                })
                .addApi(Nearby.CONNECTIONS_API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Nearby.Connections.stopAdvertising(mGoogleApiClient);

            if (!mRemotePeerEndpoints.isEmpty()) {
                Nearby.Connections.sendReliableMessage(mGoogleApiClient, mRemotePeerEndpoints, "Shutting down host".getBytes(Charset.forName("UTF-8")));
                Nearby.Connections.stopAllEndpoints(mGoogleApiClient);
                mRemotePeerEndpoints.clear();
            }

            mGoogleApiClient.disconnect();
        }
    }

    private void startAdvertising() {
        if (!isConnectedToNetwork()) {
            Log.e(TAG, "Device is not connected to network");
            return;
        }

        Nearby.Connections
                .startAdvertising(mGoogleApiClient, null, null, 0L, new Connections.ConnectionRequestListener() {
                    @Override
                    public void onConnectionRequest(final String endpointId, String deviceId, String endpointName, byte[] handshakeData) {
                        Log.d(TAG, "onConnectionRequest:" + endpointId + ":" + endpointName);

                        // Automatically accept the connection. Also possible to rejectConnectionRequest()
                        Nearby.Connections
                                .acceptConnectionRequest(mGoogleApiClient, endpointId, handshakeData, new Connections.MessageListener() {
                                    @Override
                                    public void onMessageReceived(String endpointId, byte[] payload, boolean isReliable) {
                                        Log.d(TAG, "onMessageReceived: " + new String(payload));
                                        Nearby.Connections.sendReliableMessage(mGoogleApiClient, endpointId, "ACK".getBytes(Charset.forName("UTF-8")));
                                    }

                                    @Override
                                    public void onDisconnected(String endpointId) {
                                        Log.i(TAG, "onDisconnected: " + endpointId);
                                    }
                                })
                                .setResultCallback(new ResultCallback<Status>() {
                                    @Override
                                    public void onResult(@NonNull Status status) {
                                        if (status.isSuccess()) {
                                            if (!mRemotePeerEndpoints.contains(endpointId)) {
                                                mRemotePeerEndpoints.add(endpointId);
                                            }
                                            Log.d(TAG, "Connected!");
                                        }
                                    }
                                });
                    }
                })
                .setResultCallback(new ResultCallback<Connections.StartAdvertisingResult>() {
                    @Override
                    public void onResult(Connections.StartAdvertisingResult result) {
                        Log.d(TAG, "startAdvertising:onResult:" + result);
                        if (result.getStatus().isSuccess()) {
                            Log.d(TAG, "Advertising...");
                        }
                    }
                });
    }

    private boolean isConnectedToNetwork() {
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] nets = connManager.getAllNetworks();
        if (nets != null) {
            for (Network net : nets) {
                NetworkInfo info = connManager.getNetworkInfo(net);
                if (info != null) {
                    Log.d(TAG, "found network: " + info.getTypeName() + ", " + info.getState().name());
                    if (info.isConnectedOrConnecting()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
