package com.telerik.plugins.mapbox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.graphics.PointF;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.LocationComponentOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.maps.UiSettings;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;
import com.mapbox.mapboxsdk.offline.OfflineRegionError;
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus;
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import io.cordova.hellocordova.R;

import static org.apache.cordova.Whitelist.TAG;


// TODO for screen rotation, see https://www.mapbox.com/mapbox-android-sdk/#screen-rotation
// TODO fox Xwalk compat, see nativepagetransitions plugin
// TODO look at demo app: https://github.com/mapbox/mapbox-gl-native/blob/master/android/java/MapboxGLAndroidSDKTestApp/src/main/java/com/mapbox/mapboxgl/testapp/MainActivity.java
public class MapBox extends CordovaPlugin {

    public static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    public static final String COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static final int LOCATION_REQ_CODE = 0;

    public static final int PERMISSION_DENIED_ERROR = 20;

    private static final String MAPBOX_ACCESSTOKEN_RESOURCE_KEY = "mapbox_accesstoken";
    // JSON encoding/decoding
    public static final String JSON_CHARSET = "UTF-8";
    public static final String JSON_FIELD_REGION_NAME = "FIELD_REGION_NAME";
    private static final String ACTION_SHOW = "show";
    private static final String ACTION_HIDE = "hide";
    private static final String ACTION_ADD_MARKERS = "addMarkers";
    private static final String ACTION_REMOVE_ALL_MARKERS = "removeAllMarkers";
    private static final String ACTION_ADD_MARKER_CALLBACK = "addMarkerCallback";
    // TODO:
    // private static final String ACTION_REMOVE_MARKER_CALLBACK = "removeMarkerCallback";
    private static final String ACTION_ADD_POLYGON = "addPolygon";
    private static final String ACTION_ADD_GEOJSON = "addGeoJSON";
    private static final String ACTION_GET_CENTER = "getCenter";
    private static final String ACTION_SET_CENTER = "setCenter";
    private static final String ACTION_GET_ZOOMLEVEL = "getZoomLevel";
    private static final String ACTION_SET_ZOOMLEVEL = "setZoomLevel";
    private static final String ACTION_GET_BOUNDS = "getBounds";
    private static final String ACTION_SET_BOUNDS = "setBounds";
    private static final String ACTION_GET_TILT = "getTilt";
    private static final String ACTION_SET_TILT = "setTilt";
    private static final String ACTION_SET_OFFLINE = "setOffline";
    private static final String ACTION_ANIMATE_CAMERA = "animateCamera";
    private static final String ACTION_ON_REGION_WILL_CHANGE = "onRegionWillChange";
    private static final String ACTION_ON_REGION_IS_CHANGING = "onRegionIsChanging";
    private static final String ACTION_ON_REGION_DID_CHANGE = "onRegionDidChange";
    // TODO:
    // private static final String ACTION_OFF_REGION_WILL_CHANGE = "offRegionWillChange";
    // private static final String ACTION_OFF_REGION_IS_CHANGING = "offRegionIsChanging";
    // private static final String ACTION_OFF_REGION_DID_CHANGE = "offRegionDidChange";

    public static MapView mapView;
    private static float retinaFactor;
    private String accessToken;
    private CallbackContext callback;
    private CallbackContext markerCallbackContext;

    private boolean showUserLocation;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        DisplayMetrics metrics = new DisplayMetrics();
        cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        retinaFactor = metrics.density;

