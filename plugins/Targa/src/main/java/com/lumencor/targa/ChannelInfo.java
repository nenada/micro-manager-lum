package org.lumencor.targa;

/**
 * Channel info: parameters for each channel
 */
public class ChannelInfo {
    String name;            // Channel name
    double ttlExposureMs;   // ttl exposure time in ms
    int intensity;          // intensity value for the light source

    public ChannelInfo(String name, double ttlExposureMs, int intensity) {
        this.name = name;
        this.ttlExposureMs = ttlExposureMs;
        this.intensity = intensity;
    }

    public String getExposureProperty() {
        return name + "_" + "ExposureMs";
    }

    public String getIntensityProperty() {
        return name + "_" + "Intensity";
    }

}
