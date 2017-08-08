/* @(#)ANIMWriter.java
 * Copyright © 2011 Werner Randelshofer, Switzerland. 
 * You may only use this software in accordance with the license terms.
 */
package org.monte.media.amigamovie;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.monte.media.anim.ANIMMultiplexer;
import static org.monte.media.anim.AmigaVideoFormatKeys.ENCODING_ANIM_OP5;
import static org.monte.media.anim.AmigaVideoFormatKeys.toCAMG;
import org.monte.media.codec.Format;
import static org.monte.media.codec.FormatKeys.EncodingKey;
import static org.monte.media.codec.FormatKeys.MIME_ANIM;
import org.monte.media.codec.FormatKeys.MediaType;
import static org.monte.media.codec.FormatKeys.MediaTypeKey;
import static org.monte.media.codec.FormatKeys.MimeTypeKey;
import static org.monte.media.codec.video.VideoFormatKeys.DataClassKey;
import static org.monte.media.codec.video.VideoFormatKeys.FixedFrameRateKey;
import org.monte.media.math.Rational;
import org.monte.media.movie.MovieWriter;

/**
 * {@code ANIMWriter}.
 *
 * @author Werner Randelshofer
 * @version $Id: ANIMWriter.java 364 2016-11-09 19:54:25Z werner $
 */
public class ANIMWriter extends ANIMMultiplexer implements MovieWriter {

    public final static Format ANIM = new Format(MediaTypeKey, MediaType.FILE, MimeTypeKey, MIME_ANIM);

    @Override
    public Format getFileFormat() throws IOException {
        return ANIM;
    }

    @Override
    public Rational getDuration(int track) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private static class Track {

        Format format;
    }
    private ArrayList<Track> tracks = new ArrayList<Track>();

    public ANIMWriter(File file) throws IOException {
        super(file);
    }

    @Override
    public int addTrack(Format format) throws IOException {
        if (tracks.size() > 0) {
            throw new UnsupportedOperationException("only 1 track supported");
        }
        Format derivedFormat = format.prepend(
                MediaTypeKey, MediaType.VIDEO, MimeTypeKey, MIME_ANIM,
                EncodingKey, ENCODING_ANIM_OP5, DataClassKey, byte[].class,//
                FixedFrameRateKey, false);

        setCAMG(toCAMG(derivedFormat));
        Track tr = new Track();
        tr.format = derivedFormat;

        tracks.add(tr);
        return tracks.size() - 1;
    }

    @Override
    public Format getFormat(int track) {
        return tracks.get(track).format;
    }

    @Override
    public int getTrackCount() {
        return tracks.size();
    }

    public boolean isVFRSupported() {
        return true;
    }

    @Override
    public boolean isDataLimitReached() {
        return false;
    }

    @Override
    public boolean isEmpty(int track) {
        return inputTime.isZero();
    }

}
