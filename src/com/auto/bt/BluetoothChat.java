/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.auto.bt;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import com.auto.vsn.R;
import com.auto.writer.DataWriter;
import com.google.android.gms.maps.model.LatLng;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;


/**
 * This is the main Activity that displays the current chat session.
 * @param <ImageView>
 */
@SuppressLint("HandlerLeak")
public class BluetoothChat<ImageView> extends Activity {
    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    
    // Values to identify user's car engine fuel type
    private final int GASOLINE = 100;
    private final int HYBRID = 200;
    private final int ELECTRIC = 300;
    
    private int engineFuelType;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    
    // For data writing
    private DataWriter data = new DataWriter();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        setContentView(R.layout.activity_establish_bt);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
    }
    
    //keep track of current PID number
    int message_number = 1;
    
    boolean recordData = false;
    
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        //mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        //mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        /*mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message + '\r');
            }
        });*/

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
        
        sendMessage("01 51" + "\r");
        
    	final Button button = (Button) findViewById(R.id.record);
    	button.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {				
				recordData = !recordData;
				
				if(recordData)
					startTransmission();			
			}
    	
    	});
            
        /*
        //---Clear Trouble Codes Button---
        Button getCodesButton = (Button) findViewById(R.id.button2);
        getCodesButton.setOnClickListener(new View.OnClickListener() 
        {
    	
            public void onClick(View v) {
 
            	clearCodes();
            }
        });   
        */
       
    }
    
