package no.nordicsemi.android.blinky;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

public class BlinkyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("CurrentActivity","BlinkyApplication");
        //Added to support vector drawables for devices below android 21
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        }
    }
}
