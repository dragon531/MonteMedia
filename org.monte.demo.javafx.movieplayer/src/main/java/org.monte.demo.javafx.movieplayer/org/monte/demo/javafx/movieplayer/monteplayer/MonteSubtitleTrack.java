/*
 * @(#)MonteVideoTrack.java
 * Copyright © 2024 Werner Randelshofer, Switzerland. MIT License.
 */

package org.monte.demo.javafx.movieplayer.monteplayer;

import org.monte.demo.javafx.movieplayer.model.AbstractSubtitleTrack;

import java.util.Locale;
import java.util.Map;

public class MonteSubtitleTrack extends AbstractSubtitleTrack {
    public MonteSubtitleTrack(Locale locale, long trackId, String name, Map<String, Object> metadata) {
        super(locale, trackId, name, metadata);
    }
}