        try {
            int mapboxAccesstokenResourceId = cordova.getActivity().getResources().getIdentifier(MAPBOX_ACCESSTOKEN_RESOURCE_KEY, "string", cordova.getActivity().getPackageName());
            accessToken = cordova.getActivity().getString(mapboxAccesstokenResourceId);
        } catch (Resources.NotFoundException e) {
            // we'll deal with this when the accessToken property is read, but for now let's dump the error:
            e.printStackTrace();
        }
    }

    @Override
    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) throws JSONException {

        this.callback = callbackContext;

        try {
            if (ACTION_SHOW.equals(action)) {
                final JSONObject options = args.getJSONObject(0);
                final String style = getStyle(options.optString("style"));

                final JSONObject margins = options.isNull("margins") ? null : options.getJSONObject("margins");
                final int left = applyRetinaFactor(margins == null || margins.isNull("left") ? 0 : margins.getInt("left"));
                final int right = applyRetinaFactor(margins == null || margins.isNull("right") ? 0 : margins.getInt("right"));
                final int top = applyRetinaFactor(margins == null || margins.isNull("top") ? 0 : margins.getInt("top"));
                final int bottom = applyRetinaFactor(margins == null || margins.isNull("bottom") ? 0 : margins.getInt("bottom"));

                final JSONObject center = options.isNull("center") ? null : options.getJSONObject("center");

                this.showUserLocation = !options.isNull("showUserLocation") && options.getBoolean("showUserLocation");

                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (accessToken == null) {
                            callbackContext.error(MAPBOX_ACCESSTOKEN_RESOURCE_KEY + " not set in strings.xml");
                            return;
                        }
                        Mapbox.getInstance(webView.getContext(), accessToken);
                        mapView = new MapView(webView.getContext());
                        //accessToken

                        // need to do this to register a receiver which onPause later needs
                        mapView.onResume();
                        mapView.onCreate(null);

                        mapView.getMapAsync(new OnMapReadyCallback() {
                            @Override
                            public void onMapReady(@NonNull MapboxMap mapboxMap) {

                                mapboxMap.setStyle(style, new Style.OnStyleLoaded() {
                                    @Override
                                    public void onStyleLoaded(@NonNull Style style) {
                                        try {
                                            UiSettings uiSettings = mapboxMap.getUiSettings();
                                            uiSettings.setCompassEnabled(options.isNull("hideCompass") || !options.getBoolean("hideCompass"));
                                            uiSettings.setRotateGesturesEnabled(options.isNull("disableRotation") || !options.getBoolean("disableRotation"));
                                            uiSettings.setScrollGesturesEnabled(options.isNull("disableScroll") || !options.getBoolean("disableScroll"));
                                            uiSettings.setZoomGesturesEnabled(options.isNull("disableZoom") || !options.getBoolean("disableZoom"));
                                            uiSettings.setTiltGesturesEnabled(options.isNull("disableTilt") || !options.getBoolean("disableTilt"));

                                            if (!options.isNull("hideAttribution") && options.getBoolean("hideAttribution")) {
                                                uiSettings.setAttributionMargins(-300, 0, 0, 0);
                                            }
                                            if (!options.isNull("hideLogo") && options.getBoolean("hideLogo")) {
                                                uiSettings.setLogoMargins(-300, 0, 0, 0);
                                            }

                                            if (showUserLocation) {
                                                showUserLocation(mapboxMap);
                                            }

                                            Double zoom = options.isNull("zoomLevel") ? 10 : options.getDouble("zoomLevel");
                                            float zoomLevel = zoom.floatValue();
                                            if (center != null) {
                                                if (zoomLevel > 18.0) {
                                                    zoomLevel = 18.0f;
                                                }
                                                final double lat = center.getDouble("lat");
                                                final double lng = center.getDouble("lng");
                                                mapboxMap.setCameraPosition(new CameraPosition.Builder()
                                                        .target(new LatLng(lat, lng))
                                                        .zoom(zoomLevel)
                                                        .build());
                                            }

                                            if (options.has("markers")) {
                                                addMarkers(options.getJSONArray("markers"));
                                            }
                                        } catch (Exception e) {
                                            callbackContext.error(e.getMessage());
                                        }

                                    }
                                });

                            }
                        });

                        // position the mapView overlay
                        int webViewWidth = webView.getView().getWidth();
                        int webViewHeight = webView.getView().getHeight();
                        final FrameLayout layout = (FrameLayout) webView.getView().getParent();
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(webViewWidth - left - right, webViewHeight - top - bottom);
                        params.setMargins(left, top, right, bottom);
                        mapView.setLayoutParams(params);
                        layout.addView(mapView);
                        callbackContext.success();
                    }
                });
            } else if (ACTION_SET_OFFLINE.equals(action)) {
                if (mapView != null) {
                    final JSONObject options = args.getJSONObject(0);
                    final JSONObject northEast = options.isNull("northEast") ? null : options.getJSONObject("northEast");
                    final JSONObject southWest = options.isNull("southWest") ? null : options.getJSONObject("southWest");
                    final int minZoom = options.isNull("minZoom") ? 1 : options.getInt("minZoom");
                    final int maxZoom = options.isNull("maxZoom") ? 15 : options.getInt("maxZoom");
                    final String regionName = options.isNull("regionName") ? "Map" : options.getString("regionName");

                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            try {
                                double north = 0;
                                double east = 0;
                                double south = 0;
                                double west = 0;
                                if (northEast != null) {
                                    north = northEast.getDouble("lat");
                                    east = northEast.getDouble("lng");
                                }
                                if (southWest != null) {
                                    south = southWest.getDouble("lat");
                                    west = southWest.getDouble("lng");
                                }
                                if (north == 0 && east == 0 && south == 0 && west == 0) {
                                    callbackContext.success();
                                    return;
                                }

                                OfflineManager offlineManager = OfflineManager.getInstance(webView.getContext());
                                OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(
                                        Style.LIGHT,
                                        new LatLngBounds.Builder()
                                                .include(new LatLng(north, east))
                                                .include(new LatLng(south, west))
                                                .build(),
                                        minZoom,
                                        maxZoom,
                                        webView.getContext().getResources().getDisplayMetrics().density
                                );
// Create the region asynchronously
                                byte[] metadata;
                                try {
                                    JSONObject jsonObject = new JSONObject();
                                    jsonObject.put(JSON_FIELD_REGION_NAME, regionName);
                                    String json = jsonObject.toString();
                                    metadata = json.getBytes(JSON_CHARSET);
                                } catch (Exception exception) {
                                    Log.e(TAG, "Failed to encode metadata: " + exception.getMessage());
                                    metadata = null;
                                }
                                if (metadata != null) {
                                    // Create the region asynchronously


                                    offlineManager.createOfflineRegion(definition, metadata,
                                            new OfflineManager.CreateOfflineRegionCallback() {
                                                @Override
                                                public void onCreate(OfflineRegion offlineRegion) {
                                                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);

// Monitor the download progress using setObserver
                                                    offlineRegion.setObserver(new OfflineRegion.OfflineRegionObserver() {
                                                        @Override
                                                        public void onStatusChanged(OfflineRegionStatus status) {

// Calculate the download percentage
                                                            double percentage = status.getRequiredResourceCount() >= 0
                                                                    ? (100.0 * status.getCompletedResourceCount() / status.getRequiredResourceCount()) :
                                                                    0.0;

                                                            if (status.isComplete()) {
// Download complete

                                                                sendPluginResult(callbackContext,"Region downloaded successfully.");
                                                            } else if (status.isRequiredResourceCountPrecise()) {
                                                                sendPluginResult(callbackContext,String.format("%.2f Percent Downloaded", percentage));
                                                            }
                                                        }

                                                        @Override
                                                        public void onError(OfflineRegionError error) {
// If an error occurs, print to logcat
                                                            callbackContext.error("onError reason: " + error.getReason());
                                                            makeToast("onError reason: " + error.getReason());
                                                            makeToast("onError message: " + error.getMessage());
                                                        }

                                                        @Override
                                                        public void mapboxTileCountLimitExceeded(long limit) {
// Notify if offline region exceeds maximum tile count
                                                            makeToast("Mapbox tile count limit exceeded: " + limit);
                                                            Log.e(TAG, "Mapbox tile count limit exceeded: " + limit);
                                                            callbackContext.error("Mapbox tile count limit exceeded: " + limit);
                                                        }
                                                    });
                                                }

                                                @Override
                                                public void onError(String error) {
                                                    Log.e(TAG, "Error: " + error);
                                                    makeToast("Error: " + error);
                                                    callbackContext.error("Error: " + error);
                                                }
                                            });
                                }

                            } catch (Exception e) {
                                callbackContext.error(e.getMessage());
                            }

                        }
                    });
                }
            } else if (ACTION_HIDE.equals(action)) {
                if (mapView != null) {

                    // Remove marker callback handler
                    this.markerCallbackContext = null;

                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ViewGroup vg = (ViewGroup) mapView.getParent();

                            if (vg != null) {
                                vg.removeView(mapView);
                            }

                            callbackContext.success();
                        }
                    });
                }

            } else if (ACTION_GET_CENTER.equals(action)) {
                if (mapView != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                    final CameraPosition center = mapboxMap.getCameraPosition();
                                    Map<String, Double> result = new HashMap<String, Double>();
                                    result.put("lat", center.target.getLatitude());
                                    result.put("lng", center.target.getLongitude());
                                    callbackContext.success(new JSONObject(result));
                                }
                            });

                        }
                    });
                }

            } else if (ACTION_SET_CENTER.equals(action)) {
                if (mapView != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            try {
                                final JSONObject options = args.getJSONObject(0);
                                final double lat = options.getDouble("lat");
                                final double lng = options.getDouble("lng");

                                mapView.getMapAsync(new OnMapReadyCallback() {
                                    @Override
                                    public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                        mapboxMap.setCameraPosition(new CameraPosition.Builder()
                                                .target(new LatLng(lat, lng))
                                                .build());
                                        callbackContext.success();
                                    }
                                });


                            } catch (JSONException e) {
                                callbackContext.error(e.getMessage());
                            }
                        }
                    });
                }

            } else if (ACTION_GET_ZOOMLEVEL.equals(action)) {
                if (mapView != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                    final double zoomLevel = mapboxMap.getCameraPosition().zoom;
                                    callbackContext.success("" + zoomLevel);
                                }
                            });
                        }
                    });
                }

            } else if (ACTION_SET_ZOOMLEVEL.equals(action)) {
                if (mapView != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            cordova.getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mapView.getMapAsync(new OnMapReadyCallback() {
                                        @Override
                                        public void onMapReady(@NonNull MapboxMap mapboxMap) {

                                            try {
                                                final JSONObject options = args.getJSONObject(0);
                                                final double zoom = options.getDouble("level");
                                                if (zoom >= 0 && zoom <= 20) {
                                                    mapboxMap.setCameraPosition(new CameraPosition.Builder()
                                                            .zoom(zoom)
                                                            .build());
                                                    callbackContext.success();
                                                } else {
                                                    callbackContext.error("invalid zoomlevel, use any double value from 0 to 20 (like 8.3)");
                                                }
                                            } catch (JSONException e) {
                                                callbackContext.error(e.getMessage());
                                            }
                                        }
                                    });
                                }
                            });


                        }
                    });
                }
