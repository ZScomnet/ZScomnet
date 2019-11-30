/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.blinky;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.androdocs.httprequest.HttpRequest;

import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProviders;

import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import no.nordicsemi.android.blinky.adapter.DiscoveredBluetoothDevice;
import no.nordicsemi.android.blinky.viewmodels.BlinkyViewModel;

@SuppressWarnings("ConstantConditions")
public class BlinkyActivity extends AppCompatActivity {
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.blinky.EXTRA_DEVICE";
	private BlinkyViewModel mViewModel;
	@BindView(R.id.recv_data1) TextView mRecv_data1;
	@BindView(R.id.recv_data2) TextView mRecv_data2;
	@BindView(R.id.recv_data3) TextView mRecv_data3;

	String CITY = "Cheongju,KR";
	String API = "9f729412ebaecc538d11df13ee316e86";
	String SETTING = "";

	TextView mTemperature,mHumidity,mWeather_string,mLocation;
	ImageView mWeather;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_blinky);
		Log.d("CurrentActivity","BlinkyActivity");
		ButterKnife.bind(this);

		mTemperature = findViewById(R.id.Weather_temp);
		mHumidity = findViewById(R.id.Weather_humid);
		mWeather = findViewById(R.id.Weather_Image);
		mWeather_string = findViewById(R.id.Weather_string);
		mLocation = findViewById(R.id.Earth_Location);

		View.OnClickListener Listener_User = new View.OnClickListener(){
			@Override
			public void onClick(View view){
				SETTING = "1111";
				Toast.makeText(BlinkyActivity.this,"Send User Setting Data",Toast.LENGTH_SHORT).show();
			}
		};
		Button change_SETTING = (Button) findViewById(R.id.User_Setting);
		change_SETTING.setOnClickListener(Listener_User);

		View.OnClickListener Listener_Weather = new View.OnClickListener(){
			@Override
			public void onClick(View view){
				SETTING = "";
				Toast.makeText(BlinkyActivity.this,"Send Weather Setting Data",Toast.LENGTH_SHORT).show();
			}
		};
		Button Weather_SETTING = (Button) findViewById(R.id.Weather_Setting);
		Weather_SETTING.setOnClickListener(Listener_Weather);
		new weatherTask().execute();

		Timer Update_data = new Timer();
		final Intent intent = getIntent();
		final DiscoveredBluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
		final String deviceName = device.getName();
		final String deviceAddress = device.getAddress();

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setTitle(deviceName);
		getSupportActionBar().setSubtitle(deviceAddress);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		final Handler handler = new Handler(){
			public void handleMessage(Message msg){
				String DATA = mViewModel.update_data(true,SETTING);
				if(DATA.length() > 6) {
					mRecv_data1.setText(DATA.split("a")[0]);
					mRecv_data2.setText(DATA.split("a")[1]);
					mRecv_data3.setText(DATA.split("a")[2]);
				}
				mViewModel.reset();
				SETTING = "";
			}
		};
		TimerTask TT = new TimerTask() {
			@Override
			public void run() {
				Message msg = handler.obtainMessage();
				handler.sendMessage(msg);
			}
		};

		// Configure the view model
		mViewModel = ViewModelProviders.of(this).get(BlinkyViewModel.class);
		mViewModel.connect(device);

		// Set up views
		final LinearLayout progressContainer = findViewById(R.id.progress_container);
		final TextView connectionState = findViewById(R.id.connection_state);
		final View content = findViewById(R.id.device_container);
		final View notSupported = findViewById(R.id.not_supported);

		mViewModel.isDeviceReady().observe(this, deviceReady -> {
			progressContainer.setVisibility(View.GONE);
			content.setVisibility(View.VISIBLE);
		});

		Update_data.schedule(TT, 1000, 1000);

		mViewModel.getConnectionState().observe(this, text -> {
			if (text != null) {
				progressContainer.setVisibility(View.VISIBLE);
				notSupported.setVisibility(View.GONE);
				connectionState.setText(text);
			}
		});
		mViewModel.isSupported().observe(this, supported -> {
			if (!supported) {
				progressContainer.setVisibility(View.GONE);
				notSupported.setVisibility(View.VISIBLE);
			}
		});

		mViewModel.getByteMutableLiveData().observe(this, observer ->{
			if(observer != null){
				Log.d("StringObserver","yes");
			}
		});
	}

	@OnClick(R.id.action_clear_cache)
	public void onTryAgainClicked() {
		mViewModel.reconnect();
	}

	class weatherTask extends AsyncTask<String,Void,String> {
		@Override
		protected void onPreExecute(){
			super.onPreExecute();

		}
		protected String doInBackground(String... args){
			String response = HttpRequest.excuteGet("https://api.openweathermap.org/data/2.5/weather?q=" + CITY + "&units=metric&appid=" + API);
			return response;
		}
		protected void onPostExecute(String result){
			try{
				JSONObject jsonObj = new JSONObject(result);
				JSONObject main = jsonObj.getJSONObject("main");
				JSONObject sys = jsonObj.getJSONObject("sys");
				JSONObject wind = jsonObj.getJSONObject("wind");
				JSONObject weather = jsonObj.getJSONArray("weather").getJSONObject(0);

				Long updatedAt = jsonObj.getLong("dt");
				String updatedAtText = "Updated at: " + new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH).format(new Date(updatedAt * 1000));
				String temp = main.getString("temp") + "°C";
				String tempMin = "Min Temp: " + main.getString("temp_min") + "°C";
				String tempMax = "Max Temp: " + main.getString("temp_max") + "°C";
				String pressure = main.getString("pressure");
				String humidity = main.getString("humidity");

				Long sunrise = sys.getLong("sunrise");
				Long sunset = sys.getLong("sunset");
				String windSpeed = wind.getString("speed");
				String weatherDescription = weather.getString("description");

				String address = jsonObj.getString("name") + ", " + sys.getString("country");


				/* Populating extracted data into our views */
				mWeather_string.setText(weatherDescription.toUpperCase());
				mTemperature.setText(temp);
				mHumidity.setText(humidity);
				mLocation.setText(CITY);

				/* Views populated, Hiding the loader, Showing the main design */
				findViewById(R.id.loader).setVisibility(View.GONE);

			}catch (JSONException e) {
				findViewById(R.id.loader).setVisibility(View.GONE);
				findViewById(R.id.errorText).setVisibility(View.VISIBLE);
			}
		}
	}

}
