/*
 * @(#)AVIPCMAudioCodec.java
 * Copyright © 2023 Werner Randelshofer, Switzerland. MIT License.
 */
package org.monte.media.avi.codec.audio;

import org.monte.media.av.Format;
import org.monte.media.av.FormatKeys.MediaType;
import org.monte.media.av.codec.audio.AbstractPCMAudioCodec;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;

import static org.monte.media.av.FormatKeys.EncodingKey;
import static org.monte.media.av.FormatKeys.MIME_AVI;
import static org.monte.media.av.FormatKeys.MIME_JAVA;
import static org.monte.media.av.FormatKeys.MediaTypeKey;
import static org.monte.media.av.FormatKeys.MimeTypeKey;
import static org.monte.media.av.codec.audio.AudioFormatKeys.ByteOrderKey;
import static org.monte.media.av.codec.audio.AudioFormatKeys.ENCODING_AVI_PCM;
import static org.monte.media.av.codec.audio.AudioFormatKeys.ENCODING_PCM_SIGNED;
import static org.monte.media.av.codec.audio.AudioFormatKeys.ENCODING_PCM_UNSIGNED;
import static org.monte.media.av.codec.audio.AudioFormatKeys.SampleSizeInBitsKey;
import static org.monte.media.av.codec.audio.AudioFormatKeys.SignedKey;

/**
 * {@code AbstractPCMAudioCodec} performs sign conversion, endian conversion and
 * quantization conversion of PCM audio data.
 * <p>
 * Does not perform sampling rate conversion or channel conversion.
 * <p>
 * FIXME Maybe create separate subclasses for AVI PCM and QuickTime PCM.
 *
 * @author Werner Randelshofer
 */
public class AVIPCMAudioCodec extends AbstractPCMAudioCodec {

    private final static HashSet<String> supportedEncodings = new HashSet<>(
            Arrays.asList(new String[]{
                    ENCODING_PCM_SIGNED,
                    ENCODING_PCM_UNSIGNED, ENCODING_AVI_PCM,}));

    public AVIPCMAudioCodec() {
        super(new Format[]{
                new Format(MediaTypeKey, MediaType.AUDIO,//
                        EncodingKey, ENCODING_PCM_SIGNED,//
                        MimeTypeKey, MIME_JAVA,//
                        SignedKey, true),//
                new Format(MediaTypeKey, MediaType.AUDIO,//
                        EncodingKey, ENCODING_PCM_UNSIGNED,//
                        MimeTypeKey, MIME_JAVA,//
                        SignedKey, false),//
                new Format(MediaTypeKey, MediaType.AUDIO,//
                        EncodingKey, ENCODING_AVI_PCM,//
                        MimeTypeKey, MIME_AVI,//
                        SignedKey, false, SampleSizeInBitsKey, 8),//
                new Format(MediaTypeKey, MediaType.AUDIO,//
                        EncodingKey, ENCODING_AVI_PCM,//
                        MimeTypeKey, MIME_AVI,//
                        ByteOrderKey, ByteOrder.LITTLE_ENDIAN,
                        SignedKey, true, SampleSizeInBitsKey, 16),//
                new Format(MediaTypeKey, MediaType.AUDIO,//
                        EncodingKey, ENCODING_AVI_PCM,//
                        MimeTypeKey, MIME_AVI,//
                        ByteOrderKey, ByteOrder.LITTLE_ENDIAN,
                        SignedKey, true, SampleSizeInBitsKey, 24),//
                new Format(MediaTypeKey, MediaType.AUDIO,//
                        EncodingKey, ENCODING_AVI_PCM,//
                        MimeTypeKey, MIME_AVI,//
                        ByteOrderKey, ByteOrder.LITTLE_ENDIAN,
                        SignedKey, true, SampleSizeInBitsKey, 32),//
        });
        name = "AVI PCM Codec";
    }

}