//      } else if (ACTION_GET_BOUNDS.equals(action)) {
//        if (mapView != null) {
//          cordova.getActivity().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//
//                mapView.getMapAsync(new OnMapReadyCallback() {
//                    @Override
//                    public void onMapReady(@NonNull MapboxMap mapboxMap) {
//                        // NOTE: need to change to this on a future release
//                        // final CoordinateBounds bounds = mapView.getVisibleCoordinateBounds();
//                        // final LatLng sw = bounds.getSouthWest();
//                        // final LatLng ne = bounds.getNorthEast();
//
//                        // NOTE: Workaround, see: https://github.com/mapbox/mapbox-gl-native/issues/3863#issuecomment-181825298
//                        int viewportWidth = mapView.getWidth();
//                        int viewportHeight = mapView.getHeight();
//
//                        LatLng sw = mapView.fromScreenLocation(new PointF(0, viewportHeight)); // bottom left
//                        LatLng ne = mapView.fromScreenLocation(new PointF(viewportWidth, 0)); // top right
//
//                        Map<String, Double> result = new HashMap<String, Double>();
//                        result.put("sw_lat", sw.getLatitude());
//                        result.put("sw_lng", sw.getLongitude());
//                        result.put("ne_lat", ne.getLatitude());
//                        result.put("ne_lng", ne.getLongitude());
//                        callbackContext.success(new JSONObject(result));
//
//                    }
//                });
//
//
//            }
//          });
//        }

            } else if (ACTION_SET_BOUNDS.equals(action)) {
                if (mapView != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                    try {
                                        final JSONObject options = args.getJSONObject(0);
                                        final boolean animated = !options.isNull("animated") && options.getBoolean("animated");
                                        final double sw_lat = options.getDouble("sw_lat");
                                        final double sw_lng = options.getDouble("sw_lng");
                                        final double ne_lat = options.getDouble("ne_lat");
                                        final double ne_lng = options.getDouble("ne_lng");
                                        final LatLng sw = new LatLng(sw_lat, sw_lng);
                                        final LatLng ne = new LatLng(ne_lat, ne_lng);

                                        LatLngBounds latLngBounds = new LatLngBounds.Builder()
                                                .include(sw) // Northeast
                                                .include(ne) // Southwest
                                                .build();

                                        mapboxMap.easeCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 50), 5000);

                                        callbackContext.success();
                                    } catch (JSONException e) {
                                        callbackContext.error(e.getMessage());
                                    }

                                }
                            });


                        }
                    });
                }

            } else if (ACTION_GET_TILT.equals(action)) {
                if (mapView != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                    final double tilt = mapboxMap.getCameraPosition().tilt;
                                    callbackContext.success("" + tilt);
                                }
                            });

                        }
                    });
                }

            } else if (ACTION_SET_TILT.equals(action)) {
                if (mapView != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                    try {
                                        final JSONObject options = args.getJSONObject(0);
                                        mapboxMap.setCameraPosition(new CameraPosition.Builder()
                                                .tilt(options.optDouble("pitch", 20))
                                                .build());
                                        callbackContext.success();
                                    } catch (JSONException e) {
                                        callbackContext.error(e.getMessage());
                                    }
                                }
                            });


                        }
                    });
                }

            } else if (ACTION_ANIMATE_CAMERA.equals(action)) {
                if (mapView != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                    try {
                                        // TODO check mandatory elements
                                        final JSONObject options = args.getJSONObject(0);

                                        final JSONObject target = options.getJSONObject("target");
                                        final double lat = target.getDouble("lat");
                                        final double lng = target.getDouble("lng");

                                        final CameraPosition.Builder builder =
                                                new CameraPosition.Builder()
                                                        .target(new LatLng(lat, lng));

                                        if (options.has("bearing")) {
                                            builder.bearing(((Double) options.getDouble("bearing")).floatValue());
                                        }
                                        if (options.has("tilt")) {
                                            builder.tilt(((Double) options.getDouble("tilt")).floatValue());
                                        }
                                        if (options.has("zoomLevel")) {
                                            builder.zoom(((Double) options.getDouble("zoomLevel")).floatValue());
                                        }

                                        mapboxMap.animateCamera(
                                                CameraUpdateFactory.newCameraPosition(builder.build()),
                                                (options.optInt("duration", 15)) * 1000, // default 15 seconds
                                                null);

                                        callbackContext.success();
                                    } catch (JSONException e) {
                                        callbackContext.error(e.getMessage());
                                    }
                                }
                            });


                        }
                    });
                }

            } else if (ACTION_ADD_POLYGON.equals(action)) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mapView.getMapAsync(new OnMapReadyCallback() {
                            @Override
                            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                try {
                                    final PolygonOptions polygon = new PolygonOptions();
                                    final JSONObject options = args.getJSONObject(0);
                                    final JSONArray points = options.getJSONArray("points");
                                    for (int i = 0; i < points.length(); i++) {
                                        final JSONObject marker = points.getJSONObject(i);
                                        final double lat = marker.getDouble("lat");
                                        final double lng = marker.getDouble("lng");
                                        polygon.add(new LatLng(lat, lng));
                                    }
                                    mapboxMap.addPolygon(polygon);

                                    callbackContext.success();
                                } catch (JSONException e) {
                                    callbackContext.error(e.getMessage());
                                }
                            }
                        });


                    }
                });

            } else if (ACTION_ADD_GEOJSON.equals(action)) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // TODO implement
                        callbackContext.success();
                    }
                });

            } else if (ACTION_ADD_MARKERS.equals(action)) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            addMarkers(args.getJSONArray(0));
                            callbackContext.success();
                        } catch (JSONException e) {
                            callbackContext.error(e.getMessage());
                        }
                    }
                });

            } else if (ACTION_REMOVE_ALL_MARKERS.equals(action)) {
                if (mapView != null) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            mapView.getMapAsync(new OnMapReadyCallback() {
                                @Override
                                public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                    mapboxMap.removeAnnotations();
                                    callbackContext.success();
                                }
                            });


                        }
                    });
                }

            } else if (ACTION_ADD_MARKER_CALLBACK.equals(action)) {
                this.markerCallbackContext = callbackContext;
                mapView.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(@NonNull MapboxMap mapboxMap) {
                        mapboxMap.setOnInfoWindowClickListener(new MapboxMap.OnInfoWindowClickListener() {
                            @Override
                            public boolean onInfoWindowClick(@NonNull Marker marker) {
                                return false;
                            }
                        });
                    }
                });


