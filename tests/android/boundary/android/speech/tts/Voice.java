package android.speech.tts;

import java.util.Locale;
import java.util.Set;

/** JVM boundary for voice metadata only; no synthesis or playback is simulated. */
public final class Voice {
    private final String name;
    private final Locale locale;
    private final boolean network;
    private final Set<String> features;

    public Voice(String name, Locale locale, int quality, int latency, boolean network, Set<String> features) {
        this.name = name;
        this.locale = locale;
        this.network = network;
        this.features = features;
    }
    public String getName() { return name; }
    public Locale getLocale() { return locale; }
    public boolean isNetworkConnectionRequired() { return network; }
    public Set<String> getFeatures() { return features; }
}
