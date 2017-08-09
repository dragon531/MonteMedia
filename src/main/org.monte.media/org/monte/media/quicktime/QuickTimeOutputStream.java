/* @(#)QuickTimeOutputStream.java
 * Copyright © 2017 Werner Randelshofer, Switzerland. Licensed under the MIT License. */
package org.monte.media.quicktime;

import java.awt.image.ColorModel;
import org.monte.media.av.Format;
import org.monte.media.io.ImageOutputStreamAdapter;
import org.monte.media.math.Rational;
import java.awt.image.IndexColorModel;
import java.io.*;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.stream.*;
import static java.lang.Math.*;
import static org.monte.media.av.codec.video.VideoFormatKeys.*;
import static org.monte.media.av.codec.audio.AudioFormatKeys.*;
import org.monte.media.io.IOStreams;

/**
 * This class provides low-level support for writing already encoded audio and
 * video samples into a QuickTime file.
 *
 * @author Werner Randelshofer
 * @version 1.0 2011-08-19 Created.
 */
public class QuickTimeOutputStream extends AbstractQuickTimeStream {

    /**
     * Creates a new instance.
     *
     * @param file the output file
     */
    public QuickTimeOutputStream(File file) throws IOException {
        if (file.exists()) {
            file.delete();
        }
        this.out = new FileImageOutputStream(file);
        this.streamOffset = 0;
        init();
    }

    /**
     * Creates a new instance.
     *
     * @param out the output stream.
     */
    public QuickTimeOutputStream(ImageOutputStream out) throws IOException {
        this.out = out;
        this.streamOffset = out.getStreamPosition();
        init();
    }

    private void init() {
        creationTime = new Date();
        modificationTime = new Date();
    }

    /**
     * Sets the time scale for this movie, that is, the number of time units
     * that pass per second in its time coordinate system. <p> The default value
     * is 600.
     *
     * @param timeScale
     */
    public void setMovieTimeScale(long timeScale) {
        if (timeScale < 1 || timeScale > (2L << 32)) {
            throw new IllegalArgumentException("timeScale must be between 1 and 2^32:" + timeScale);
        }
        this.movieTimeScale = timeScale;
    }

    /**
     * Returns the time scale of the movie.
     *
     * @return time scale
     * @see #setMovieTimeScale(long)
     */
    public long getMovieTimeScale() {
        return movieTimeScale;
    }

    /**
     * Returns the time scale of the media in a track.
     *
     * @param track Track index.
     * @return time scale
     * @see #setMovieTimeScale(long)
     */
    public long getMediaTimeScale(int track) {
        return tracks.get(track).mediaTimeScale;
    }

    /**
     * Returns the media duration of a track in the media's time scale.
     *
     * @param track Track index.
     * @return media duration
     */
    public long getMediaDuration(int track) {
        return tracks.get(track).mediaDuration;
    }

    /**
     * Returns the track duration in the movie's time scale without taking the
     * edit list into account. <p> The returned value is the media duration of
     * the track in the movies's time scale.
     *
     * @param track Track index.
     * @return unedited track duration
     */
    public long getUneditedTrackDuration(int track) {
        Track t = tracks.get(track);
        return t.mediaDuration * t.mediaTimeScale / movieTimeScale;
    }

    /**
     * Returns the track duration in the movie's time scale. <p> If the track
     * has an edit-list, the track duration is the sum of all edit durations.
     * <p> If the track does not have an edit-list, then this method returns the
     * media duration of the track in the movie's time scale.
     *
     * @param track Track index.
     * @return track duration
     */
    public long getTrackDuration(int track) {
        return tracks.get(track).getTrackDuration(movieTimeScale);
    }

    /**
     * Returns the total duration of the movie in the movie's time scale.
     *
     * @return media duration
     */
    public long getMovieDuration() {
        long duration = 0;
        for (Track t : tracks) {
            duration = Math.max(duration, t.getTrackDuration(movieTimeScale));
        }
        return duration;
    }

    /**
     * Sets the color table for videos with indexed color models.
     *
     * @param track The track number.
     * @param icm IndexColorModel. Specify null to use the standard Macintosh
     * color table.
     */
    public void setVideoColorTable(int track, ColorModel icm) {
        if (icm instanceof IndexColorModel) {
            VideoTrack t = (VideoTrack) tracks.get(track);
            t.videoColorTable = (IndexColorModel) icm;
        }
    }

    /**
     * Gets the preferred color table for displaying the movie on devices that
     * support only 256 colors.
     *
     * @param track The track number.
     * @return The color table or null, if the video uses the standard Macintosh
     * color table.
     */
    public IndexColorModel getVideoColorTable(int track) {
        VideoTrack t = (VideoTrack) tracks.get(track);
        return t.videoColorTable;
    }

    /**
     * Sets the edit list for the specified track. <p> In the absence of an edit
     * list, the presentation of the track starts immediately. An empty edit is
     * used to offset the start time of a track. <p>
     *
     * @throws IllegalArgumentException If the edit list ends with an empty
     * edit.
     */
    public void setEditList(int track, Edit[] editList) {
        if (editList != null && editList.length > 0 && editList[editList.length - 1].mediaTime == -1) {
            throw new IllegalArgumentException("Edit list must not end with empty edit.");
        }
        tracks.get(track).editList = editList;
    }

    /**
     * Adds a video track.
     *
     * @param compressionType The QuickTime "image compression format"
     * 4-Character code. A list of supported 4-Character codes is given in qtff,
     * table 3-1, page 96.
     * @param compressorName The QuickTime compressor name. Can be up to 32
     * characters long.
     * @param timeScale The media time scale between 1 and 2^32.
     * @param width The width of a video frame.
     * @param height The height of a video frame.
     * @param depth The number of bits per pixel.
     * @param syncInterval Interval for sync-samples. 0=automatic. 1=all frames
     * are keyframes. Values larger than 1 specify that for every n-th frame is
     * a keyframe. Apple's QuickTime will not work properly if there is not at
     * least one keyframe every second.
     *
     * @return Returns the track index.
     *
     * @throws IllegalArgumentException if {@code width} or {@code height} is
     * smaller than 1, if the length of {@code compressionType} is not equal to
     * 4, if the length of the {@code compressorName} is not between 1 and 32,
     * if the tiimeScale is not between 1 and 2^32.
     */
    public int addVideoTrack(String compressionType, String compressorName, long timeScale, int width, int height, int depth, int syncInterval) throws IOException {
        ensureStarted();
        if (compressionType == null || compressionType.length() != 4) {
            throw new IllegalArgumentException("compressionType must be 4 characters long:" + compressionType);
        }
        if (compressorName == null || compressorName.length() < 1 || compressorName.length() > 32) {
            throw new IllegalArgumentException("compressorName must be between 1 and 32 characters long:" + (compressorName == null ? "null" : "\"" + compressorName + "\""));
        }
        if (timeScale < 1 || timeScale > (2L << 32)) {
            throw new IllegalArgumentException("timeScale must be between 1 and 2^32:" + timeScale);
        }
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Width and height must be greater than 0, width:" + width + " height:" + height);
        }