//      } else if (ACTION_ON_REGION_WILL_CHANGE.equals(action)) {
//        if (mapView != null) {
//
//            mapView.getMapAsync(new OnMapReadyCallback() {
//                @Override
//                public void onMapReady(@NonNull MapboxMap mapboxMap) {
//                    mapboxMap.addOnCameraMoveListener(new MapView.OnCameraWillChangeListener(callbackContext));
//                    mapboxMap.addOnMapChangedListener(new RegionWillChangeListener(callbackContext));
//                }
//            });
//
//
//        }
//
//      } else if (ACTION_ON_REGION_IS_CHANGING.equals(action)) {
//        if (mapView != null) {
//          mapView.addOnMapChangedListener(new RegionIsChangingListener(callbackContext));
//        }
//
//      } else if (ACTION_ON_REGION_DID_CHANGE.equals(action)) {
//        if (mapView != null) {
//          mapView.addOnMapChangedListener(new RegionDidChangeListener(callbackContext));
//        }

            } else {
                return false;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            callbackContext.error(t.getMessage());
        }
        return true;
    }

    private void makeToast(String message){
        Toast.makeText(webView.getContext(),
                message,
                Toast.LENGTH_SHORT).show();
    }

    private void sendPluginResult(CallbackContext callbackContext,String message){
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, message);
        pluginResult.setKeepCallback(true); // keep callback
        callbackContext.sendPluginResult(pluginResult);
    }

    private void addMarkers(JSONArray markers) throws JSONException {
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                for (int i = 0; i < markers.length(); i++) {
                    final JSONObject marker;
                    try {
                        marker = markers.getJSONObject(i);
                        final MarkerOptions mo = new MarkerOptions();
                        mo.title(marker.isNull("title") ? null : marker.getString("title"));
                        mo.snippet(marker.isNull("subtitle") ? null : marker.getString("subtitle"));
                        mo.position(new LatLng(marker.getDouble("lat"), marker.getDouble("lng")));
                        mapboxMap.addMarker(mo);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            }
        });


    }

