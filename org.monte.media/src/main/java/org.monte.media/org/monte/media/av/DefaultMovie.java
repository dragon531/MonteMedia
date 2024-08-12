/*
 * @(#)DefaultMovie.java
 * Copyright © 2023 Werner Randelshofer, Switzerland. MIT License.
 */
package org.monte.media.av;

import org.monte.media.math.Rational;

import java.io.IOException;

/**
 * {@code DefaultMovie}.
 *
 * @author Werner Randelshofer
 */
public class DefaultMovie extends AbstractMovie {
    private final static long serialVersionUID = 1L;

    private MovieReader reader;

    @Override
    public MovieReader getReader() {
        return reader;
    }

    public void setReader(MovieReader reader) {
        this.reader = reader;
    }

    @Override
    public Rational getDuration() {
        try {
            return reader.getDuration();
        } catch (IOException ex) {
            InternalError ie = new InternalError("Can't read duration.");
            ie.initCause(ex);
            throw ie;
        }
    }

    @Override
    public long timeToSample(int track, Rational time) {
        try {
            return reader.timeToSample(track, time);
        } catch (IOException ex) {
            return 0;
        }
    }

    @Override
    public Rational sampleToTime(int track, long sample) {
        try {
            return reader.sampleToTime(track, sample);
        } catch (IOException ex) {
            return new Rational(0);
        }
    }

    @Override
    public int getTrackCount() {
        try {
            return reader.getTrackCount();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public Format getFormat(int track) {
        try {
            return reader.getFormat(track);
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public Format getFileFormat() {
        try {
            return reader.getFileFormat();
        } catch (IOException ex) {
            return null;
        }
    }
}