/*   
    public void startDataParsing() {
    	
    	Button button = (Button) findViewById(R.id.record);
    	button.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {				
				recordData = !recordData;
				
				if(recordData)
					startTransmission();			
			}
    	
    	});
    	
    }
 */
    
    public void startTransmission() {
    	
    	sendMessage("01 00" + '\r'); 
    
    }
    

	public void getData(int messagenumber) {
		
		if(this.engineFuelType == GASOLINE) {
	
			switch(messagenumber) {
		
				case 1: // get RPM
					sendMessage("01 0C" + "\r");
					messagenumber++;
					break;
				
				case 2: // get throttle
					sendMessage("01 11" + "\r");
					messagenumber++;
					break;
				
				case 3: // get Fuel Level
					sendMessage("01 2F" + "\r");
					messagenumber++;
					break;
		
				case 4: // get Speed (km/h)
					sendMessage("01 0D" + "\r");
					messagenumber++;
					break;
				
				case 5: // get MAF air flow rate
					sendMessage("01 10" + "\r");
					messagenumber++;
					break;
				
				case 6: // get distance travelled
					sendMessage("01 31" + "\r");
					messagenumber++;
					break;
				
				case 7: // get Coordinates
					fetchCoordinates();
					messagenumber++;
					break;
				
				default: ; 	
        	
			}
		} else if(this.engineFuelType == ELECTRIC) {
			// TODO do something
		} else if(this.engineFuelType == HYBRID) {
			// TODO do something
		}
    }
    
	// get Coordinates code
	
	private void fetchCoordinates() {
		
		LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE); 
		Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		this.data.setLng(location.getLongitude());
		this.data.setLat(location.getLatitude());
		
	}
	

	/*
    public void clearCodes() {
    	
    	final TextView TX = (TextView) findViewById(R.id.TXView2);
    	
        if(mConnectedDeviceName != null) {
        		
        	sendMessage("04" + '\r'); //send Clear Trouble Codes Command
        	TX.setText("Clear Codes");
        	Toast.makeText(getApplicationContext(), "OBD Trouble Codes Cleared", Toast.LENGTH_SHORT).show();
        
        }
        else {
        	Toast.makeText(getApplicationContext(), "OBD Adapter NOT CONNECTED", Toast.LENGTH_SHORT).show();
        }
        
    }
    */

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    /*private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }*/

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            //mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message); 
            }
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };

    private final void setStatus(int resId) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getActionBar();
        actionBar.setSubtitle(subTitle);
    }

 
    //Contains previous value of parameters
    int prev_dist = 0;
    
    //Instantaneous vehicle speed in km/h to caculate fuel economy in MPG (miles per gallon)
    int vss = 0;
	
    

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
    		

        @Override
        public void handleMessage(Message msg) {
        	
        	String dataRecieved;
        	int a = 0;
        	int b = 0;
        	int PID = 0;
        	
        	
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                    mConversationArrayAdapter.clear();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                    setStatus(R.string.title_connecting);
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                    setStatus(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
              
                //mConversationArrayAdapter.add("Me:  " + writeMessage);
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer               
                String readMessage = new String(readBuf, 0, msg.arg1);
                
                
                
                // ------- ADDED CODE FOR OBD -------- //      
                dataRecieved = readMessage;
                //RX.setText(dataRecieved);
                
                
                if((dataRecieved != null) && (dataRecieved.matches("\\s*[0-9A-Fa-f]{2} [0-9A-Fa-f]{2}\\s*\r?\n?" ))) {

	        			dataRecieved = dataRecieved.trim();
	        			String[] bytes = dataRecieved.split(" ");
	
	        			if((bytes[0] != null)&&(bytes[1] != null)) {
	        				
	        				PID = Integer.parseInt(bytes[0].trim(), 16);
	        				a = Integer.parseInt(bytes[1].trim(), 16); 
	        				}
	        			

	        		switch(PID) {
	            	
	        			case 12: //PID(0C): RPM
	        				
	        				int rpm_value = (a * 256) / 4; // formula for RPM from PID
	        				data.setRPM(rpm_value);
		            		break;
		            		
	        			case 13: //PID(0D): Speed (km/h)
	        				
	        				int speed_value = a; // formula for Speed from PID
	        				vss = speed_value;
	        				data.setSpeed(speed_value);
	        				break;
	        				
	        			case 16: //PID(10): MAF
	        				
	        				int maf = (a * 256); // formula for MAF in 100g/s
	        				
	        				/*
	        				 To calculate instantaneous fuel consumption in MPG (miles per gallon),
	        				 we use the formula:
	        				 
	        				 fuel economy (MPG) = (14.7 * 6.17 * 454 * 0.621371 * VSS) / (3600 * (MAF / 100))
	        									= (710.7 * VSS) / MAF
	        									  
	        				 in which: 14.7 = ideal air/fuel ratio for gasoline
	        						   6.17 = density of gasoline
	        				 		   454 = grams per pound conversion
	        				 		   0.621371 = miles per hour/kilometers per hour conversion
	        				 		   3600 = seconds per hour conversion
	        				 		   100 = modify MAF value into g/s (notice the maf I've pulled is in 100g/s)
	        						   VSS - vehicle speed in km/h
	        						   MAF - mass air flow rate in 100g/s
	        				*/
	        				
	        				double fuel_econ_val = (710.7 * vss) / maf; // formula for fuel economy in MPG (see above)
	        					   fuel_econ_val = 235.2 * fuel_econ_val; // converting MPG(US) into L/100km
	        				data.setFuelEcon(fuel_econ_val);
	        				break;
	        				
	        			case 17: //PID(11): Throttle Position
	        				
	        				int throttle_percentage = (a * 100) / 255; // formula for throttle position from PID
	        				data.setThrottle(throttle_percentage);
	        				break;
	        				
	        			case 47: //PID(2F): Fuel Level
	        				
	        				int remaining_fuel = (a * 100) / 255; // formula for fuel level from PID
	        				data.setFuelLevel(remaining_fuel);
	        				break;
	        				
	        			case 49: //PID(31): Distance Traveled after MIL cleared
	        				
	        				int distance = a * 256; // formula for distance traveled from PID
	        				
	        				if (prev_dist == 0) { // initial data pull, the car is stationary
	        					
	        					prev_dist = distance;
	        					data.setDistance(distance - prev_dist);
	        					
	        				} else { // the car is moving, distance = newDist - oldDist
	        					
	        					data.setDistance(distance - prev_dist);
	        					prev_dist = distance; // the newDist is now the oldDist for next data pulling
	        					
	        				}
	        				
	        				break;
	        				
	        			case 81: // PID(51): Engine Fuel Type
	        				
	        				if(a == 1) 
	        					engineFuelType = GASOLINE;
	        				
	        				if(a == 8) 
	        					engineFuelType = ELECTRIC;
	        				
	        				if((a == 17) || (a == 20)) 
	        					engineFuelType = HYBRID;
	        				
	        				break;	        				
		            			            		            		
		            	default: ;

	        		}

        	}
            else if((dataRecieved != null) && (dataRecieved.matches("\\s*[0-9A-Fa-f]{1,2} [0-9A-Fa-f]{2} [0-9A-Fa-f]{2}\\s*\r?\n?" ))) {
            	
    			dataRecieved = dataRecieved.trim();
    			String[] bytes = dataRecieved.split(" ");
    			
    			if((bytes[0] != null)&&(bytes[1] != null)&&(bytes[2] != null)) {
    				
    				PID = Integer.parseInt(bytes[0].trim(), 16);
    				a = Integer.parseInt(bytes[1].trim(), 16);
    				b = Integer.parseInt(bytes[2].trim(), 16);
    				
    			}
    			
    			//PID(0C): RPM
            	if(PID == 12) {
            		
            		int rpm_value = ((a * 256) + b) / 4;
            		data.setRPM(rpm_value);
            		
            	//PID(10): MAF
            	} else if(PID == 16) {
            		
            		int maf = ((a * 256) + b);
            		double fuel_econ_val = (710.7 * vss) / maf;
            			   fuel_econ_val = 235.2 * fuel_econ_val; // converting MPG(US) into L/100km
            		data.setFuelEcon(fuel_econ_val);
            	
            	//PID(31): Distance Traveled after MIL cleared
            	} else if(PID == 49) {
            		
            		int distance = (a * 256) + b;
            		
    				if (prev_dist == 0) {
    					
    					prev_dist = distance;
    					data.setDistance(distance - prev_dist);
    					
    				} else {
    					
    					data.setDistance(distance - prev_dist);
    					prev_dist = distance;
    					
    				}
            		
            	} else if((PID == 1)||(PID == 65)) {
            		
            		switch(a) {
	            	
        				case 13: //PID(0D): Speed (km/h)
        				
        					int speed_value = b;
        					vss = speed_value;
        					data.setSpeed(speed_value);
        					break;
        				
        				case 17: //PID(11): Throttle Position
        				
        					int throttle_percentage = (b * 100) / 255;
        					data.setThrottle(throttle_percentage);
        					break;
        				
        				case 47: //PID(2F): Fuel Level
        				
        					int remaining_fuel = (b * 100) / 255;
        					data.setFuelLevel(remaining_fuel);
        					break;
        					
        				case 81: // PID(51): Engine Fuel Type
	        				
	        				if(b == 1) 
	        					engineFuelType = GASOLINE;
	        				
	        				if(b == 8) 
	        					engineFuelType = ELECTRIC;
	        				
	        				if((b == 17) || (b == 20)) 
	        					engineFuelType = HYBRID;
	        				
	        				break;       					
			            		
			            default: ;
            		}
            	}
            	
            }
            /*
            else if((dataRecieved != null) && (dataRecieved.matches("\\s*[0-9]+(\\.[0-9]?)?V\\s*\r*\n*" ))) {
            	
            	dataRecieved = dataRecieved.trim();
            	String volt_number = dataRecieved.substring(0, dataRecieved.length()-1); 
            	double needle_value = Double.parseDouble(volt_number);
            	needle_value = (((needle_value - 11)*21) /0.5) - 100;
            	int volt_value = (int)(needle_value);
            	
	            	if(prev_voltage == 0) {
		            	RotateAnimation Voltage_animation = new RotateAnimation(-100, volt_value, 30, 97);
		            	Voltage_animation.setInterpolator(new LinearInterpolator());
		        	    Voltage_animation.setDuration(500);
		        	    Voltage_animation.setFillAfter(true);
		        	  //  ((View) pointer5).startAnimation(Voltage_animation); 
		            	prev_voltage = volt_value;
	            	}
	            	else {
	            		RotateAnimation Voltage_animation = new RotateAnimation(prev_voltage, volt_value, 30, 97);
		            	Voltage_animation.setInterpolator(new LinearInterpolator());
		        	    Voltage_animation.setDuration(500);
		        	    Voltage_animation.setFillAfter(true);
		        	 //   ((View) pointer5).startAnimation(Voltage_animation); 
		            	prev_voltage = volt_value;
	            	}
	            
            	//voltage.setText(dataRecieved);
            	
            } 
            else if((dataRecieved != null) && (dataRecieved.matches("\\s*[0-9]+(\\.[0-9]?)?V\\s*V\\s*>\\s*\r*\n*" ))) {
            	
            	dataRecieved = dataRecieved.trim();
            	String volt_number = dataRecieved.substring(0, dataRecieved.length()-1); 
            	double needle_value = Double.parseDouble(volt_number);
            	needle_value = (((needle_value - 11)*21) /0.5) - 100;
            	int volt_value = (int)(needle_value);
            	
	            	if(prev_voltage == 0) {
		            	RotateAnimation Voltage_animation = new RotateAnimation(-100, volt_value, 30, 97);
		            	Voltage_animation.setInterpolator(new LinearInterpolator());
		        	    Voltage_animation.setDuration(500);
		        	    Voltage_animation.setFillAfter(true);
		        	 //   ((View) pointer5).startAnimation(Voltage_animation); 
		            	prev_voltage = volt_value;
	            	}
	            	else {
	            		RotateAnimation Voltage_animation = new RotateAnimation(prev_voltage, volt_value, 30, 97);
		            	Voltage_animation.setInterpolator(new LinearInterpolator());
		        	    Voltage_animation.setDuration(500);
		        	    Voltage_animation.setFillAfter(true);
		        	//    ((View) pointer5).startAnimation(Voltage_animation); 
		            	prev_voltage = volt_value;
	            	} 
            	
            	//voltage.setText(dataRecieved);
            	
            }    
            */
            else if((dataRecieved != null) && (dataRecieved.matches("\\s*[ .A-Za-z0-9\\?*>\r\n]*\\s*>\\s*\r*\n*" ))) {
            	
            	if(message_number == 8) {
            		
            		data.setTime();
            		
            		try {
						data.write();
					} catch (IOException e) {
						e.printStackTrace();
					}
            		
            		message_number = 1;
            		
            	} else {
            		
            		getData(message_number++);
            		
            	}
            	
            }
            else { 
            	;
            }
                
 
                //mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }

		
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, true);
            }
            break;
        case REQUEST_CONNECT_DEVICE_INSECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, false);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_establish_bt, menu);
        return true;
    }
    
}

	