/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.DataObject;
import com.google.android.apps.forscience.whistlepunk.DatabaseConnectionService;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AvailableSensors;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Class to get sensor data from the Magnetic sensor.
 */
public class MagneticStrengthSensor extends ScalarSensor {
    // For historical reasons, the ID is MagneticRotationSensor. Since this is not exposed to the
    // user, we will just not mind the inconsistency.
    public static final String ID = "MagneticRotationSensor";
    private SensorEventListener mSensorEventListener;

    public MagneticStrengthSensor() {
        super(ID);
    }

    // added: declare new variables.
    private Timer timer;
    private float dataValue;
    private double doubleValue;
    private int frequencyTime;
    private boolean firstTime = true;


    @Override
    protected SensorRecorder makeScalarControl(final StreamConsumer c,
            final SensorEnvironment environment, final Context context,
            final SensorStatusListener listener) {
        return new AbstractSensorRecorder() {
            @Override
            public void startObserving() {

                // retrieve the stored frequency value
                frequencyTime = ExperimentDetailsFragment.getTheStoredFrequency(ID);

                System.out.println("======================================");
                System.out.println("                  ");
                System.out.println("======================================");
                System.out.println(" ");
                System.out.println(" ");
                System.out.println("        Starting magnetic sensor");
                System.out.println("        FrequencyTime in milliseconds: " + frequencyTime);
                System.out.println(" ");
                System.out.println(" ");
                System.out.println("======================================");
                System.out.println("                  ");
                System.out.println("======================================");

                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
                SensorManager sensorManager = getSensorManager(context);
                Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                if (mSensorEventListener != null) {
                    getSensorManager(context).unregisterListener(mSensorEventListener);
                }
                final Clock clock = environment.getDefaultClock();

                // added: method to schedule data to be sent to database every 'frequency' seconds
                timer = new Timer();
                timer.schedule(new sendData(), 0, frequencyTime);

                mSensorEventListener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        // The strength is the square root of the sum of the squares of the
                        // values in X, Y and Z.
                        c.addData(clock.getNow(), Math.sqrt(Math.pow(event.values[0], 2) +
                                Math.pow(event.values[1], 2) + Math.pow(event.values[2], 2)));

                        // added: this is for the data collection for database
                        doubleValue = Math.sqrt(Math.pow(event.values[0], 2) +
                                Math.pow(event.values[1], 2) + Math.pow(event.values[2], 2));
                        // convert doubleValue to float
                        dataValue = (float)doubleValue;
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {

                    }
                };
                sensorManager.registerListener(mSensorEventListener, magnetometer,
                        SensorManager.SENSOR_DELAY_UI);
            }

            @Override
            public void stopObserving() {

                System.out.println("======================================");
                System.out.println("                  ");
                System.out.println("======================================");
                System.out.println(" ");
                System.out.println(" ");
                System.out.println("        Stopping magnetic sensor");
                System.out.println(" ");
                System.out.println(" ");
                System.out.println("======================================");
                System.out.println("                  ");
                System.out.println("======================================");

                // added: stop the timer task as the observing of the sensors is no longer needed
                timer.cancel();

                getSensorManager(context).unregisterListener(mSensorEventListener);
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
            }
        };
    }

    public static boolean isMagneticRotationSensorAvailable(AvailableSensors availableSensors) {
        return availableSensors.isSensorAvailable(Sensor.TYPE_MAGNETIC_FIELD);
    }

    // added: this class was added to sends the data to collection class that will then sent to database
    class sendData extends TimerTask {
        public void run() {

            // added: data object that will be sent to connection class to then go to the Database
            DataObject data = new DataObject(ID, dataValue);

            if (firstTime) {
                try {
                    Thread.sleep(250); // 250 millisecond delay to allow first collection of sensor data.
                    // as this sensor records movement
                    data.setDataValue(dataValue);
                    firstTime = false;
                } catch (InterruptedException ex) {}
            }

            // send the data to the DatabaseConnectionService
            DatabaseConnectionService.sendData(data);
            //======================================
            // connection to database
            //======================================
        }
    }
}