        VideoTrack t = new VideoTrack();
        t.mediaCompressionType = compressionType;
        t.mediaCompressorName = compressorName;
        t.mediaTimeScale = timeScale;
        t.width = width;
        t.height = height;
        t.videoDepth = depth;
        t.syncInterval = syncInterval;
        t.format = new Format(
                MediaTypeKey, MediaType.VIDEO,
                MimeTypeKey, MIME_QUICKTIME,
                EncodingKey, compressionType,
                CompressorNameKey, compressorName,
                DataClassKey, byte[].class,
                WidthKey, width, HeightKey, height, DepthKey, depth,
                FrameRateKey, new Rational(timeScale, 1));
        tracks.add(t);
        return tracks.size() - 1;
    }

    /**
     * Adds an audio track.
     *
     * @param compressionType The QuickTime 4-character code. A list of
     * supported 4-Character codes is given in qtff, table 3-7, page 113.
     * @param timeScale The media time scale between 1 and 2^32.
     * @param sampleRate The sample rate. The integer portion must match the
     * {@code timeScale}.
     * @param numberOfChannels The number of channels: 1 for mono, 2 for stereo.
     * @param sampleSizeInBits The number of bits in a sample: 8 or 16.
     * @param isCompressed Whether the sound is compressed.
     * @param frameDuration The frame duration, expressed in the media’s
     * timescale, where the timescale is equal to the sample rate. For
     * uncompressed formats, this field is always 1.
     * @param frameSize For uncompressed audio, the number of bytes in a sample
     * for a single channel (sampleSize divided by 8). For compressed audio, the
     * number of bytes in a frame.
     *
     * @throws IllegalArgumentException if the audioFormat is not 4 characters
     * long, if the time scale is not between 1 and 2^32, if the integer portion
     * of the sampleRate is not equal to the timeScale, if numberOfChannels is
     * not 1 or 2.
     * @return Returns the track index.
     */
    public int addAudioTrack(String compressionType, //
            long timeScale, double sampleRate, //
            int numberOfChannels, int sampleSizeInBits, //
            boolean isCompressed, //
            int frameDuration, int frameSize, boolean signed, ByteOrder byteOrder) throws IOException {
        ensureStarted();
        if (compressionType == null || compressionType.length() != 4) {
            throw new IllegalArgumentException("audioFormat must be 4 characters long:" + compressionType);
        }
        if (timeScale < 1 || timeScale > (2L << 32)) {
            throw new IllegalArgumentException("timeScale must be between 1 and 2^32:" + timeScale);
        }
        if (timeScale != (int) Math.floor(sampleRate)) {
            throw new IllegalArgumentException("timeScale: " + timeScale + " must match integer portion of sampleRate: " + sampleRate);
        }
        if (numberOfChannels != 1 && numberOfChannels != 2) {
            throw new IllegalArgumentException("numberOfChannels must be 1 or 2: " + numberOfChannels);
        }
        if (sampleSizeInBits != 8 && sampleSizeInBits != 16) {
            throw new IllegalArgumentException("sampleSize must be 8 or 16: " + numberOfChannels);
        }

        AudioTrack t = new AudioTrack();
        t.mediaCompressionType = compressionType;
        t.mediaTimeScale = timeScale;
        t.soundSampleRate = sampleRate;
        t.soundCompressionId = isCompressed ? -2 : -1;
        t.soundNumberOfChannels = numberOfChannels;
        t.soundSampleSize = sampleSizeInBits;
        t.soundSamplesPerPacket = frameDuration;
        if (isCompressed) {
            t.soundBytesPerPacket = frameSize;
            t.soundBytesPerFrame = frameSize * numberOfChannels;
        } else {
            t.soundBytesPerPacket = frameSize / numberOfChannels;
            t.soundBytesPerFrame = frameSize;
        }
        t.soundBytesPerSample = sampleSizeInBits / 8;

        t.format = new Format(
                MediaTypeKey, MediaType.AUDIO,
                MimeTypeKey, MIME_QUICKTIME,
                EncodingKey, compressionType,
                SampleRateKey, Rational.valueOf(sampleRate),
                SampleSizeInBitsKey, sampleSizeInBits,
                ChannelsKey, numberOfChannels,
                FrameSizeKey, frameSize,
                SampleRateKey, Rational.valueOf(sampleRate),
                SignedKey, signed,
                ByteOrderKey, byteOrder);
        tracks.add(t);
        return tracks.size() - 1;
    }

    /**
     * Sets the compression quality of a track. <p> A value of 0 stands for
     * "high compression is important" a value of 1 for "high image quality is
     * important". <p> Changing this value affects the encoding of video frames
     * which are subsequently written into the track. Frames which have already
     * been written are not changed. <p> This value has no effect on videos
     * encoded with lossless encoders such as the PNG format. <p> The default
     * value is 0.97.
     *
     * @param newValue
     */
    public void setCompressionQuality(int track, float newValue) {
        VideoTrack vt = (VideoTrack) tracks.get(track);
        vt.videoQuality = newValue;
    }

    /**
     * Returns the compression quality of a track.
     *
     * @return compression quality
     */
    public float getCompressionQuality(int track) {
        return ((VideoTrack) tracks.get(track)).videoQuality;
    }

    /**
     * Sets the sync interval for the specified video track.
     *
     * @param track The track number.
     * @param i Interval between sync samples (keyframes). 0 = automatic. 1 =
     * write all samples as sync samples. n = sync every n-th sample.
     */
    public void setSyncInterval(int track, int i) {
        ((VideoTrack) tracks.get(track)).syncInterval = i;
    }

    /**
     * Gets the sync interval from the specified video track.
     */
    public int getSyncInterval(int track) {
        return ((VideoTrack) tracks.get(track)).syncInterval;
    }

    /**
     * Sets the creation time of the movie.
     */
    public void setCreationTime(Date creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * Gets the creation time of the movie.
     */
    public Date getCreationTime() {
        return creationTime;
    }

    /**
     * Sets the modification time of the movie.
     */
    public void setModificationTime(Date modificationTime) {
        this.modificationTime = modificationTime;
    }

    /**
     * Gets the modification time of the movie.
     */
    public Date getModificationTime() {
        return modificationTime;
    }

    /**
     * Gets the preferred rate at which to play this movie. A value of 1.0
     * indicates normal rate.
     */
    public double getPreferredRate() {
        return preferredRate;
    }

    /**
     * Sets the preferred rate at which to play this movie. A value of 1.0
     * indicates normal rate.
     */
    public void setPreferredRate(double preferredRate) {
        this.preferredRate = preferredRate;
    }

    /**
     * Gets the preferred volume of this movie’s sound. A value of 1.0 indicates
     * full volume.
     */
    public double getPreferredVolume() {
        return preferredVolume;
    }

    /**
     * Sets the preferred volume of this movie’s sound. A value of 1.0 indicates
     * full volume.
     */
    public void setPreferredVolume(double preferredVolume) {
        this.preferredVolume = preferredVolume;
    }

    /**
     * Gets the time value for current time position within the movie.
     */
    public long getCurrentTime() {
        return currentTime;
    }

    /**
     * Sets the time value for current time position within the movie.
     */
    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
    }

    /**
     * Gets the time value of the time of the movie poster.
     */
    public long getPosterTime() {
        return posterTime;
    }

    /**
     * Sets the time value of the time of the movie poster.
     */
    public void setPosterTime(long posterTime) {
        this.posterTime = posterTime;
    }

    /**
     * Gets the duration of the movie preview in movie time scale units.
     */
    public long getPreviewDuration() {
        return previewDuration;
    }

    /**
     * Gets the duration of the movie preview in movie time scale units.
     */
    public void setPreviewDuration(long previewDuration) {
        this.previewDuration = previewDuration;
    }

    /**
     * Gets the time value in the movie at which the preview begins.
     */
    public long getPreviewTime() {
        return previewTime;
    }

    /**
     * The time value in the movie at which the preview begins.
     */
    public void setPreviewTime(long previewTime) {
        this.previewTime = previewTime;
    }

    /**
     * The duration of the current selection in movie time scale units.
     */
    public long getSelectionDuration() {
        return selectionDuration;
    }

    /**
     * The duration of the current selection in movie time scale units.
     */
    public void setSelectionDuration(long selectionDuration) {
        this.selectionDuration = selectionDuration;
    }

    /**
     * The time value for the start time of the current selection.
     */
    public long getSelectionTime() {
        return selectionTime;
    }

    /**
     * The time value for the start time of the current selection.
     */
    public void setSelectionTime(long selectionTime) {
        this.selectionTime = selectionTime;
    }

    /**
     * Sets the transformation matrix of the entire movie.
     * <pre>
     * {a, b, u,
     *  c, d, v,
     *  tx,ty,w} // X- and Y-Translation
     *
     *           [ a  b  u
     * [x y 1] *   c  d  v   = [x' y' 1]
     * </pre> tx ty w ]
     *
     *
     * @param matrix The transformation matrix.
     */
    public void setMovieTransformationMatrix(double[] matrix) {
        if (matrix.length != 9) {
            throw new IllegalArgumentException("matrix must have 9 elements, matrix.length=" + matrix.length);
        }

        System.arraycopy(matrix, 0, movieMatrix, 0, 9);
    }

    /**
     * Gets the transformation matrix of the entire movie.
     *
     * @return The transformation matrix.
     */
    public double[] getMovieTransformationMatrix() {
        return movieMatrix.clone();
    }

    /**
     * Sets the transformation matrix of the specified track.
     * <pre>
     * {a, b, u,
     *  c, d, v,
     *  tx,ty,w} // X- and Y-Translation
     *
     *           [ a  b  u
     * [x y 1] *   c  d  v   = [x' y' 1]
     * </pre> tx ty w ]
     *
     *
     * @param track The track number.
     * @param matrix The transformation matrix.
     */
    public void setTransformationMatrix(int track, double[] matrix) {
        if (matrix.length != 9) {
            throw new IllegalArgumentException("matrix must have 9 elements, matrix.length=" + matrix.length);
        }

        System.arraycopy(matrix, 0, tracks.get(track).matrix, 0, 9);
    }

    /**
     * Gets the transformation matrix of the specified track.
     *
     * @param track The track number.
     * @return The transformation matrix.
     */
    public double[] getTransformationMatrix(int track) {
        return tracks.get(track).matrix.clone();
    }

    /**
     * Sets the state of the QuickTimeWriter to started. <p> If the state is
     * changed by this method, the prolog is written.
     */
    protected void ensureStarted() throws IOException {
        ensureOpen();
        if (state == States.FINISHED) {
            throw new IOException("Can not write into finished movie.");
        }
        if (state != States.STARTED) {
            writeProlog();
            mdatAtom = new WideDataAtom("mdat");
            state = States.STARTED;
        }
    }

    /**
     * Writes an already encoded sample from a file into a track. <p> This
     * method does not inspect the contents of the samples. The contents has to
     * match the format and dimensions of the media in this track.
     *
     * @param track The track index.
     * @param file The file which holds the encoded data sample.
     * @param duration The duration of the sample in media time scale units.
     * @param isSync whether the sample is a sync sample (key frame).
     *
     * @throws IOException if writing the sample data failed.
     */
    public void writeSample(int track, File file, long duration, boolean isSync) throws IOException {
        ensureStarted();
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            writeSample(track, in, duration, isSync);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Writes an already encoded sample from an input stream into a track. <p>
     * This method does not inspect the contents of the samples. The contents
     * has to match the format and dimensions of the media in this track.
     *
     * @param track The track index.
     * @param in The input stream which holds the encoded sample data.
     * @param duration The duration of the video frame in media time scale
     * units.
     * @param isSync Whether the sample is a sync sample (keyframe).
     *
     * @throws IllegalArgumentException if the duration is less than 1.
     * @throws IOException if writing the sample data failed.
     */
    public void writeSample(int track, InputStream in, long duration, boolean isSync) throws IOException {
        ensureStarted();
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be greater 0");
        }
        Track t = tracks.get(track); // throws index out of bounds exception if illegal track index
        ensureOpen();
        ensureStarted();
        long offset = getRelativeStreamPosition();
        OutputStream mdatOut = mdatAtom.getOutputStream();
        IOStreams.copy(in, mdatOut);
        long length = getRelativeStreamPosition() - offset;
        t.addSample(new Sample(duration, offset, length), 1, isSync);
    }

    /**
     * Writes an already encoded sample from a byte array into a track. <p> This
     * method does not inspect the contents of the samples. The contents has to
     * match the format and dimensions of the media in this track.
     *
     * @param track The track index.
     * @param data The encoded sample data.
     * @param duration The duration of the sample in media time scale units.
     * @param isSync Whether the sample is a sync sample.
     *
     * @throws IllegalArgumentException if the duration is less than 1.
     * @throws IOException if writing the sample data failed.
     */
    public void writeSample(int track, byte[] data, long duration, boolean isSync) throws IOException {
        writeSample(track, data, 0, data.length, duration, isSync);
    }

    /**
     * Writes an already encoded sample from a byte array into a track. <p> This
     * method does not inspect the contents of the samples. The contents has to
     * match the format and dimensions of the media in this track.
     *
     * @param track The track index.
     * @param data The encoded sample data.
     * @param off The start offset in the data.
     * @param len The number of bytes to write.
     * @param duration The duration of the sample in media time scale units.
     * @param isSync Whether the sample is a sync sample (keyframe).
     *
     * @throws IllegalArgumentException if the duration is less than 1.
     * @throws IOException if writing the sample data failed.
     */
    public void writeSample(int track, byte[] data, int off, int len, long duration, boolean isSync) throws IOException {
        ensureStarted();
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be greater 0");
        }
        Track t = tracks.get(track); // throws index out of bounds exception if illegal track index
        ensureOpen();
        ensureStarted();
        long offset = getRelativeStreamPosition();
        OutputStream mdatOut = mdatAtom.getOutputStream();
        mdatOut.write(data, off, len);
        t.addSample(new Sample(duration, offset, len), 1, isSync);
    }

    /**
     * Writes multiple sync samples from a byte array into a track. <p> This
     * method does not inspect the contents of the samples. The contents has to
     * match the format and dimensions of the media in this track.
     *
     * @param track The track index.
     * @param sampleCount The number of samples.
     * @param data The encoded sample data. The length of data must be dividable
     * by sampleCount.
     * @param sampleDuration The duration of a sample. All samples must have the
     * same duration.
     *
     * @throws IllegalArgumentException if {@code sampleDuration} is less than 1
     * or if the length of {@code data} is not dividable by {@code sampleCount}.
     * @throws IOException if writing the chunk failed.
     */
    public void writeSamples(int track, int sampleCount, byte[] data, long sampleDuration, boolean isSync) throws IOException {
        writeSamples(track, sampleCount, data, 0, data.length, sampleDuration, isSync);
    }

    /**
     * Writes multiple sync samples from a byte array into a track. <p> This
     * method does not inspect the contents of the samples. The contents has to
     * match the format and dimensions of the media in this track.
     *
     * @param track The track index.
     * @param sampleCount The number of samples.
     * @param data The encoded sample data.
     * @param off The start offset in the data.
     * @param len The number of bytes to write. Must be dividable by
     * sampleCount.
     * @param sampleDuration The duration of a sample. All samples must have the
     * same duration.
     *
     * @throws IllegalArgumentException if the duration is less than 1.
     * @throws IOException if writing the sample data failed.
     */
    public void writeSamples(int track, int sampleCount, byte[] data, int off, int len, long sampleDuration) throws IOException {
        writeSamples(track, sampleCount, data, off, len, sampleDuration, true);
    }

    /**
     * Writes multiple samples from a byte array into a track. <p> This method
     * does not inspect the contents of the data. The contents has to match the
     * format and dimensions of the media in this track.
     *
     * @param track The track index.
     * @param sampleCount The number of samples.
     * @param data The encoded sample data.
     * @param off The start offset in the data.
     * @param len The number of bytes to write. Must be dividable by
     * sampleCount.
     * @param sampleDuration The duration of a sample. All samples must have the
     * same duration.
     * @param isSync Whether the samples are sync samples. All samples must
     * either be sync samples or non-sync samples.
     *
     * @throws IllegalArgumentException if the duration is less than 1.
     * @throws IOException if writing the sample data failed.
     */
    public void writeSamples(int track, int sampleCount, byte[] data, int off, int len, long sampleDuration, boolean isSync) throws IOException {
        ensureStarted();
        if (sampleDuration <= 0) {
            throw new IllegalArgumentException("sampleDuration must be greater 0, sampleDuration=" + sampleDuration + " track=" + track);
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be greater 0, sampleCount=" + sampleCount + " track=" + track);
        }
        if (len % sampleCount != 0) {
            throw new IllegalArgumentException("len must be divisable by sampleCount len=" + len + " sampleCount=" + sampleCount + " track=" + track);
        }
        Track t = tracks.get(track); // throws index out of bounds exception if illegal track index
        ensureOpen();
        ensureStarted();
        long offset = getRelativeStreamPosition();
        OutputStream mdatOut = mdatAtom.getOutputStream();
        mdatOut.write(data, off, len);


        int sampleLength = len / sampleCount;
        Sample first = new Sample(sampleDuration, offset, sampleLength);
        Sample last = new Sample(sampleDuration, offset + sampleLength * (sampleCount - 1), sampleLength);
        t.addChunk(new Chunk(first, last, sampleCount, 1), isSync);
    }

    /**
     * Returns true if the limit for media samples has been reached. If this
     * limit is reached, no more samples should be added to the movie. <p>
     * QuickTime files can be up to 64 TB long, but there are other values that
     * may overflow before this size is reached. This method returns true when
     * the files size exceeds 2^60 or when the media duration value of a track
     * exceeds 2^61.
     */
    public boolean isDataLimitReached() {
        try {
            long maxMediaDuration = 0;
            for (Track t : tracks) {
                maxMediaDuration = max(t.mediaDuration, maxMediaDuration);
            }

            return getRelativeStreamPosition() > (1L << 61) //
                    || maxMediaDuration > 1L << 61;
        } catch (IOException ex) {
            return true;
        }
    }

    /**
     * Closes the movie file as well as the stream being filtered.
     *
     * @exception IOException if an I/O error has occurred
     */
    public void close() throws IOException {
        try {
            if (state == States.STARTED) {
                finish();
            }
        } finally {
            if (state != States.CLOSED) {
                out.close();
                state = States.CLOSED;
            }
        }
    }

    /**
     * Finishes writing the contents of the QuickTime output stream without
     * closing the underlying stream. Use this method when applying multiple
     * filters in succession to the same output stream.
     *
     * @exception IllegalStateException if the dimension of the video track has
     * not been specified or determined yet.
     * @exception IOException if an I/O exception has occurred
     */
    public void finish() throws IOException {
        ensureOpen();
        if (state != States.FINISHED) {
            for (int i = 0, n = tracks.size(); i < n; i++) {
            }
            mdatAtom.finish();
            writeEpilog();
            state = States.FINISHED;
            /*
             for (int i = 0, n = tracks.size(); i < n; i++) {
             if (tracks.get(i) instanceof VideoTrack) {
             VideoTrack t = (VideoTrack) tracks.get(i);
             t.videoWidth = t.videoHeight = -1;
             }
             }*/
        }
    }

    /**
     * Check to make sure that this stream has not been closed
     */
    protected void ensureOpen() throws IOException {
        if (state == States.CLOSED) {
            throw new IOException("Stream closed");
        }
    }

    /**
     * Writes the stream prolog.
     */
    private void writeProlog() throws IOException {
        /* File type atom
         *
         typedef struct {
         magic brand;
         bcd4 versionYear;
         bcd2 versionMonth;
         bcd2 versionMinor;
         magic[4] compatibleBrands;
         } ftypAtom;
         */
        DataAtom ftypAtom = new DataAtom("ftyp");
        DataAtomOutputStream d = ftypAtom.getOutputStream();
        d.writeType("qt  "); // brand
        d.writeBCD4(2005); // versionYear
        d.writeBCD2(3); // versionMonth
        d.writeBCD2(0); // versionMinor
        d.writeType("qt  "); // compatibleBrands
        d.writeInt(0); // compatibleBrands (0 is used to denote no value)
        d.writeInt(0); // compatibleBrands (0 is used to denote no value)
        d.writeInt(0); // compatibleBrands (0 is used to denote no value)
        ftypAtom.finish();
    }

    private void writeEpilog() throws IOException {
        long duration = getMovieDuration();

        DataAtom leaf;

        /* Movie Atom ========= */
        moovAtom = new CompositeAtom("moov");

        /* Movie Header Atom -------------
         * The data contained in this atom defines characteristics of the entire
         * QuickTime movie, such as time scale and duration. It has an atom type
         * value of 'mvhd'.
         *
         * typedef struct {
         byte version;
         byte[3] flags;
         mactimestamp creationTime;
         mactimestamp modificationTime;
         int timeScale;
         int duration;
         fixed16d16 preferredRate;
         fixed8d8 preferredVolume;
         byte[10] reserved;
         fixed16d16 matrixA;
         fixed16d16 matrixB;
         fixed2d30 matrixU;
         fixed16d16 matrixC;
         fixed16d16 matrixD;
         fixed2d30 matrixV;
         fixed16d16 matrixX;
         fixed16d16 matrixY;
         fixed2d30 matrixW;
         int previewTime;
         int previewDuration;
         int posterTime;
         int selectionTime;
         int selectionDuration;
         int currentTime;
         int nextTrackId;
         } movieHeaderAtom;
         */
        leaf = new DataAtom("mvhd");
        moovAtom.add(leaf);
        DataAtomOutputStream d = leaf.getOutputStream();
        d.writeByte(0); // version
        // A 1-byte specification of the version of this movie header atom.

        d.writeByte(0); // flags[0]
        d.writeByte(0); // flags[1]
        d.writeByte(0); // flags[2]
        // Three bytes of space for future movie header flags.

        d.writeMacTimestamp(creationTime); // creationTime
        // A 32-bit integer that specifies the calendar date and time (in
        // seconds since midnight, January 1, 1904) when the movie atom was
        // created. It is strongly recommended that this value should be
        // specified using coordinated universal time (UTC).

        d.writeMacTimestamp(modificationTime); // modificationTime
        // A 32-bit integer that specifies the calendar date and time (in
        // seconds since midnight, January 1, 1904) when the movie atom was
        // changed. BooleanIt is strongly recommended that this value should be
        // specified using coordinated universal time (UTC).

        d.writeUInt(movieTimeScale); // timeScale
        // A time value that indicates the time scale for this movie—that is,
        // the number of time units that pass per second in its time coordinate
        // system. A time coordinate system that measures time in sixtieths of a
        // second, for example, has a time scale of 60.

        d.writeUInt(duration); // duration
        // A time value that indicates the duration of the movie in time scale
        // units. Note that this property is derived from the movie’s tracks.
        // The value of this field corresponds to the duration of the longest
        // track in the movie.

        d.writeFixed16D16(preferredRate); // preferredRate
        // A 32-bit fixed-point number that specifies the rate at which to play
        // this movie. A value of 1.0 indicates normal rate.

        d.writeFixed8D8(preferredVolume); // preferredVolume
        // A 16-bit fixed-point number that specifies how loud to play this
        // movie’s sound. A value of 1.0 indicates full volume.

        d.write(new byte[10]); // reserved;
        // Ten bytes reserved for use by Apple. Set to 0.

        d.writeFixed16D16(movieMatrix[0]); // matrix[0]
        d.writeFixed16D16(movieMatrix[1]); // matrix[1]
        d.writeFixed2D30(movieMatrix[2]); // matrix[2]
        d.writeFixed16D16(movieMatrix[3]); // matrix[3]
        d.writeFixed16D16(movieMatrix[4]); // matrix[4]
        d.writeFixed2D30(movieMatrix[5]); // matrix[5]
        d.writeFixed16D16(movieMatrix[6]); // matrix[6]
        d.writeFixed16D16(movieMatrix[7]); // matrix[7]
        d.writeFixed2D30(movieMatrix[8]); // matrix[8]

        // The matrix structure associated with this movie. A matrix shows how
        // to map points from one coordinate space into another. See “Matrices”
        // for a discussion of how display matrices are used in QuickTime:
        // http://developer.apple.com/documentation/QuickTime/QTFF/QTFFChap4/chapter_5_section_4.html#//apple_ref/doc/uid/TP40000939-CH206-18737

        d.writeUInt(previewTime); // previewTime
        // The time value in the movie at which the preview begins.

        d.writeUInt(previewDuration); // previewDuration
        // The duration of the movie preview in movie time scale units.

        d.writeUInt(posterTime); // posterTime
        // The time value of the time of the movie poster.

        d.writeUInt(selectionTime); // selectionTime
        // The time value for the start time of the current selection.

        d.writeUInt(selectionDuration); // selectionDuration
        // The duration of the current selection in movie time scale units.

        d.writeUInt(currentTime); // currentTime;
        // The time value for current time position within the movie.

        d.writeUInt(tracks.size() + 1); // nextTrackId
        // A 32-bit integer that indicates a value to use for the track ID
        // number of the next track added to this movie. Note that 0 is not a
        // valid track ID value.

        for (int i = 0, n = tracks.size(); i < n; i++) {
            /* Track Atom ======== */
            writeTrackAtoms(i, moovAtom, modificationTime);
        }
        //
        moovAtom.finish();
    }

    protected void writeTrackAtoms(int trackIndex, CompositeAtom moovAtom, Date modificationTime) throws IOException {
        Track t = tracks.get(trackIndex);

        DataAtom leaf;
        DataAtomOutputStream d;

        /* Track Atom ======== */
        CompositeAtom trakAtom = new CompositeAtom("trak");
        moovAtom.add(trakAtom);

        /* Track Header Atom -----------
         * The track header atom specifies the characteristics of a single track
         * within a movie. A track header atom contains a size field that
         * specifies the number of bytes and a type field that indicates the
         * format of the data (defined by the atom type 'tkhd').
         *
         typedef struct {
         byte version;
         byte flag0;
         byte flag1;
         byte set TrackHeaderFlags flag2;
         mactimestamp creationTime;
         mactimestamp modificationTime;
         int trackId;
         byte[4] reserved;
         int duration;
         byte[8] reserved;
         short layer;
         short alternateGroup;
         short volume;
         byte[2] reserved;
         int[9] matrix;
         int trackWidth;
         int trackHeight;
         } trackHeaderAtom;     */
        leaf = new DataAtom("tkhd");
        trakAtom.add(leaf);
        d = leaf.getOutputStream();
        d.write(0); // version
        // A 1-byte specification of the version of this track header.

        d.write(0); // flag[0]
        d.write(0); // flag[1]
        d.write(t.headerFlags); // flag[2]
        // Three bytes that are reserved for the track header flags. These flags
        // indicate how the track is used in the movie. The following flags are
        // valid (all flags are enabled when set to 1):
        //
        // Track enabled
        //      Indicates that the track is enabled. Flag value is 0x0001.
        // Track in movie
        //      Indicates that the track is used in the movie. Flag value is
        //      0x0002.
        // Track in preview
        //      Indicates that the track is used in the movie’s preview. Flag
        //      value is 0x0004.
        // Track in poster
        //      Indicates that the track is used in the movie’s poster. Flag
        //      value is 0x0008.

        d.writeMacTimestamp(creationTime); // creationTime
        // A 32-bit integer that indicates the calendar date and time (expressed
        // in seconds since midnight, January 1, 1904) when the track header was
        // created. It is strongly recommended that this value should be
        // specified using coordinated universal time (UTC).

        d.writeMacTimestamp(modificationTime); // modificationTime
        // A 32-bit integer that indicates the calendar date and time (expressed
        // in seconds since midnight, January 1, 1904) when the track header was
        // changed. It is strongly recommended that this value should be
        // specified using coordinated universal time (UTC).

        d.writeInt(trackIndex + 1); // trackId
        // A 32-bit integer that uniquely identifies the track. The value 0
        // cannot be used.

        d.writeInt(0); // reserved;
        // A 32-bit integer that is reserved for use by Apple. Set this field to 0.

        d.writeUInt(t.getTrackDuration(movieTimeScale)); // duration
        // A time value that indicates the duration of this track (in the
        // movie’s time coordinate system). Note that this property is derived
        // from the track’s edits. The value of this field is equal to the sum
        // of the durations of all of the track’s edits. If there is no edit
        // list, then the duration is the sum of the sample durations, converted
        // into the movie timescale.

        d.writeLong(0); // reserved
        // An 8-byte value that is reserved for use by Apple. Set this field to 0.

        d.writeShort(0); // layer;
        // A 16-bit integer that indicates this track’s spatial priority in its
        // movie. The QuickTime Movie Toolbox uses this value to determine how
        // tracks overlay one another. Tracks with lower layer values are
        // displayed in front of tracks with higher layer values.

        d.writeShort(0); // alternate group
        // A 16-bit integer that specifies a collection of movie tracks that
        // contain alternate data for one another. QuickTime chooses one track
        // from the group to be used when the movie is played. The choice may be
        // based on such considerations as playback quality, language, or the
        // capabilities of the computer.

        d.writeFixed8D8(t.mediaType == MediaType.AUDIO ? 1 : 0); // volume
        // A 16-bit fixed-point value that indicates how loudly this track’s
        // sound is to be played. A value of 1.0 indicates normal volume.

        d.writeShort(0); // reserved
        // A 16-bit integer that is reserved for use by Apple. Set this field to 0.

        double[] m = t.matrix;
        d.writeFixed16D16(m[0]); // matrix[0]
        d.writeFixed16D16(m[1]); // matrix[1]
        d.writeFixed2D30(m[2]); // matrix[2]
        d.writeFixed16D16(m[3]); // matrix[3]
        d.writeFixed16D16(m[4]); // matrix[4]
        d.writeFixed2D30(m[5]); // matrix[5]
        d.writeFixed16D16(m[6]); // matrix[6]
        d.writeFixed16D16(m[7]); // matrix[7]
        d.writeFixed2D30(m[8]); // matrix[8]
        // The matrix structure associated with this track.
        // See Figure 2-8 for an illustration of a matrix structure:
        // http://developer.apple.com/documentation/QuickTime/QTFF/QTFFChap2/chapter_3_section_3.html#//apple_ref/doc/uid/TP40000939-CH204-32967

        d.writeFixed16D16(t.mediaType == MediaType.VIDEO ? ((VideoTrack) t).width : 0); // width
        // A 32-bit fixed-point number that specifies the width of this track in pixels.

        d.writeFixed16D16(t.mediaType == MediaType.VIDEO ? ((VideoTrack) t).height : 0); // height
        // A 32-bit fixed-point number that indicates the height of this track in pixels.

        /* Edit Atom ========= */
        CompositeAtom edtsAtom = new CompositeAtom("edts");
        trakAtom.add(edtsAtom);

        /* Edit List atom ------- */
        /*
         typedef struct {
         byte version;
         byte[3] flags;
         int numberOfEntries;
         editListTable editListTable[numberOfEntries];
         } editListAtom;
            
         typedef struct {
         int trackDuration;
         int mediaTime;
         fixed16d16 mediaRate;
         } editListTable;
         */
        leaf = new DataAtom("elst");
        edtsAtom.add(leaf);
        d = leaf.getOutputStream();

        d.write(0); // version
        // One byte that specifies the version of this header atom.

        d.write(0); // flag[0]
        d.write(0); // flag[1]
        d.write(0); // flag[2]

        Edit[] elist = t.editList;
        if (elist == null || elist.length == 0) {
            d.writeUInt(1); // numberOfEntries
            d.writeUInt(t.getTrackDuration(movieTimeScale)); // trackDuration
            d.writeUInt(0); // mediaTime
            d.writeFixed16D16(1); // mediaRate
        } else {
            d.writeUInt(elist.length); // numberOfEntries
            for (int i = 0; i < elist.length; ++i) {
                d.writeUInt(elist[i].trackDuration); // trackDuration
                d.writeUInt(elist[i].mediaTime); // mediaTime
                d.writeUInt(elist[i].mediaRate); // mediaRate
            }
        }


        /* Media Atom ========= */
        CompositeAtom mdiaAtom = new CompositeAtom("mdia");
        trakAtom.add(mdiaAtom);

        /* Media Header atom -------
         typedef struct {
         byte version;
         byte[3] flags;
         mactimestamp creationTime;
         mactimestamp modificationTime;
         int timeScale;
         int duration;
         short language;
         short quality;
         } mediaHeaderAtom;*/
        leaf = new DataAtom("mdhd");
        mdiaAtom.add(leaf);
        d = leaf.getOutputStream();
        d.write(0); // version
        // One byte that specifies the version of this header atom.

        d.write(0); // flag[0]
        d.write(0); // flag[1]
        d.write(0); // flag[2]
        // Three bytes of space for media header flags. Set this field to 0.

        d.writeMacTimestamp(creationTime); // creationTime
        // A 32-bit integer that specifies (in seconds since midnight, January
        // 1, 1904) when the media atom was created. It is strongly recommended
        // that this value should be specified using coordinated universal time
        // (UTC).

        d.writeMacTimestamp(modificationTime); // modificationTime
        // A 32-bit integer that specifies (in seconds since midnight, January
        // 1, 1904) when the media atom was changed. It is strongly recommended
        // that this value should be specified using coordinated universal time
        // (UTC).

        d.writeUInt(t.mediaTimeScale); // timeScale
        // A time value that indicates the time scale for this media—that is,
        // the number of time units that pass per second in its time coordinate
        // system.

        d.writeUInt(t.mediaDuration); // duration
        // The duration of this media in units of its time scale.

        d.writeShort(0); // language;
        // A 16-bit integer that specifies the language code for this media.
        // See “Language Code Values” for valid language codes:
        // http://developer.apple.com/documentation/QuickTime/QTFF/QTFFChap4/chapter_5_section_2.html#//apple_ref/doc/uid/TP40000939-CH206-27005

        d.writeShort(0); // quality
        // A 16-bit integer that specifies the media’s playback quality—that is,
        // its suitability for playback in a given environment.

        /**
         * Media Handler Reference Atom -------
         */
        leaf = new DataAtom("hdlr");
        mdiaAtom.add(leaf);
        /*typedef struct {
         byte version;
         byte[3] flags;
         magic componentType;
         magic componentSubtype;
         magic componentManufacturer;
         int componentFlags;
         int componentFlagsMask;
         pstring componentName;
         } handlerReferenceAtom;
         */
        d = leaf.getOutputStream();
        d.write(0); // version
        // A 1-byte specification of the version of this handler information.

        d.write(0); // flag[0]
        d.write(0); // flag[1]
        d.write(0); // flag[2]
        // A 3-byte space for handler information flags. Set this field to 0.

        d.writeType("mhlr"); // componentType
        // A four-character code that identifies the type of the handler. Only
        // two values are valid for this field: 'mhlr' for media handlers and
        // 'dhlr' for data handlers.

        d.writeType(t.mediaType == MediaType.VIDEO ? "vide" : "soun"); // componentSubtype
        // A four-character code that identifies the type of the media handler
        // or data handler. For media handlers, this field defines the type of
        // data—for example, 'vide' for video data or 'soun' for sound data.
        //
        // For data handlers, this field defines the data reference type—for
        // example, a component subtype value of 'alis' identifies a file alias.

        if (t.mediaType == MediaType.AUDIO) {
            d.writeType("appl");
        } else {
            d.writeUInt(0);
        }
        // componentManufacturer
        // Reserved. Set to 0.

        d.writeUInt(t.mediaType == MediaType.AUDIO ? 268435456L : 0); // componentFlags
        // Reserved. Set to 0.

        d.writeUInt(t.mediaType == MediaType.AUDIO ? 65941 : 0); // componentFlagsMask
        // Reserved. Set to 0.

        d.writePString(t.mediaType == MediaType.AUDIO ? "Apple Sound Media Handler" : ""); // componentName (empty string)
        // A (counted) string that specifies the name of the component—that is,
        // the media handler used when this media was created. This field may
        // contain a zero-length (empty) string.

        /* Media Information atom ========= */
        writeMediaInformationAtoms(trackIndex, mdiaAtom);
    }

    protected void writeMediaInformationAtoms(int trackIndex, CompositeAtom mdiaAtom) throws IOException {
        Track t = tracks.get(trackIndex);
        DataAtom leaf;
        DataAtomOutputStream d;
        /* Media Information atom ========= */
        CompositeAtom minfAtom = new CompositeAtom("minf");
        mdiaAtom.add(minfAtom);

        /* Video or Audio media information atom -------- */
        switch (t.mediaType) {
            case VIDEO:
        writeVideoMediaInformationHeaderAtom(trackIndex,minfAtom);
                break;
            case AUDIO:
        writeSoundMediaInformationHeaderAtom(trackIndex,minfAtom);
                break;
            default:
                throw new UnsupportedOperationException("Media type "+t.mediaType+" not supported yet.");
        }


        /* Data Handler Reference Atom -------- */
        // The handler reference atom specifies the media handler component that
        // is to be used to interpret the media’s data. The handler reference
        // atom has an atom type value of 'hdlr'.
        leaf = new DataAtom("hdlr");
        minfAtom.add(leaf);
        /*typedef struct {
         byte version;
         byte[3] flags;
         magic componentType;
         magic componentSubtype;
         magic componentManufacturer;
         int componentFlags;
         int componentFlagsMask;
         pstring componentName;
         ubyte[] extraData;
         } handlerReferenceAtom;
         */
        d = leaf.getOutputStream();
        d.write(0); // version
        // A 1-byte specification of the version of this handler information.

        d.write(0); // flag[0]
        d.write(0); // flag[1]
        d.write(0); // flag[2]
        // A 3-byte space for handler information flags. Set this field to 0.

        d.writeType("dhlr"); // componentType
        // A four-character code that identifies the type of the handler. Only
        // two values are valid for this field: 'mhlr' for media handlers and
        // 'dhlr' for data handlers.

        d.writeType("alis"); // componentSubtype
        // A four-character code that identifies the type of the media handler
        // or data handler. For media handlers, this field defines the type of
        // data—for example, 'vide' for video data or 'soun' for sound data.
        // For data handlers, this field defines the data reference type—for
        // example, a component subtype value of 'alis' identifies a file alias.

        if (t.mediaType == MediaType.AUDIO) {
            d.writeType("appl");
        } else {
            d.writeUInt(0);
        }
        // componentManufacturer
        // Reserved. Set to 0.

        d.writeUInt(t.mediaType == MediaType.AUDIO ? 268435457L : 0); // componentFlags
        // Reserved. Set to 0.

        d.writeInt(t.mediaType == MediaType.AUDIO ? 65967 : 0); // componentFlagsMask
        // Reserved. Set to 0.

        d.writePString("Apple Alias Data Handler"); // componentName (empty string)
        // A (counted) string that specifies the name of the component—that is,
        // the media handler used when this media was created. This field may
        // contain a zero-length (empty) string.

        /* Data information atom ===== */
        CompositeAtom dinfAtom = new CompositeAtom("dinf");
        minfAtom.add(dinfAtom);

        /* Data reference atom ----- */
        // Data reference atoms contain tabular data that instructs the data
        // handler component how to access the media’s data.
        leaf = new DataAtom("dref");
        dinfAtom.add(leaf);
        /*typedef struct {
         ubyte version;
         ubyte[3] flags;
         int numberOfEntries;
         dataReferenceEntry dataReference[numberOfEntries];
         } dataReferenceAtom;
            
         set {
         dataRefSelfReference=1 // I am not shure if this is the correct value for this flag
         } drefEntryFlags;
            
         typedef struct {
         int size;
         magic type;
         byte version;
         ubyte flag1;
         ubyte flag2;
         ubyte set drefEntryFlags flag3;
         byte[size - 12] data;
         } dataReferenceEntry;
         */
        d = leaf.getOutputStream();
        d.write(0); // version
        // A 1-byte specification of the version of this data reference atom.

        d.write(0); // flag[0]
        d.write(0); // flag[1]
        d.write(0); // flag[2]
        // A 3-byte space for data reference flags. Set this field to 0.

        d.writeInt(1); // numberOfEntries
        // A 32-bit integer containing the count of data references that follow.

        d.writeInt(12); // dataReference.size
        // A 32-bit integer that specifies the number of bytes in the data
        // reference.

        d.writeType("alis"); // dataReference.type
        // A 32-bit integer that specifies the type of the data in the data
        // reference. Table 2-4 lists valid type values:
        // http://developer.apple.com/documentation/QuickTime/QTFF/QTFFChap2/chapter_3_section_4.html#//apple_ref/doc/uid/TP40000939-CH204-38840

        d.write(0); // dataReference.version
        // A 1-byte specification of the version of the data reference.

        d.write(0); // dataReference.flag1
        d.write(0); // dataReference.flag2
        d.write(0x1); // dataReference.flag3
        // A 3-byte space for data reference flags. There is one defined flag.
        //
        // Self reference
        //      This flag indicates that the media’s data is in the same file as
        //      the movie atom. On the Macintosh, and other file systems with
        //      multifork files, set this flag to 1 even if the data resides in
        //      a different fork from the movie atom. This flag’s value is
        //      0x0001.


        /* Sample Table atom ========= */
        writeSampleTableAtoms(trackIndex, minfAtom);
    }
        protected void writeVideoMediaInformationHeaderAtom(int trackIndex, CompositeAtom minfAtom) throws IOException {
            DataAtom leaf;
            DataAtomOutputStream d;

            /* Video media information atom -------- */
            leaf = new DataAtom("vmhd");
            minfAtom.add(leaf);
            /*typedef struct {
             byte version;
             byte flag1;
             byte flag2;
             byte set vmhdFlags flag3;
             short graphicsMode;
             ushort[3] opcolor;
             } videoMediaInformationHeaderAtom;*/
            d = leaf.getOutputStream();
            d.write(0); // version
            // One byte that specifies the version of this header atom.

            d.write(0); // flag[0]
            d.write(0); // flag[1]
            d.write(0x1); // flag[2]
            // Three bytes of space for media header flags.
            // This is a compatibility flag that allows QuickTime to distinguish
            // between movies created with QuickTime 1.0 and newer movies. You
            // should always set this flag to 1, unless you are creating a movie
            // intended for playback using version 1.0 of QuickTime. This flag’s
            // value is 0x0001.

            d.writeShort(0x40); // graphicsMode (0x40 = DitherCopy)
            // A 16-bit integer that specifies the transfer mode. The transfer mode
            // specifies which Boolean operation QuickDraw should perform when
            // drawing or transferring an image from one location to another.
            // See “Graphics Modes” for a list of graphics modes supported by
            // QuickTime:
            // http://developer.apple.com/documentation/QuickTime/QTFF/QTFFChap4/chapter_5_section_5.html#//apple_ref/doc/uid/TP40000939-CH206-18741

            d.writeUShort(0); // opcolor[0]
            d.writeUShort(0); // opcolor[1]
            d.writeUShort(0); // opcolor[2]
            // Three 16-bit values that specify the red, green, and blue colors for
            // the transfer mode operation indicated in the graphics mode field.
        }
        protected void writeSoundMediaInformationHeaderAtom(int trackIndex,CompositeAtom minfAtom) throws IOException {
            DataAtom leaf;
            DataAtomOutputStream d;

            /* Sound media information header atom -------- */
            leaf = new DataAtom("smhd");
            minfAtom.add(leaf);
            /*typedef struct {
             ubyte version;
             ubyte[3] flags;
             short balance;
             short reserved;
             } soundMediaInformationHeaderAtom;*/
            d = leaf.getOutputStream();
            d.write(0); // version
            // A 1-byte specification of the version of this sound media information header atom.

            d.write(0); // flag[0]
            d.write(0); // flag[1]
            d.write(0); // flag[2]
            // A 3-byte space for sound media information flags. Set this field to 0.

            d.writeFixed8D8(0); // balance
            // A 16-bit integer that specifies the sound balance of this
            // sound media. Sound balance is the setting that controls
            // the mix of sound between the two speakers of a computer.
            // This field is normally set to 0.
            // Balance values are represented as 16-bit, fixed-point
            // numbers that range from -1.0 to +1.0. The high-order 8
            // bits contain the integer portion of the value; the
            // low-order 8 bits contain the fractional part. Negative
            // values weight the balance toward the left speaker;
            // positive values emphasize the right channel. Setting the
            // balance to 0 corresponds to a neutral setting.

            d.writeUShort(0); // reserved
            // Reserved for use by Apple. Set this field to 0.

        }

    protected void writeSampleTableAtoms(int trackIndex, CompositeAtom minfAtom) throws IOException {
        Track t = tracks.get(trackIndex);
        DataAtom leaf;
        DataAtomOutputStream d;

        /* Sample Table atom ========= */
        CompositeAtom stblAtom = new CompositeAtom("stbl");
        minfAtom.add(stblAtom);

        /* Sample Description atom ------- */
        t.writeSampleDescriptionAtom(stblAtom);


        /* Time to Sample atom ---- */
        // Time-to-sample atoms store duration information for a media’s
        // samples, providing a mapping from a time in a media to the
        // corresponding data sample. The time-to-sample atom has an atom type
        // of 'stts'.
        leaf = new DataAtom("stts");
        stblAtom.add(leaf);
        /*
         typedef struct {
         byte version;
         byte[3] flags;
         int numberOfEntries;
         timeToSampleTable timeToSampleTable[numberOfEntries];
         } timeToSampleAtom;
            
         typedef struct {
         int sampleCount;
         int sampleDuration;
         } timeToSampleTable;
         */
        d = leaf.getOutputStream();
        d.write(0); // version
        // A 1-byte specification of the version of this time-to-sample atom.

        d.write(0); // flag[0]
        d.write(0); // flag[1]
        d.write(0); // flag[2]
        // A 3-byte space for time-to-sample flags. Set this field to 0.

        d.writeUInt(t.timeToSamples.size()); // numberOfEntries
        // A 32-bit integer containing the count of entries in the
        // time-to-sample table.

        for (TimeToSampleGroup tts : t.timeToSamples) {
            d.writeUInt(tts.getSampleCount()); // timeToSampleTable[0].sampleCount
            // A 32-bit integer that specifies the number of consecutive
            // samples that have the same duration.

            d.writeUInt(tts.getSampleDuration()); // timeToSampleTable[0].sampleDuration
            // A 32-bit integer that specifies the duration of each
            // sample.
        }
        /* sample to chunk atom -------- */
        // The sample-to-chunk atom contains a table that maps samples to chunks
        // in the media data stream. By examining the sample-to-chunk atom, you
        // can determine the chunk that contains a specific sample.
        leaf = new DataAtom("stsc");
        stblAtom.add(leaf);
        /*
         typedef struct {
         byte version;
         byte[3] flags;
         int numberOfEntries;
         sampleToChunkTable sampleToChunkTable[numberOfEntries];
         } sampleToChunkAtom;
            
         typedef struct {
         int firstChunk;
         int samplesPerChunk;
         int sampleDescription;
         } sampleToChunkTable;
         */
        d = leaf.getOutputStream();
        d.write(0); // version
        // A 1-byte specification of the version of this time-to-sample atom.

        d.write(0); // flag[0]
        d.write(0); // flag[1]
        d.write(0); // flag[2]
        // A 3-byte space for time-to-sample flags. Set this field to 0.

        int entryCount = 0;
        long previousSampleCount = -1;
        long previousSampleDescriptionId = -1;
        for (Chunk c : t.chunks) {
            if (c.sampleCount != previousSampleCount//
                    || c.sampleDescriptionId != previousSampleDescriptionId) {
                previousSampleCount = c.sampleCount;
                previousSampleDescriptionId = c.sampleDescriptionId;
                entryCount++;
            }
        }

        d.writeInt(entryCount); // number of entries
        // A 32-bit integer containing the count of entries in the sample-to-chunk table.

        int firstChunk = 1;
        previousSampleCount = -1;
        previousSampleDescriptionId = -1;
        for (Chunk c : t.chunks) {
            if (c.sampleCount != previousSampleCount//
                    || c.sampleDescriptionId != previousSampleDescriptionId) {
                previousSampleCount = c.sampleCount;
                previousSampleDescriptionId = c.sampleDescriptionId;

                d.writeUInt(firstChunk); // first chunk
                // The first chunk number using this table entry.

                d.writeUInt(c.sampleCount); // samples per chunk
                // The number of samples in each chunk.

                d.writeInt(c.sampleDescriptionId); // sample description

                // The identification number associated with the sample description for
                // the sample. For details on sample description atoms, see “Sample
                // Description Atoms.”:
                // http://developer.apple.com/documentation/QuickTime/QTFF/QTFFChap2/chapter_3_section_5.html#//apple_ref/doc/uid/TP40000939-CH204-25691
            }
            firstChunk++;
        }
        //
        /* sync sample atom -------- */
        if (t.syncSamples != null) {
            leaf = new DataAtom("stss");
            stblAtom.add(leaf);
            /*
             typedef struct {
             byte version;
             byte[3] flags;
             int numberOfEntries;
             syncSampleTable syncSampleTable[numberOfEntries];
             } syncSampleAtom;
                
             typedef struct {
             int number;
             } syncSampleTable;
             */
            d = leaf.getOutputStream();
            d.write(0); // version
            // A 1-byte specification of the version of this time-to-sample atom.

            d.write(0); // flag[0]
            d.write(0); // flag[1]
            d.write(0); // flag[2]
            // A 3-byte space for time-to-sample flags. Set this field to 0.

            d.writeUInt(t.syncSamples.size());
            // Number of entries
            //A 32-bit integer containing the count of entries in the sync sample table.

            for (Long number : t.syncSamples) {
                d.writeUInt(number);
                // Sync sample table A table of sample numbers; each sample
                // number corresponds to a key frame.
            }
        }


        /* sample size atom -------- */
        // The sample size atom contains the sample count and a table giving the
        // size of each sample. This allows the media data itself to be
        // unframed. The total number of samples in the media is always
        // indicated in the sample count. If the default size is indicated, then
        // no table follows.
        leaf = new DataAtom("stsz");
        stblAtom.add(leaf);
        /*
         typedef struct {
         byte version;
         byte[3] flags;
         int sampleSize;
         int numberOfEntries;
         sampleSizeTable sampleSizeTable[numberOfEntries];
         } sampleSizeAtom;
            
         typedef struct {
         int size;
         } sampleSizeTable;
         */
        d = leaf.getOutputStream();
        d.write(0); // version
        // A 1-byte specification of the version of this time-to-sample atom.

        d.write(0); // flag[0]
        d.write(0); // flag[1]
        d.write(0); // flag[2]
        // A 3-byte space for time-to-sample flags. Set this field to 0.

        int sampleUnit = t.mediaType == MediaType.AUDIO//
                && ((AudioTrack) t).soundCompressionId != -2 //
                ? ((AudioTrack) t).soundSampleSize / 8 * ((AudioTrack) t).soundNumberOfChannels//
                : 1;
        if (t.sampleSizes.size() == 1) {
            d.writeUInt(t.sampleSizes.get(0).getSampleLength() / sampleUnit); // sample size
            // A 32-bit integer specifying the sample size. If all the samples are
            // the same size, this field contains that size value. If this field is
            // set to 0, then the samples have different sizes, and those sizes are
            // stored in the sample size table.

            d.writeUInt(t.sampleSizes.get(0).getSampleCount()); // number of entries
            // A 32-bit integer containing the count of entries in the sample size
            // table.

        } else {
            d.writeUInt(0); // sample size
            // A 32-bit integer specifying the sample size. If all the samples are
            // the same size, this field contains that size value. If this field is
            // set to 0, then the samples have different sizes, and those sizes are
            // stored in the sample size table.


            long count = 0;
            for (SampleSizeGroup s : t.sampleSizes) {
                count += s.sampleCount;
            }
            d.writeUInt(count); // number of entries
            // A 32-bit integer containing the count of entries in the sample size
            // table.

            for (SampleSizeGroup s : t.sampleSizes) {
                long sampleSize = s.getSampleLength() / sampleUnit;
                for (int i = 0; i < s.sampleCount; i++) {
                    d.writeUInt(sampleSize); // sample size
                    // The size field contains the size, in bytes, of the sample in
                    // question. The table is indexed by sample number—the first entry
                    // corresponds to the first sample, the second entry is for the
                    // second sample, and so on.
                }
            }
        }
        //
        /* chunk offset atom -------- */
        // The chunk-offset table gives the index of each chunk into the
        // QuickTime Stream. There are two variants, permitting the use of
        // 32-bit or 64-bit offsets. The latter is useful when managing very
        // large movies. Only one of these variants occurs in any single
        // instance of a sample table atom.
        if (t.chunks.isEmpty() || t.chunks.get(t.chunks.size() - 1).getChunkOffset() <= 0xffffffffL) {
            /* 32-bit chunk offset atom -------- */
            leaf = new DataAtom("stco");
            stblAtom.add(leaf);
            /*
             typedef struct {
             byte version;
             byte[3] flags;
             int numberOfEntries;
             chunkOffsetTable chunkOffsetTable[numberOfEntries];
             } chunkOffsetAtom;
                
             typedef struct {
             int offset;
             } chunkOffsetTable;
             */
            d = leaf.getOutputStream();
            d.write(0); // version
            // A 1-byte specification of the version of this time-to-sample atom.

            d.write(0); // flag[0]
            d.write(0); // flag[1]
            d.write(0); // flag[2]
            // A 3-byte space for time-to-sample flags. Set this field to 0.

            d.writeUInt(t.chunks.size()); // number of entries
            // A 32-bit integer containing the count of entries in the chunk
            // offset table.
            for (Chunk c : t.chunks) {
                d.writeUInt(c.getChunkOffset() + mdatOffset); // offset
                // The offset contains the byte offset from the beginning of the
                // data stream to the chunk. The table is indexed by chunk
                // number—the first table entry corresponds to the first chunk,
                // the second table entry is for the second chunk, and so on.
            }
        } else {
            /* 64-bit chunk offset atom -------- */
            leaf = new DataAtom("co64");
            stblAtom.add(leaf);
            /*
             typedef struct {
             byte version;
             byte[3] flags;
             int numberOfEntries;
             chunkOffsetTable chunkOffset64Table[numberOfEntries];
             } chunkOffset64Atom;
                
             typedef struct {
             long offset;
             } chunkOffset64Table;
             */
            d = leaf.getOutputStream();
            d.write(0); // version
            // A 1-byte specification of the version of this time-to-sample atom.

            d.write(0); // flag[0]
            d.write(0); // flag[1]
            d.write(0); // flag[2]
            // A 3-byte space for time-to-sample flags. Set this field to 0.

            d.writeUInt(t.chunks.size()); // number of entries
            // A 32-bit integer containing the count of entries in the chunk
            // offset table.

            for (Chunk c : t.chunks) {
                d.writeLong(c.getChunkOffset()); // offset
                // The offset contains the byte offset from the beginning of the
                // data stream to the chunk. The table is indexed by chunk
                // number—the first table entry corresponds to the first chunk,
                // the second table entry is for the second chunk, and so on.
            }
        }
    }

    /**
     * Writes a version of the movie which is optimized for the web into the
     * specified output file. <p> This method finishes the movie and then copies
     * its content into the specified file. The web-optimized file starts with
     * the movie header.
     *
     * @param outputFile The output file
     * @param compressHeader Whether the movie header shall be compressed.
     */
    public void toWebOptimizedMovie(File outputFile, boolean compressHeader) throws IOException {
        finish();
        long originalMdatOffset = mdatAtom.getOffset();
        CompositeAtom originalMoovAtom = moovAtom;
        mdatOffset = 0;

        ImageOutputStream originalOut = out;
        try {
            out = null;

            if (compressHeader) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                int maxIteration = 5;
                long compressionHeadersSize = 40 + 8;
                long headerSize = 0;
                long freeSize = 0;
                while (true) {
                    mdatOffset = compressionHeadersSize + headerSize + freeSize;
                    buf.reset();
                    DeflaterOutputStream deflater = new DeflaterOutputStream(buf);
                    out = new MemoryCacheImageOutputStream(deflater);
                    writeEpilog();
                    out.close();
                    deflater.close();

                    if (buf.size() > headerSize + freeSize && --maxIteration > 0) {
                        if (headerSize != 0) {
                            freeSize = Math.max(freeSize, buf.size() - headerSize - freeSize);
                        }
                        headerSize = buf.size();
                    } else {
                        freeSize = headerSize + freeSize - buf.size();
                        headerSize = buf.size();
                        break;
                    }
                }

                if (maxIteration < 0 || buf.size() == 0) {
                    compressHeader = false;
                    System.err.println("WARNING QuickTimeWriter failed to compress header.");
                } else {
                    out = new FileImageOutputStream(outputFile);
                    writeProlog();

                    // 40 bytes compression headers
                    DataAtomOutputStream daos = new DataAtomOutputStream(new ImageOutputStreamAdapter(out));
                    daos.writeUInt(headerSize + 40);
                    daos.writeType("moov");

                    daos.writeUInt(headerSize + 32);
                    daos.writeType("cmov");

                    daos.writeUInt(12);
                    daos.writeType("dcom");
                    daos.writeType("zlib");

                    daos.writeUInt(headerSize + 12);
                    daos.writeType("cmvd");
                    daos.writeUInt(originalMoovAtom.size());

                    daos.write(buf.toByteArray());

                    // 8 bytes "free" atom + free data
                    daos.writeUInt(freeSize + 8);
                    daos.writeType("free");
                    for (int i = 0; i < freeSize; i++) {
                        daos.write(0);
                    }
                }

            }
            if (!compressHeader) {
                out = new FileImageOutputStream(outputFile);
                mdatOffset = moovAtom.size();
                writeProlog();
                writeEpilog();
            }


            byte[] buf = new byte[4096];
            originalOut.seek((originalMdatOffset));
            for (long count = 0, n = mdatAtom.size(); count < n;) {
                int read = originalOut.read(buf, 0, (int) Math.min(buf.length, n - count));
                out.write(buf, 0, read);
                count += read;
            }
            out.close();
        } finally {
            mdatOffset = 0;
            moovAtom = originalMoovAtom;
            out = originalOut;
        }
    }
}
