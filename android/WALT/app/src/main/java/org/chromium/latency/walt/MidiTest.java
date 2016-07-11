/*
 * Copyright (C) 2016 The Android Open Source Project
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

package org.chromium.latency.walt;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.os.Handler;

@TargetApi(23)
abstract class MidiTest extends Experiment {

    private Handler handler = new Handler();

    private static final String TEENSY_MIDI_NAME = "Teensyduino Teensy MIDI";

    private MidiManager mMidiManager;
    MidiDevice mMidiDevice;

    private boolean mConnecting = false;

    MidiTest(Context context) {
        super(context);
        mMidiManager = (MidiManager) context.getSystemService(Context.MIDI_SERVICE);
        findMidiDevice();
    }

    MidiTest(Context context, AutoRunFragment.ResultHandler resultHandler) {
        super(context, resultHandler);
    }

    @Override
    void run() {
        if (mMidiDevice == null) {
            if (mConnecting) {
                log("Still connecting...");
                final Experiment experiment = this;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        experiment.run();
                    }
                });
            } else {
                log("MIDI device is not open!");
            }
        }
    }

    private void findMidiDevice() {
        MidiDeviceInfo[] infos = mMidiManager.getDevices();
        for(MidiDeviceInfo info : infos) {
            String name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
            log("Found MIDI device named " + name);
            if(TEENSY_MIDI_NAME.equals(name)) {
                log("^^^ using this device ^^^");
                mConnecting = true;
                mMidiManager.openDevice(info, new MidiManager.OnDeviceOpenedListener() {
                    @Override
                    public void onDeviceOpened(MidiDevice device) {
                        if (device == null) {
                            log("Error, unable to open MIDI device");
                        } else {
                            log("Opened MIDI device successfully!");
                            mMidiDevice = device;
                        }
                        mConnecting = false;
                    }
                }, null);
                break;
            }
        }
    }
}
