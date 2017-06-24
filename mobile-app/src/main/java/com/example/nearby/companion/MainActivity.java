package com.example.nearby.companion;

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
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.nio.charset.Charset;

import static com.example.nearby.companion.BuildConfig.NEARBY_SERVICE_ID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Nearby";

    private GoogleApiClient mGoogleApiClient;
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
                Nearby.Connections.stopDiscovery(mGoogleApiClient);
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

        Nearby.Connections.startDiscovery(mGoogleApiClient, NEARBY_SERVICE_ID, new EndpointDiscoveryCallback() {
                    @Override
                    public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                        log("onEndpointFound:" + endpointId + ":" + info.getEndpointName());

                        Nearby.Connections
                                .requestConnection(mGoogleApiClient, null, endpointId, new ConnectionLifecycleCallback() {
                                    @Override
                                    public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                                        log("onConnectionInitiated. Token: " + connectionInfo.getAuthenticationToken());
                                        // Automatically accept the connection on both sides"
                                        Nearby.Connections.acceptConnection(mGoogleApiClient, endpointId, new PayloadCallback() {
                                            @Override
                                            public void onPayloadReceived(String endpointId, Payload payload) {
                                                if (payload.getType() == Payload.Type.BYTES) {
                                                    log("onPayloadReceived: " + new String(payload.asBytes()));
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
                                        log("onConnectionResult:" + endpointId + ":" + resolution.getStatus());
                                        if (resolution.getStatus().isSuccess()) {
                                            log("Connected successfully");
                                            Nearby.Connections.stopDiscovery(mGoogleApiClient);
                                            mRemoteHostEndpoint = endpointId;
                                            mIsConnected = true;
                                        } else {
                                            if (resolution.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED) {
                                                log("The connection was rejected by one or both sides");
                                            } else {
                                                log("Connection to " + endpointId + " failed. Code: " + resolution.getStatus().getStatusCode());
                                            }
                                            mIsConnected = false;
                                        }
                                    }

                                    @Override
                                    public void onDisconnected(String endpointId) {
                                        // We've been disconnected from this endpoint. No more data can be sent or received.
                                        log("onDisconnected: " + endpointId);
                                    }
                                })
                                .setResultCallback(new ResultCallback<Status>() {
                                    @Override
                                    public void onResult(@NonNull Status status) {
                                        if (status.isSuccess()) {
                                            // We successfully requested a connection. Now both sides
                                            // must accept before the connection is established.
                                        } else {
                                            // Nearby Connections failed to request the connection.
                                        }
                                    }
                                });
                    }

                    @Override
                    public void onEndpointLost(String endpointId) {
                        // An endpoint that was previously available for connection is no longer.
                        // It may have stopped advertising, gone out of range, or lost connectivity.
                        log("onEndpointLost:" + endpointId);
                    }
                },
                new DiscoveryOptions(Strategy.P2P_STAR)
        )
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
        log("About to send message: " + message);
        Nearby.Connections.sendPayload(mGoogleApiClient, mRemoteHostEndpoint, Payload.fromBytes(message.getBytes(Charset.forName("UTF-8"))));
    }

    private void initLayout() {
        setContentView(R.layout.activity_main);
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