//  private class RegionWillChangeListener implements MapView.OnMapChangedListener {
//    private CallbackContext callback;
//
//    public RegionWillChangeListener(CallbackContext providedCallback) {
//      this.callback = providedCallback;
//    }
//
//    @Override
//    public void onMapChanged(int change) {
//      if ( change == MapView.REGION_WILL_CHANGE_ANIMATED ) {
//        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
//        pluginResult.setKeepCallback(true);
//        callback.sendPluginResult(pluginResult);
//      }
//    }
//  }
//
//  private class RegionIsChangingListener implements MapView.OnMapChangedListener {
//    private CallbackContext callback;
//
//    public RegionIsChangingListener(CallbackContext providedCallback) {
//      this.callback = providedCallback;
//    }
//
//    @Override
//    public void onMapChanged(int change) {
//      if ( change == MapView.REGION_IS_CHANGING ) {
//        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
//        pluginResult.setKeepCallback(true);
//        callback.sendPluginResult(pluginResult);
//      }
//    }
//  }
//
//  private class RegionDidChangeListener implements MapView.OnMapChangedListener {
//    private CallbackContext callback;
//
//    public RegionDidChangeListener(CallbackContext providedCallback) {
//      this.callback = providedCallback;
//    }
//
//    @Override
//    public void onMapChanged(int change) {
//      if ( change == MapView.REGION_DID_CHANGE_ANIMATED ) {
//        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
//        pluginResult.setKeepCallback(true);
//        callback.sendPluginResult(pluginResult);
//      }
//    }
//  }

