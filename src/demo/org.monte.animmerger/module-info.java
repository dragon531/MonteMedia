/* @(#)module-info.java
 * Copyright © 2007 Werner Randelshofer, Switzerland.
 * You may only use this software in accordance with the license terms.
 */

module org.monte.animmerger {
    requires java.desktop;
    
    requires org.monte.media.amiga;
    requires org.monte.media.misc;
    requires org.monte.media.player;
    
    exports org.monte.animmerger;
}
