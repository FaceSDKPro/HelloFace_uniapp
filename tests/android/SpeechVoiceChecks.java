import android.speech.tts.Voice;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Exercises the compiled production selector against Android voice metadata boundaries. */
public final class SpeechVoiceChecks {
    private static Object selector;
    private static Method offline;

    private static Voice voice(String name, String language, String country, boolean online, boolean missing) {
        return new Voice(name, new Locale(language, country), 0, 0, online,
                missing ? Collections.singleton("notInstalled") : Collections.emptySet());
    }

    private static void select(Locale locale, Voice expected, Voice... available) throws Exception {
        Set<Voice> voices = new LinkedHashSet<>(Arrays.asList(available));
        if (offline.invoke(selector, voices, locale) != expected) {
            throw new AssertionError("Wrong offline voice for " + locale);
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("uts.sdk.modules.HelloFaceVerify.HelloFaceVerifyVoices");
        selector = type.getField("INSTANCE").get(null);
        offline = type.getMethod("offline", Set.class, Locale.class);
        Voice cn = voice("Chinese", "zh", "CN", false, false);
        Voice samsung = voice("Samsung Chinese", "zho", "CHN", false, false);
        Voice generic = voice("Mandarin", "zh", "", false, false);
        Voice genericIso3 = voice("Mandarin ISO3", "zho", "", false, false);
        Voice hk = voice("Other region", "zh", "HK", false, false);
        Voice english = voice("English", "eng", "USA", false, false);
        select(Locale.CHINA, cn, hk, generic, cn);
        select(Locale.CHINA, samsung, hk, generic, samsung);
        select(Locale.CHINA, generic, hk, generic);
        select(Locale.CHINA, genericIso3, hk, genericIso3);
        select(Locale.CHINA, generic, voice("Online", "zh", "CN", true, false),
                voice("Not installed", "zho", "CHN", false, true), generic);
        select(Locale.CHINA, null, english);
        select(Locale.CHINA, null);
        select(Locale.US, english, cn, english);
        System.out.println("PASS compiled offline voice selector: 8 locale, region and availability checks");
    }
}
