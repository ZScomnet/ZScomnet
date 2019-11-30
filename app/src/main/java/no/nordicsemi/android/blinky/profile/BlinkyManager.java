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

package no.nordicsemi.android.blinky.profile;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.blinky.profile.callback.BlinkyTXDataCallback;
import no.nordicsemi.android.blinky.profile.callback.BlinkyRXDataCallback;
import no.nordicsemi.android.blinky.profile.data.BlinkyLED;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.LogSession;
import no.nordicsemi.android.log.Logger;

public class BlinkyManager extends BleManager<BlinkyManagerCallbacks> {
	/** Nordic Blinky Service UUID. */
	public final static UUID LBS_UUID_SERVICE = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
	/** BUTTON characteristic UUID. */
	private final static UUID LBS_UUID_TX_CHAR = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
	/** LED characteristic UUID. */
	private final static UUID LBS_UUID_RX_CHAR = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
	private String resultTx = "";
	private BluetoothGattCharacteristic mTXCharacteristic, mRXCharacteristic;
	private LogSession mLogSession;
	private boolean mSupported;
	private boolean mLedOn;
	private BluetoothGatt bluetoothGatt;

	public BlinkyManager(@NonNull final Context context) {
		super(context);
	}

	@NonNull
	@Override
	protected BleManagerGattCallback getGattCallback() {
		return mGattCallback;
	}

	/**
	 * Sets the log session to be used for low level logging.
	 * @param session the session, or null, if nRF Logger is not installed.
	 */
	public void setLogger(@Nullable final LogSession session) {
		this.mLogSession = session;
	}

	@Override
	public void log(final int priority, @NonNull final String message) {
		// The priority is a Log.X constant, while the Logger accepts it's log levels.
		Logger.log(mLogSession, LogContract.Log.Level.fromPriority(priority), message);
	}

	@Override
	protected boolean shouldClearCacheWhenDisconnected() {
		return !mSupported;
	}

	public String getResultTx() {
		return resultTx;
	}

	public void reset_ResultTx() { resultTx = ""; }

	private final BlinkyTXDataCallback mTXDataCallback = new BlinkyTXDataCallback() {

		@Override
		public void onReceiveData(@NonNull BluetoothDevice device, String data) {
			Log.d("dataReceive",data);
			if(data != "/n"){
				resultTx += data;
			}
			Log.d("resultTX",resultTx);
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			log(Log.WARN, "Invalid data received: " + data);
		}
	};

	/**
	 * The LED callback will be notified when the LED state was read or sent to the target device.
	 * <p>
	 * This callback implements both {@link no.nordicsemi.android.ble.callback.DataReceivedCallback}
	 * and {@link no.nordicsemi.android.ble.callback.DataSentCallback} and calls the same
	 * method on success.
	 * <p>
	 * If the data received were invalid, the
	 * {@link BlinkyRXDataCallback#onInvalidDataReceived(BluetoothDevice, Data)} will be
	 * called.
	 */
	private final BlinkyRXDataCallback mRXCallback = new BlinkyRXDataCallback() {
		@Override
		public void onLedStateChanged(@NonNull final BluetoothDevice device,
									  final boolean on) {
			mLedOn = on;
			log(LogContract.Log.Level.APPLICATION, "LED " + (on ? "ON" : "OFF"));
			mCallbacks.onLedStateChanged(device, on);
		}

		@Override
		public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
										  @NonNull final Data data) {
			// Data can only invalid if we read them. We assume the app always sends correct data.
			log(Log.WARN, "Invalid data received: " + data);
		}
	};

	/**
	 * BluetoothGatt callbacks object.
	 */
	private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {
		@Override
		protected void initialize() {
			setNotificationCallback(mTXCharacteristic).with(mTXDataCallback);
			readCharacteristic(mRXCharacteristic).with(mRXCallback).enqueue();
			readCharacteristic(mTXCharacteristic).with(mTXDataCallback).enqueue();
			enableNotifications(mTXCharacteristic).enqueue();
		}

		@Override
		public boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
			bluetoothGatt = gatt;
			final BluetoothGattService service = gatt.getService(LBS_UUID_SERVICE);
			if (service != null) {
				mTXCharacteristic = service.getCharacteristic(LBS_UUID_TX_CHAR);
				mRXCharacteristic = service.getCharacteristic(LBS_UUID_RX_CHAR);
			}

			boolean writeRequest = false;
			if (mRXCharacteristic != null) {
				final int rxProperties = mRXCharacteristic.getProperties();
				writeRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
			}
			mRXCharacteristic.setValue("Hi".getBytes());
			if(gatt.writeCharacteristic(mRXCharacteristic)){
				Log.d("Success","on");
			}
			mSupported = mTXCharacteristic != null && mRXCharacteristic != null && writeRequest;
			return mSupported;
		}

		@Override
		protected void onDeviceDisconnected() {
			mTXCharacteristic = null;
			mRXCharacteristic = null;
		}
	};

	/**
	 * Sends a request to the device to turn the LED on or off.
	 *
	 * @param on true to turn the LED on, false to turn it off.
	 */
	public void send(final boolean on,String SETTING) {
		// Are we connected?
		if (mRXCharacteristic == null)
			return;
		// No need to change?
		if (SETTING != "")
			mRXCharacteristic.setValue("3333".getBytes());
		else
			mRXCharacteristic.setValue("1".getBytes());
        bluetoothGatt.writeCharacteristic(mRXCharacteristic);
        Log.d("SendMessage","message");
	} // send 35로 바꿈

}