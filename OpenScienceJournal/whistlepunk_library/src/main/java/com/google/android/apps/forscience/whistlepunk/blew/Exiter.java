package com.google.android.apps.forscience.whistlepunk.blew;

import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.telecom.Call;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DatabaseConnectionService;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;

import org.eclipse.paho.client.mqttv3.MqttException;

public class Exiter extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        this.startForeground(1, new Notification());
        return null;
    }

       @Override
    public void onTaskRemoved(Intent rootIntent){

       try {
           DatabaseConnectionService.kill();
           BleSensorManager.getInstance().disconnect();
       } catch (Exception e) {
           e.printStackTrace();
       }
        AppSingleton.getInstance(this).getSensorRegistry().refreshBuiltinSensors(this);
        stopForeground(true);
        stopSelf();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
