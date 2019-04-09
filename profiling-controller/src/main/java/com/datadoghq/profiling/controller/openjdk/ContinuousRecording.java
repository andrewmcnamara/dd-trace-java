/*
 * Copyright 2019 Datadog
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datadoghq.profiling.controller.openjdk;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import com.datadoghq.profiling.controller.RecordingData;

import jdk.jfr.Recording;
import jdk.jfr.RecordingState;

/**
 * Implementation for a continuous recording.
 */
public class ContinuousRecording implements RecordingData {
	private final Recording recording;
	private final Instant defaultStart;
	private final Instant defaultEnd;

	public ContinuousRecording(Recording recording, Instant start, Instant end) {
		this.recording = recording;
		this.defaultStart = start;
		this.defaultEnd = end;
	}

	public ContinuousRecording(Recording recording) {
		this(recording, null, null);
	}

	@Override
	public boolean isAvailable() {
		return recording.getState() == RecordingState.STOPPED;
	}

	@Override
	public InputStream getStream() throws IllegalStateException, IOException {
		return recording.getStream(defaultStart, defaultEnd);
	}

	public InputStream getStream(Instant start, Instant end) throws IOException {
		return recording.getStream(start, end);
	}

	@Override
	public void release() {
		recording.close();
	}

	public String getName() {
		return recording.getName();
	}

	@Override
	public String toString() {
		return "ContinuousRecording: " + getName();
	}

	@Override
	public Instant getRequestedStart() {
		return defaultStart;
	}

	@Override
	public Instant getRequestedEnd() {
		return defaultEnd;
	}

}
