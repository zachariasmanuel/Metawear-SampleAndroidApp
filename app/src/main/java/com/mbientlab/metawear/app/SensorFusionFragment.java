/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.app.help.HelpOption;
import com.mbientlab.metawear.app.help.HelpOptionAdapter;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.SensorFusion;
import com.mbientlab.metawear.module.SensorFusion.EulerAngle;
import com.mbientlab.metawear.module.SensorFusion.Quaternion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by etsai on 11/23/16.
 * Edited by Zac on 03/22/17
 */

public class SensorFusionFragment extends SensorFragment {
    private static final String STREAM_KEY = "sensor_fusion_stream";
    private static final String STREAM_KEY_1 = "sensor_fusion_stream_1";
    private static final float SAMPLING_PERIOD = 1 / 100f;
    private int mNumPoints = 0;

    private final ArrayList<Entry> x0 = new ArrayList<>(), x1 = new ArrayList<>(), x2 = new ArrayList<>(), x3 = new ArrayList<>();
    private SensorFusion sensorFusion;
    private Spinner fusionModeSelection;
    private int srcIndex = 0;
    private List<String[]> dataSet = new ArrayList<>();
    private String dataRow[] = new String[9];

    public SensorFusionFragment() {
        super(R.string.navigation_fragment_sensor_fusion, R.layout.fragment_sensor_config_spinner, -1f, 1f);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ((TextView) view.findViewById(R.id.config_option_title)).setText(R.string.config_name_sensor_fusion_data);

        fusionModeSelection = (Spinner) view.findViewById(R.id.config_option_spinner);
        fusionModeSelection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                srcIndex = position;

                final YAxis leftAxis = chart.getAxisLeft();
                if (position == 0) {
                    leftAxis.setAxisMaxValue(1.f);
                    leftAxis.setAxisMinValue(-1.f);
                } else {
                    leftAxis.setAxisMaxValue(360f);
                    leftAxis.setAxisMinValue(-360f);
                }

                refreshChart(false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(getContext(), R.array.values_fusion_data, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fusionModeSelection.setAdapter(spinnerAdapter);
        askPermission();
    }

    private void askPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ((getActivity().checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) || getActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
    }

    @Override
    protected void setup() {


        dataSet.clear();
        showLog("Called setup");
        sensorFusion.configure()
                .setMode(SensorFusion.Mode.NDOF)
                .setAccRange(SensorFusion.AccRange.AR_16G)
                .setGyroRange(SensorFusion.GyroRange.GR_2000DPS)
                .commit();

        sensorFusion.routeData().fromQuaternions().stream(STREAM_KEY).commit()
                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                    @Override
                    public void success(RouteManager result) {
                        streamRouteManager = result;
                        result.subscribe(STREAM_KEY, new RouteManager.MessageHandler() {
                            @Override
                            public void process(Message message) {
                                final Quaternion quaternion = message.getData(Quaternion.class);

                               // mergeData(1, quaternion, null, 0);
                                //showLog("Quaternion w - " + quaternion.w() + " x - " + quaternion.w() + " y - " + quaternion.w() + " z - " + quaternion.w());

                                LineData data = chart.getData();

                                data.addXValue(String.format(Locale.US, "%.2f", sampleCount * SAMPLING_PERIOD));
                                data.addEntry(new Entry(quaternion.w(), sampleCount), 0);
                                data.addEntry(new Entry(quaternion.x(), sampleCount), 1);
                                data.addEntry(new Entry(quaternion.y(), sampleCount), 2);
                                data.addEntry(new Entry(quaternion.z(), sampleCount), 3);

                                sampleCount++;

                            }
                        });

                        //sensorFusion.start(SensorFusion.DataOutput.QUATERNION);
                    }
                });

        sensorFusion.routeData().fromEulerAngles().stream(STREAM_KEY).commit()
                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                    @Override
                    public void success(RouteManager result) {
                        streamRouteManager = result;
                        result.subscribe(STREAM_KEY, new RouteManager.MessageHandler() {
                            @Override
                            public void process(Message message) {
                                final EulerAngle angles = message.getData(EulerAngle.class);
                                message.getTimestamp();
                                mergeData(2, null, angles, message.getTimestamp().getTimeInMillis());
                                //showLog("EulerAngle Heading - " + angles.heading() + " Pitch - " + angles.pitch() + " Roll - " + angles.roll() + " Yaw - " + angles.yaw());
                            }
                        });
                        sensorFusion.start(SensorFusion.DataOutput.EULER_ANGLES);
                    }
                });


        sensorFusion.routeData().fromLinearAcceleration().stream(STREAM_KEY_1).commit()
                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                    @Override
                    public void success(RouteManager result) {
                        streamRouteManager = result;
                        result.subscribe(STREAM_KEY_1, new RouteManager.MessageHandler() {
                            @Override
                            public void process(Message message) {
                                //Accelerometer an = message.getData();

                                CartesianFloat cartesianFloat = message.getData(CartesianFloat.class);
                                //showLog("LinearAcceleration X - " + cartesianFloat.x() + " Y - " + cartesianFloat.y() + " Z - " + cartesianFloat.z());
                                //final EulerAngle angles = message.getData(. class);
                                //message.getTimestamp();
                                mergeData(1, cartesianFloat, null, message.getTimestamp().getTimeInMillis());
                                //showLog("EulerAngle Heading - " + angles.heading() + " Pitch - " + angles.pitch() + " Roll - " + angles.roll() + " Yaw - " + angles.yaw());
                            }
                        });
                        sensorFusion.start(SensorFusion.DataOutput.LINEAR_ACC);
                    }
                });
    }


    private void mergeData(int type, CartesianFloat cartesianFloat, EulerAngle angles, long timeStamp) {
        //showLog("Called merge data");

        if (type == 1 && mNumPoints == 0) {
            //showLog("Type 1");
            dataRow[0] = cartesianFloat.x() + "";
            dataRow[1] = cartesianFloat.y() + "";
            dataRow[2] = cartesianFloat.z() + "";
            mNumPoints += 3;
        } else if (type == 2 && mNumPoints == 3) {
            //showLog("Type 2");
            dataRow[3] = angles.heading() + "";
            dataRow[4] = angles.pitch() + "";
            dataRow[5] = angles.roll() + "";
            dataRow[6] = angles.yaw() + "";
            dataRow[7] = timeStamp + "";
            mNumPoints += 5;
        }

        if (mNumPoints >= 8) {
            mNumPoints = 0;
            dataSet.add(dataRow);
            showLog("Output  - LinearAcceleration x - " + dataRow[0] + " y - " + dataRow[1] + " z - " + dataRow[2] +  " Heading - " + dataRow[3] + " Pitch - " + dataRow[4] + " Roll - " + dataRow[5] + " Yaw - " + dataRow[6] + " Timestamp - " + dataRow[7]);
            dataRow = new String[8];
        }
    }


    @Override
    protected void clean() {
        showLog("Called clean");
        sensorFusion.stop();
    }

    @Override
    protected String saveData() {
        showLog("Called saveData");
        writeFile();
        return null;
    }

    public String writeFile() {
        String csvData;
        int count = 0;
        csvData = "linear_x,linear_y,linear_z,heading,pitch,roll,yaw,timestamp\n";
        for (String data[] : dataSet) {
            csvData = csvData + data[0] + "," + data[1] + "," + data[2] + "," + data[3] + "," + data[4] + "," + data[5] + "," + data[6] + "," + data[7]+ "\n";
            count++;
        }
        showLog(csvData);
        String filename = DateFormat.getDateTimeInstance().format(new Date()) + ".csv";
        writeToFile(csvData, filename);
        resetData(true);
        return count + "_" + filename;

    }

    public void writeToFile(String data, String name) {
        File path = new File(Environment.getExternalStorageDirectory().getPath() + "/SensorFusedData/");

        // Make sure the path directory exists.
        if (!path.exists()) {
            // Make it, if it doesn't exit
            path.mkdirs();
        }

        final File file = new File(path, name);

        // Save your stream, don't forget to flush() it before closing it.

        try {
            file.createNewFile();
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            myOutWriter.append(data);

            myOutWriter.close();

            fOut.flush();
            fOut.close();
            showLog("File wrote successfully - " + file.getAbsolutePath());
            Toast.makeText(getActivity(), "File wrote successfully - " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    @Override
    protected void resetData(boolean clearData) {
        dataSet.clear();
        showLog("Called resetData");
        if (clearData) {
            sampleCount = 0;
            chartXValues.clear();
            x0.clear();
            x1.clear();
            x2.clear();
            x3.clear();
        }

        ArrayList<LineDataSet> spinAxisData = new ArrayList<>();
        spinAxisData.add(new LineDataSet(x0, srcIndex == 0 ? "w" : "heading"));
        spinAxisData.get(0).setColor(Color.BLACK);
        spinAxisData.get(0).setDrawCircles(false);

        spinAxisData.add(new LineDataSet(x1, srcIndex == 0 ? "x" : "pitch"));
        spinAxisData.get(1).setColor(Color.RED);
        spinAxisData.get(1).setDrawCircles(false);

        spinAxisData.add(new LineDataSet(x2, srcIndex == 0 ? "y" : "roll"));
        spinAxisData.get(2).setColor(Color.GREEN);
        spinAxisData.get(2).setDrawCircles(false);

        spinAxisData.add(new LineDataSet(x3, srcIndex == 0 ? "z" : "yaw"));
        spinAxisData.get(3).setColor(Color.BLUE);
        spinAxisData.get(3).setDrawCircles(false);

        LineData data = new LineData(chartXValues);
        for (LineDataSet set : spinAxisData) {
            data.addDataSet(set);
        }
        data.setDrawValues(false);
        chart.setData(data);
    }

    @Override
    protected void boardReady() throws UnsupportedModuleException {
        sensorFusion = mwBoard.getModule(SensorFusion.class);
    }

    @Override
    protected void fillHelpOptionAdapter(HelpOptionAdapter adapter) {
        adapter.add(new HelpOption(R.string.config_name_sensor_fusion_data, R.string.config_desc_sensor_fusion_data));
    }


    private void showLog(String message) {
        Log.d("Ride", message);
    }
}
