package com.android.contacts.common.location;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.util.List;

/**
 * Service used to perform asynchronous geocoding from within a broadcast receiver. Given a
 * {@link Location}, convert it into a country code, and save it in shared preferences.
 */
public class UpdateCountryService extends IntentService {
    private static final String TAG = UpdateCountryService.class.getSimpleName();

    private static final String ACTION_UPDATE_COUNTRY = "saveCountry";

    private static final String KEY_INTENT_LOCATION = "location";

    public UpdateCountryService() {
        super(TAG);
    }

    public static void updateCountry(Context context, Location location) {
        final Intent serviceIntent = new Intent(context, UpdateCountryService.class);
        serviceIntent.setAction(ACTION_UPDATE_COUNTRY);
        serviceIntent.putExtra(UpdateCountryService.KEY_INTENT_LOCATION, location);
        context.startService(serviceIntent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            Log.d(TAG, "onHandleIntent: could not handle null intent");
            return;
        }
        if (ACTION_UPDATE_COUNTRY.equals(intent.getAction())) {
            final Location location = (Location) intent.getParcelableExtra(KEY_INTENT_LOCATION);
            final String country = getCountryFromLocation(getApplicationContext(), location);

            if (country == null) {
                return;
            }

            final SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            final Editor editor = prefs.edit();
            editor.putLong(CountryDetector.KEY_PREFERENCE_TIME_UPDATED,
                    System.currentTimeMillis());
            editor.putString(CountryDetector.KEY_PREFERENCE_CURRENT_COUNTRY, country);
            editor.commit();
        }
    }

    /**
     * Given a {@link Location}, return a country code.
     *
     * @return the ISO 3166-1 two letter country code
     */
    private String getCountryFromLocation(Context context, Location location) {
        final Geocoder geocoder = new Geocoder(context);
        String country = null;
        try {
            final List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && addresses.size() > 0) {
                country = addresses.get(0).getCountryCode();
            }
        } catch (IOException e) {
            Log.w(TAG, "Exception occurred when getting geocoded country from location");
        }
        return country;
    }
}
