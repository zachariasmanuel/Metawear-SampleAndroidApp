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

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

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
import com.mbientlab.metawear.module.SensorFusion;
import com.mbientlab.metawear.module.SensorFusion.EulerAngle;
import com.mbientlab.metawear.module.SensorFusion.Quaternion;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by etsai on 11/23/16.
 * Edited by Zac on 03/22/17
 */

public class SensorFusionFragment extends SensorFragment {
    private static final String STREAM_KEY = "sensor_fusion_stream";
    private static final String STREAM_KEY_1 = "sensor_fusion_stream_1";
    private static final float SAMPLING_PERIOD = 1 / 100f;

    private final ArrayList<Entry> x0 = new ArrayList<>(), x1 = new ArrayList<>(), x2 = new ArrayList<>(), x3 = new ArrayList<>();
    private SensorFusion sensorFusion;
    private Spinner fusionModeSelection;
    private int srcIndex = 0;

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
    }

    @Override
    protected void setup() {
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

                                showLog("Quaternion w - " + quaternion.w() + " x - " + quaternion.w() + " y - " + quaternion.w() + " z - " + quaternion.w());

                                LineData data = chart.getData();

                                data.addXValue(String.format(Locale.US, "%.2f", sampleCount * SAMPLING_PERIOD));
                                data.addEntry(new Entry(quaternion.w(), sampleCount), 0);
                                data.addEntry(new Entry(quaternion.x(), sampleCount), 1);
                                data.addEntry(new Entry(quaternion.y(), sampleCount), 2);
                                data.addEntry(new Entry(quaternion.z(), sampleCount), 3);

                                sampleCount++;

                            }
                        });

                        sensorFusion.start(SensorFusion.DataOutput.QUATERNION);
                    }
                });

        sensorFusion.routeData().fromEulerAngles().stream(STREAM_KEY_1).commit()
                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                    @Override
                    public void success(RouteManager result) {
                        streamRouteManager = result;
                        result.subscribe(STREAM_KEY_1, new RouteManager.MessageHandler() {
                            @Override
                            public void process(Message message) {
                                final EulerAngle angles = message.getData(EulerAngle.class);

                                showLog("EulerAngle Heading - " + angles.heading() + " Pitch - " + angles.pitch() + " Roll - " + angles.roll() + " Yaw - " + angles.yaw());

                            }
                        });

                        sensorFusion.start(SensorFusion.DataOutput.EULER_ANGLES);
                    }
                });
    }


    @Override
    protected void clean() {
        sensorFusion.stop();
    }

    @Override
    protected String saveData() {
        final String CSV_HEADER = (srcIndex == 0 ? String.format("time,w,x,y,z%n") : String.format("time,heading,pitch,roll,yaw%n"));
        String filename = String.format(Locale.US, "%s_%tY%<tm%<td-%<tH%<tM%<tS%<tL.csv", getContext().getString(sensorResId), Calendar.getInstance());

        try {
            FileOutputStream fos = getActivity().openFileOutput(filename, Context.MODE_PRIVATE);
            fos.write(CSV_HEADER.getBytes());

            LineData data = chart.getLineData();
            LineDataSet x0DataSet = data.getDataSetByIndex(0), x1DataSet = data.getDataSetByIndex(1),
                    x2DataSet = data.getDataSetByIndex(2), x3DataSet = data.getDataSetByIndex(3);
            for (int i = 0; i < data.getXValCount(); i++) {
                fos.write(String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f,%.3f%n", (i * SAMPLING_PERIOD),
                        x0DataSet.getEntryForXIndex(i).getVal(), x1DataSet.getEntryForXIndex(i).getVal(),
                        x2DataSet.getEntryForXIndex(i).getVal(), x3DataSet.getEntryForXIndex(i).getVal()).getBytes());
            }
            fos.close();
            return filename;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void resetData(boolean clearData) {
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
