package com.example.nearby.things;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.Connections;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static com.example.nearby.things.BuildConfig.NEARBY_SERVICE_ID;

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
                Nearby.Connections.sendPayload(mGoogleApiClient, mRemotePeerEndpoints, Payload.fromBytes("Shutting down host".getBytes(Charset.forName("UTF-8"))));
                Nearby.Connections.stopAllEndpoints(mGoogleApiClient);
                mRemotePeerEndpoints.clear();
            }

            mGoogleApiClient.disconnect();
        }
    }

    private void startAdvertising() {
        Nearby.Connections
                .startAdvertising(mGoogleApiClient, null, NEARBY_SERVICE_ID, new ConnectionLifecycleCallback() {
                            @Override
                            public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                                Log.d(TAG, "onConnectionInitiated. Token: " + connectionInfo.getAuthenticationToken());
                                // Automatically accept the connection on both sides"
                                Nearby.Connections.acceptConnection(mGoogleApiClient, endpointId, new PayloadCallback() {
                                    @Override
                                    public void onPayloadReceived(String endpointId, Payload payload) {
                                        if (payload.getType() == Payload.Type.BYTES) {
                                            Log.d(TAG, "onPayloadReceived: " + new String(payload.asBytes()));
                                            Nearby.Connections.sendPayload(mGoogleApiClient, endpointId, Payload.fromBytes("ACK".getBytes(Charset.forName("UTF-8"))));
                                        }
                                    }

                                    @Override
                                    public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                                        // Provides updates about the progress of both incoming and outgoing payloads
                                    }
                                });
                            }

                            @Override
                            public void onConnectionResult(String endpointId, ConnectionResolution resolution) {
                                Log.d(TAG, "onConnectionResult");
                                if (resolution.getStatus().isSuccess()) {
                                    if (!mRemotePeerEndpoints.contains(endpointId)) {
                                        mRemotePeerEndpoints.add(endpointId);
                                    }
                                    Log.d(TAG, "Connected! (endpointId=" + endpointId + ")");
                                } else {
                                    Log.w(TAG, "Connection to " + endpointId + " failed. Code: " + resolution.getStatus().getStatusCode());
                                }
                            }

                            @Override
                            public void onDisconnected(String endpointId) {
                                // We've been disconnected from this endpoint. No more data can be sent or received.
                                Log.i(TAG, "onDisconnected: " + endpointId);
                            }
                        },
                        new AdvertisingOptions(Strategy.P2P_STAR)
                )
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
}
