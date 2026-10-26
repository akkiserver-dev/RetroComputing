package jp.akkiserver.rc.api.hardware.device;

/**
 * Optional capability that provides an image for the selected architecture to boot.
 *
 * @author akki697222
 */
public interface BootSource {
    /**
     * Returns the boot image supplied by this device.
     *
     * @return boot image bytes
     */
    byte[] bootImage();
}