//  private class MarkerClickListener implements MapView.OnInfoWindowClickListener {
//
//    @Override
//    public boolean onMarkerClick(@NonNull Marker marker) {
//      // callback
//      if (markerCallbackContext != null) {
//        final JSONObject json = new JSONObject();
//        try {
//          json.put("title", marker.getTitle());
//          json.put("subtitle", marker.getSnippet());
//          json.put("lat", marker.getPosition().getLatitude());
//          json.put("lng", marker.getPosition().getLongitude());
//        } catch (JSONException e) {
//          PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR,
//              "Error in callback of " + ACTION_ADD_MARKER_CALLBACK + ": " + e.getMessage());
//          pluginResult.setKeepCallback(true);
//          markerCallbackContext.sendPluginResult(pluginResult);
//        }
//        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, json);
//        pluginResult.setKeepCallback(true);
//        markerCallbackContext.sendPluginResult(pluginResult);
//        return true;
//      }
//      return false;
//    }
//  }

    private static int applyRetinaFactor(int i) {
        return (int) (i * retinaFactor);
    }

    private static String getStyle(final String requested) {
        if ("light".equalsIgnoreCase(requested)) {
            return Style.LIGHT;
        } else if ("dark".equalsIgnoreCase(requested)) {
            return Style.DARK;
        } else if ("outdoors".equalsIgnoreCase(requested)) {
            return Style.OUTDOORS;
        } else if ("satellite".equalsIgnoreCase(requested)) {
            return Style.SATELLITE;
            // TODO not currently supported on Android
//    } else if ("hybrid".equalsIgnoreCase(requested)) {
//      return Style.HYBRID;
        } else if ("streets".equalsIgnoreCase(requested)) {
            return Style.MAPBOX_STREETS;
        } else {
            return requested;
        }
    }

    private boolean permissionGranted(String... types) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        for (final String type : types) {
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this.cordova.getActivity(), type)) {
                return false;
            }
        }
        return true;
    }

    protected void showUserLocation(@NonNull MapboxMap mapboxMap) {
        if (permissionGranted(COARSE_LOCATION, FINE_LOCATION)) {
            //noinspection MissingPermission

            // Get an instance of the component
            LocationComponent locationComponent = mapboxMap.getLocationComponent();

// Set the LocationComponent activation options
            LocationComponentActivationOptions locationComponentActivationOptions =
                    LocationComponentActivationOptions.builder(webView.getContext(), mapboxMap.getStyle())
                            .useDefaultLocationEngine(false)
                            .build();

// Activate with the LocationComponentActivationOptions object
            locationComponent.activateLocationComponent(locationComponentActivationOptions);

// Enable to make component visible
            if (ActivityCompat.checkSelfPermission(webView.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(webView.getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            locationComponent.setLocationComponentEnabled(true);

// Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);

// Set the component's render mode
            locationComponent.setRenderMode(RenderMode.COMPASS);


    } else {
      requestPermission(COARSE_LOCATION, FINE_LOCATION);
    }
  }


  private void requestPermission(String... types) {
    ActivityCompat.requestPermissions(
        this.cordova.getActivity(),
        types,
        LOCATION_REQ_CODE);
  }

  // TODO
  public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
    for (int r : grantResults) {
      if (r == PackageManager.PERMISSION_DENIED) {
        this.callback.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
        return;
      }
    }
    switch (requestCode) {
      case LOCATION_REQ_CODE:
          mapView.getMapAsync(new OnMapReadyCallback() {
                                  @Override
                                  public void onMapReady(@NonNull MapboxMap mapboxMap) {
                                      showUserLocation(mapboxMap);
                                  }
                              });

        break;
    }
  }

  public void onPause(boolean multitasking) {
    if (mapView != null) {
      mapView.onPause();
    }
  }

  public void onResume(boolean multitasking) {
    if (mapView != null) {
      mapView.onResume();
    }
  }

  public void onDestroy() {
    if (mapView != null) {
      mapView.onDestroy();
    }
  }
}
